package osgi.enroute.trains.track.controller.provider;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osgi.enroute.trains.cloud.api.Color;
import osgi.enroute.trains.cloud.api.Command;
import osgi.enroute.trains.cloud.api.Command.Type;
import osgi.enroute.trains.cloud.api.Segment;
import osgi.enroute.trains.cloud.api.TrackForSegment;
import osgi.enroute.trains.controller.api.RFIDSegmentController;
import osgi.enroute.trains.controller.api.SegmentController;
import osgi.enroute.trains.controller.api.SignalSegmentController;
import osgi.enroute.trains.controller.api.SwitchSegmentController;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;


/**
 * The TrackController listenes for Command events and performs 
 * those on the right Segment Controller
 */
@Component(name = "osgi.enroute.trains.track.controller",
	properties={"event.topics="+Command.TOPIC},
	immediate=true)
public class TrackControllerImpl implements EventHandler {
	static Logger logger = LoggerFactory.getLogger(TrackControllerImpl.class);

	private TrackForSegment trackManager;

	private Map<Integer, RFIDSegmentController> rfids = Collections.synchronizedMap(new HashMap<Integer, RFIDSegmentController>());
	private Map<Integer, SignalSegmentController> signals = Collections.synchronizedMap(new HashMap<Integer, SignalSegmentController>());
	private Map<Integer, SwitchSegmentController> switches = Collections.synchronizedMap(new HashMap<Integer, SwitchSegmentController>());

	
	@Override
	public void handleEvent(Event event) {
		Type type = (Type)event.getProperty("type");
		String s = (String)event.getProperty("segment");
		
		Segment segment = trackManager.getSegments().get(s);
		if(segment==null){
			System.err.println("Segment "+s+" does not exist");
			logger.error("Segment "+s+" does not exist");
			return;
		}
		
		switch(type){
		case SIGNAL: {
			Color color = (Color)event.getProperty("signal");
			
			SignalSegmentController controller = signals.get(segment.controller);
			if(controller==null){
				System.err.println("Controller "+segment.controller+" not found");
				logger.error("Controller "+segment.controller+" not found");
				return;
			}
			
			controller.signal(color);
			
			trackManager.signal(s, color);
			System.out.println("CHANGE "+s+" TO "+color);
			return;
		}
		case SWITCH: {
			SwitchSegmentController controller = switches.get(segment.controller);
			if(controller==null){
				System.err.println("Controller "+segment.controller+" not found");
				logger.error("Controller "+segment.controller+" not found");
				return;
			}
			
			controller.swtch(!controller.getSwitch());
			
			trackManager.switched(s, controller.getSwitch());
			System.out.println("SWITCH "+s);
			return;
		}
		}
	}
	
	@Reference
	public void setTrackManager(TrackForSegment tm){
		this.trackManager = tm;
	}
	
	@Reference(type='*')
	public void addRFIDController(RFIDSegmentController c, Map<String, Object> properties){
		int id = Integer.parseInt((String)properties.get(SegmentController.CONTROLLER_ID));
		// find the segment for this controller
		Segment rfidSegment = null;
		for(Segment s : trackManager.getSegments().values()){
			if(s.controller==id){
				rfidSegment = s;
			}
		}
		
		// track new rfid detections on this controller
		if(rfidSegment!=null){
			String segment = rfidSegment.name;
			trackRFID(id, segment);
		}
		
		rfids.put(id, c);
	}
	
	public void removeRFIDController(RFIDSegmentController c, Map<String, Object> properties){
		int id = Integer.parseInt((String)properties.get(SegmentController.CONTROLLER_ID));
		rfids.remove(id);
	}
	
	private void trackRFID(final int controller, final String segment){
		// check whether this controller is still valid
		RFIDSegmentController c = rfids.get(controller);
		if(c!=null){
			c.nextRFID().then( (p) -> {
				// notify track manager when a train passes by
				String train = p.getValue();
				trackManager.locatedTrainAt(train, segment);
				return null;}
			).then((p) -> {
				// and then start tracking the next one
				trackRFID(controller, segment); 
				return null;});
		}
	}
	
	@Reference(type='*')
	public void addSignalController(SignalSegmentController c, Map<String, Object> properties){
		int id = Integer.parseInt((String)properties.get(SegmentController.CONTROLLER_ID));
		signals.put(id, c);
	}
	
	public void removeSignalController(SignalSegmentController c, Map<String, Object> properties){
		int id = Integer.parseInt((String)properties.get(SegmentController.CONTROLLER_ID));
		signals.remove(id);
	}
	
	@Reference(type='*')
	public void addSwitchController(SwitchSegmentController c, Map<String, Object> properties){
		int id = Integer.parseInt((String)properties.get(SegmentController.CONTROLLER_ID));
		switches.put(id, c);
	}
	
	public void removeSwitchController(SwitchSegmentController c, Map<String, Object> properties){
		int id = Integer.parseInt((String)properties.get(SegmentController.CONTROLLER_ID));
		switches.remove(id);
	}
}
