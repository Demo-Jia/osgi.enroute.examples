/**
 * OSGI ENROUTE EXAMPLES CM
 * 
 * This module provides a demo of the capabilities of the OSGi Configuration
 * Admin service.
 */

(function() {

	'use strict';

	var MODULE = angular.module('osgi.enroute.examples.cm', //
	[ 'ngRoute', 'ngResource', 'enJsonrpc', 'enEasse' ]);

	var alerts = [];
	var editor;
	var resolveBefore;

	function error(msg) {
		alerts.push({
			msg : msg,
			type : 'danger'
		});
	}

	//
	// CONFIG
	//

	MODULE.config(function($routeProvider, en$jsonrpcProvider) {

		en$jsonrpcProvider.route();
		en$jsonrpcProvider.setNotification({
			error : error
		})

		resolveBefore = {
			cmAppEndpoint : en$jsonrpcProvider
					.endpoint("osgi.enroute.examples.cm")
		};

		$routeProvider.when('/', {
			templateUrl : 'main/htm/about.htm'
		});

		$routeProvider.when('/singleton', {
			controller : singletonProvider,
			templateUrl : 'main/htm/singleton.htm',
			resolve : resolveBefore
		});

		$routeProvider.when('/factory', {
			controller : factoryProvider,
			templateUrl : 'main/htm/factory.htm',
			resolve : resolveBefore
		});

		$routeProvider.otherwise('/');

	});

	//
	// RUN
	//

	MODULE.run(function($rootScope, $location, en$easse, en$jsonrpc) {

		$rootScope.alerts = alerts;
		$rootScope.closeAlert = function(index) {
			alerts.splice(index, 1);
		};
		$rootScope.page = function() {
			return $location.url();
		}

		resolveBefore.cmAppEndpoint().then(function(cmapp) {
			editor = new Editor(cmapp);
			$rootScope.editor = editor;
			en$easse.handle("osgi/enroute/examples/*", editor.update, error);
		});

		function Editor(cmapp) {
			var THIS = this;
			
			this.configurations = {};
			this.cmapp = cmapp;
			
			this.init = function(isfact) {
				this.isfact = isfact;
				this.select(undefined);
				return this;
			}

			this.update = function(msg) {
				if (!msg.properties) {
					delete THIS.configurations[msg.pid];
					if (THIS.pid == msg.pid) {
						THIS.select(undefined);
					}
				} else {
					THIS.set(msg.pid,msg.factoryPid,msg.properties);
					THIS.select(THIS.configurations[msg.pid]);
				}

				$rootScope.$digest();
			};

			this.select = function(config) {
				if (config ) {
					if ( this.isfact == !config.factoryPid)
						return;
					
					this.pid = config.pid;
					this.factoryPid = config.factoryPid;
					this.configuration = config;
				} else {
					this.pid = null;
					this.factoryPid = null;
					this.configuration = null;
				}
			}

			this.set = function(pid, fpid, properties) {
				var config = this.configurations[pid];
				if (angular.isUndefined(config)) {
					config = (this.configurations[pid] = {
						pid : pid
					});
				}

				config.factoryPid = fpid || null;
				config.properties = properties || config.properties || {};
			}

			this.getConfigurations = function(factoryPid) {
				if (this.isfact && !factoryPid)
					return;

				var result = [];
				for ( var k in this.configurations) {
					var c = this.configurations[k];
					if (factoryPid == c.factoryPid)
						result.push(c);
				}
				return result;
			};

			this.getFactoryPids = function() {
				var factoryPids = {};
				for ( var k in this.configurations) {
					var c = this.configurations[k];
					if (c.factoryPid)
						factoryPids[c.factoryPid] = null;
				}
				return Object.keys(factoryPids);
			}

			this.addInstance = function(fpid) {
				cmapp.createInstance(fpid).then(function(pid) {
					THIS.set(pid, fpid);
					THIS.select(THIS.configurations[pid])
				});
			}

			this.addSingleton = function(pid) {
				THIS.set(pid, null);
				THIS.select(this.configurations[pid]);
			}

			this.remove = function(pid) {
				cmapp.removeConfiguration(pid);
			}

			this.addKey = function(key, value) {
				this.configuration.properties[key] = value;
				cmapp
						.saveConfiguration(this.pid,
								this.configuration.properties);
			}

			this.removeKey = function(key) {
				delete this.configuration.properties[key];
				cmapp
						.saveConfiguration(this.pid,
								this.configuration.properties);
			}

			this.isArray = function(value) {
				return angular.isArray(value);
			}

			this.example = function(name) {
				cmapp.example(name);
			}
			//
			// Ok, get all the configurations
			//

			cmapp.findConfigurations(null).then(function(configurations) {
				configurations.forEach(function(c) {
					this.set(c["service.pid"], c["service.factoryPid"], c);
				}, THIS);
			});
		}
	});

	var singletonProvider = function($scope) {
		editor.init(false);
	}

	var factoryProvider = function($scope) {
		editor.init(true);
	}

})();
