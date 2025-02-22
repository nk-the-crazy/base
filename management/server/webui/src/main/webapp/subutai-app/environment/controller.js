'use strict';

angular.module('subutai.environment.controller', [])
	.controller('EnvironmentViewCtrl', EnvironmentViewCtrl)
	.directive('fileModel', fileModel)
	.filter( 'sshEmail', function () {
		return function( input, modify )
		{
			if( !modify )
				return input;

			var newVal = input.split(' ');

			return newVal[newVal.length - 1];
		}
	});

EnvironmentViewCtrl.$inject = ['$scope', '$rootScope', 'environmentService', 'trackerSrv', 'identitySrv', 'SweetAlert', '$resource', '$compile', 'ngDialog', '$timeout', '$sce', '$stateParams', 'DTOptionsBuilder', 'DTColumnDefBuilder'];
fileModel.$inject = ['$parse'];

var fileUploader = {};

function EnvironmentViewCtrl($scope, $rootScope, environmentService, trackerSrv, identitySrv, SweetAlert, $resource, $compile, ngDialog, $timeout, $sce, $stateParams, DTOptionsBuilder, DTColumnDefBuilder) {

	var vm = this;

	vm.activeMode = 'simple';

	vm.currentEnvironment = {};

	vm.sshKeysList = [];
	vm.environments = [];
	vm.domainStrategies = [];
	vm.strategies = [];
	vm.sshKeyForEnvironment = '';
	vm.environmentForDomain = '';
	vm.currentDomain = {};
	vm.installed = false;
	vm.pending = false;
	vm.isEditing = false;

	vm.advancedEnv = {};
	vm.advancedEnv.currentNode = getDefaultValues();
	vm.advancedModeEnabled = false;
	vm.nodeStatus = 'Add to';
	vm.nodeList = [];
	vm.colors = quotaColors;
	vm.containersType = [];
	vm.containersTypeInfo = [];
	vm.listOfUsers = [];
	vm.users2Add = [];
	vm.installedContainers = [];
	vm.currentUser = {};
	vm.restInProgress = false;

	// functions
	vm.changeMode = changeMode;

	vm.destroyEnvironment = destroyEnvironment;
	vm.sshKey = sshKey;
	vm.addSshKey = addSshKey;
	vm.removeSshKey = removeSshKey;
	vm.showContainersList = showContainersList;
	vm.setSSHKey = setSSHKey;
	vm.showSSHKeyForm = showSSHKeyForm;
	vm.showSSHKeysPopup = showSSHKeysPopup;
	vm.deleteSSHKey = deleteSSHKey;
	vm.sshKeyFormat = sshKeyFormat;
	vm.showDomainForm = showDomainForm;
	vm.setDomain = setDomain;
	vm.removeDomain = removeDomain;
	vm.minimizeLogs = minimizeLogs;
	vm.getQuotaColor = getQuotaColor;
	vm.initDataTable = initDataTable;

	//share environment functions
	vm.shareEnvironmentWindow = shareEnvironmentWindow;
	vm.shareEnvironment = shareEnvironment;
	vm.addUser2Stack = addUser2Stack;
	vm.removeUserFromStack = removeUserFromStack;

	function changeMode(modeStatus) {
		if(modeStatus) {
			vm.activeMode = 'advanced';
		} else {
			vm.activeMode = 'simple';
		}
	}

	identitySrv.getCurrentUser().success (function (data) {
		vm.currentUser = data;
	});

	environmentService.getContainersType()
		.success(function (data) {
			vm.containersType = data;
		})
		.error(function (data) {
			VARS_MODAL_ERROR( SweetAlert, data );
		});

	environmentService.getContainersTypesInfo()
		.success(function (data) {
			vm.containersTypeInfo = [];

			for( var i = 0; i < data.length; i++ )
			{
				var type = data[i].key.split(/\.(.+)?/)[0];
				var property = data[i].key.split(/\.(.+)?/)[1];

				if( vm.containersTypeInfo[type] === undefined )
				{
					vm.containersTypeInfo[type] = {};
				}

				vm.containersTypeInfo[type][property] = data[i].value;
			}
		});


	vm.containersTotal = [];
	function loadEnvironments() {

		if( !vm.restInProgress )
		{
			vm.restInProgress = true;
			vm.containersTotal = [];
			environmentService.getEnvironments().success (function (data) {
				var environmentsList = [];
				for (var i = 0; i < data.length; ++i) {
					data[i].containersByQuota = getContainersSortedByQuota(data[i].containers);
					environmentsList.push(data[i]);
				}
				vm.environments = environmentsList;
				vm.restInProgress = false;
			}).error(function (error){
				console.log(error);
				vm.restInProgress = false;
			});
		}
	}
	loadEnvironments();

	$scope.$on('reloadEnvironmentsList', function(event) {
		loadEnvironments();
	});

	environmentService.getStrategies().success(function (data) {
		vm.strategies = data;
	});

	environmentService.getDomainStrategies().success(function (data) {
		vm.domainStrategies = data;
	});

	//installed environment table options
	function initDataTable() {
		vm.dtInstance = {};
		vm.dtOptionsInstallTable = DTOptionsBuilder
			.newOptions()
			.withOption('order', [[ 1, "asc" ]])
			.withOption('stateSave', true)
			//.withOption('paging', false)
			.withOption('searching', false)
			//.withOption('retrieve', true)
			.withPaginationType('full_numbers');
		vm.dtColumnDefsInstallTable = [
			DTColumnDefBuilder.newColumnDef(0).notSortable(),
			DTColumnDefBuilder.newColumnDef(1),
			DTColumnDefBuilder.newColumnDef(2).notSortable(),
			DTColumnDefBuilder.newColumnDef(3).notSortable(),
			DTColumnDefBuilder.newColumnDef(4).notSortable(),
			DTColumnDefBuilder.newColumnDef(5).notSortable()
		];
	}

	var refreshTable;
	var reloadTableData = function() {
		refreshTable = $timeout(function myFunction() {
			loadEnvironments();
			refreshTable = $timeout(reloadTableData, 30000);
		}, 30000);
	};
	reloadTableData();

	$rootScope.$on('$stateChangeStart',	function(event, toState, toParams, fromState, fromParams){
		$timeout.cancel(refreshTable);
	});

	function addUser2Stack(user) {
		vm.users2Add.push(angular.copy(user));
		for (var i = 0; i < vm.listOfUsers.length; ++i) {
			if (vm.listOfUsers[i].fullName === user.fullName) {
				vm.listOfUsers.splice (i, 1);
				break;
			}
		}
	}


	function removeUserFromStack(key) {
		vm.listOfUsers.push (vm.users2Add[key]);
		vm.users2Add.splice(key, 1);
	}

	function shareEnvironmentWindow(environment) {
		vm.listOfUsers = [];
		identitySrv.getUsers().success(function (data) {
			for (var i = 0; i < data.length; ++i) {
				if (data[i].id !== vm.currentUser.id) {
					vm.listOfUsers.push (data[i]);
				}
			}
			for (var i = 0; i < vm.listOfUsers.length; ++i) {
				vm.listOfUsers[i].read = true;
				vm.listOfUsers[i].write = true;
				vm.listOfUsers[i].update = true;
				vm.listOfUsers[i].delete = true;
			}
			environmentService.getShared(environment.id).success(function (data2) {
				vm.users2Add = data2;
				for (var i = 0; i < vm.users2Add.length; ++i) {
					if (vm.users2Add[i].id === vm.currentUser.id) {
						vm.users2Add.splice (i, 1);
						--i;
						continue;
					}
					for (var j = 0; j < vm.listOfUsers.length; ++j) {
						if (vm.listOfUsers[j].id === vm.users2Add[i].id) {
							vm.users2Add[i].fullName = vm.listOfUsers[j].fullName;
							vm.listOfUsers.splice (j, 1);
							break;
						}
					}
				}
				vm.currentEnvironment = environment;
				ngDialog.open ({
					template: "subutai-app/environment/partials/popups/shareEnv.html",
					scope: $scope
				});
			});
		});
	}

	function shareEnvironment() {
		var arr = [];
		for (var i = 0; i < vm.users2Add.length; ++i) {
			arr.push ({
				id: vm.users2Add[i].id,
				read: vm.users2Add[i].read,
				write: vm.users2Add[i].write,
				update: vm.users2Add[i].update,
				delete: vm.users2Add[i].delete
			});
		}
		environmentService.share (JSON.stringify (arr), vm.currentEnvironment.id).success(function (data) {
			SweetAlert.swal("Success!", "Your environment was successfully shared.", "success");
			loadEnvironments();
			ngDialog.closeAll();
		}).error(function (data) {
			SweetAlert.swal("ERROR!", "Your container is safe :). Error: " + data.ERROR, "error");
		});
	}

	function actionSwitch (data, type, full, meta) {
		if (typeof (data.revoke) === "boolean") {
			return '<div class = "toggle"><input type = "checkbox" class="check" ng-click="environmentViewCtrl.revoke(\'' + data.id + '\')" ng-checked=\'' + data.revoke + '\'><div class = "toggle-bg"></div><b class = "b switch"></b></div>'
		}
		else {
			return "";
		}
	}

	vm.revoke = revoke;

	function revoke (environmentId) {
		environmentService.revoke (environmentId).success (function (data) {
			loadEnvironments();
		});
	}

	function getContainersSortedByQuota(containers) {

		var sortedContainers = containers.length > 0 ? {} : null;

		for (var index = 0; index < containers.length; index++) {

			var container = containers[index];
			var remoteProxyContainer = !container.local && container.dataSource == "hub";

			// We don't show on UI containers created by Hub, located on other peers.
			// See details: io.subutai.core.environment.impl.adapter.EnvironmentAdapter.
			if ( remoteProxyContainer )
			{
				continue;
			}

			var quotaSize = containers[index].type;
			var templateName = containers[index].templateName;

			if (!sortedContainers[quotaSize]) {
				sortedContainers[quotaSize] = {};
				sortedContainers[quotaSize].quantity = 1;
				sortedContainers[quotaSize].containers = {};
				sortedContainers[quotaSize].containers[templateName] = 1;
			} else {
				if (!sortedContainers[quotaSize].containers[templateName]) {
					sortedContainers[quotaSize].quantity += 1;
					sortedContainers[quotaSize].containers[templateName] = 1;
				} else {
					sortedContainers[quotaSize].quantity += 1;
					sortedContainers[quotaSize].containers[templateName] += 1;
				}
			}
		}

		for(var item in sortedContainers) {
			sortedContainers[item].tooltip = "";
			for(var container in sortedContainers[item].containers) {
				sortedContainers[item].tooltip += container + ":&nbsp;" + sortedContainers[item].containers[container] + "<br/>";
			}
			sortedContainers[item].tooltip += "Quota: " + item;
		}
		return sortedContainers;
	}

	function destroyEnvironment(environmentId, key) {
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
				title: "Are you sure?",
				text: "You will not be able to recover this Environment!",
				type: "warning",
				showCancelButton: true,
				confirmButtonColor: "#ff3f3c",
				confirmButtonText: "Delete",
				cancelButtonText: "Cancel",
				closeOnConfirm: false,
				closeOnCancel: true,
				showLoaderOnConfirm: true
			},
			function (isConfirm) {
				window.onkeydown = previousWindowKeyDown;
				if (isConfirm) {
					SweetAlert.swal(
						{
							title : 'Delete!',
							text : 'Your environment is being deleted!!',
							timer: VARS_TOOLTIP_TIMEOUT,
							showConfirmButton: false
						}
					);

					var destroyEnvEvent = new CustomEvent('destroyEnvironment', {'detail': environmentId});
					document.getElementById('js-environment-creation').dispatchEvent(destroyEnvEvent);

					vm.environments[key].status = 'UNDER_MODIFICATION';

					environmentService.destroyEnvironment(environmentId).success(function (data) {
						//SweetAlert.swal("Destroyed!", "Your environment has been destroyed.", "success");
						loadEnvironments();
						$rootScope.notificationsUpdate = 'destroyEnvironment';
					}).error(function (data) {
						$timeout(function() {
							SweetAlert.swal("ERROR!", "Your environment is safe :). Error: " + data.ERROR, "error");
							$rootScope.notificationsUpdate = 'destroyEnvironmentError';
						}, 2000);
					});
					loadEnvironments();
				}
			});
	}

	function showContainersList(key) {
		vm.containers = vm.environments[key].containers;
	}

	function sshKey(key) {
		delete vm.enviromentSSHKey.key;
		vm.enviromentSSHKey.environmentKey = key;
	}

	function addSshKey(key){
		var enviroment = vm.environments[vm.enviromentSSHKey.environmentKey];
		environmentService.setSshKey(vm.enviromentSSHKey.key, enviroment.id).success(function (data) {
			SweetAlert.swal("Success!", "You have successfully added SSH key for " + enviroment.id + " environment!", "success");
		}).error(function (error) {
			SweetAlert.swal("Cancelled", "Error: " + error.ERROR, "error");
		});
	}

	function removeSshKey(environmentId){
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
			title: "Are you sure?",
			text: "Delete environment SSH keys!",
			type: "warning",
			showCancelButton: true,
			confirmButtonColor: "#ff3f3c",
			confirmButtonText: "Delete",
			cancelButtonText: "Cancel",
			closeOnConfirm: false,
			closeOnCancel: true,
			showLoaderOnConfirm: true
		},
		function (isConfirm) {
			window.onkeydown = previousWindowKeyDown;
			if (isConfirm) {
				environmentService.removeSshKey(environmentId).success(function () {
					SweetAlert.swal("Destroyed!", "Your SSH keys has been deleted.", "success");
				}).error(function (data) {
					SweetAlert.swal("ERROR!", "Your SSH keys is safe :). Error: " + data.ERROR, "error");
				});
			}
		});
	}

	function getContainers() {
		var environment = vm.environments[vm.environmentQuota];
		vm.containersForQuota = environment.containers;
	}

	function showSSHKeyForm(environmentId) {
		vm.sshKeyForEnvironment = environmentId;
		ngDialog.open({
			template: 'subutai-app/environment/partials/popups/sshKeyForm.html',
			scope: $scope
		});
	}

	function showSSHKeysPopup(environmentId) {

		vm.sshKeysList = [];
		vm.sshKeyForEnvironment = environmentId;
		environmentService.getSshKey(environmentId).success( function (data) {
			vm.sshKeysList = data;
			ngDialog.open({
				template: 'subutai-app/environment/partials/popups/sshKeysPopup.html',
				scope: $scope
			});
		}).error( function(data) {
			SweetAlert.swal("Error", "Error: " + data.ERROR, "error");
			ngDialog.closeAll();
			LOADING_SCREEN('none');
		});

	}

	function deleteSSHKey(sshKey, index) {
		LOADING_SCREEN();
		environmentService.removeSshKey(vm.sshKeyForEnvironment, sshKey).success( function (data) {

			vm.sshKeysList.splice(index, 1);
			LOADING_SCREEN('none');
		}).error( function(error) {
			SweetAlert.swal("Error", "Error: " + error, "error");
			ngDialog.closeAll();
			LOADING_SCREEN('none');
		});
	}

	function sshKeyFormat(sshKey) {
		var splitedSSH = sshKey.split('==');
		return splitedSSH[0];
	}

	function showDomainForm(environmentId) {
		vm.environmentForDomain = environmentId;
		vm.currentDomain = {};
		LOADING_SCREEN();
		environmentService.getDomain(environmentId).success(function (data) {
			vm.currentDomain = data;
			ngDialog.open({
				template: 'subutai-app/environment/partials/popups/domainForm.html',
				scope: $scope
			});
			LOADING_SCREEN('none');
		});
	}

	function setDomain(domain) {
		var file = fileUploader;
		LOADING_SCREEN();
		environmentService.setDomain(domain, vm.environmentForDomain, file).success(function (data) {
			SweetAlert.swal("Success!", "You have successfully added domain for " + vm.environmentForDomain + " environment!", "success");
			ngDialog.closeAll();
			LOADING_SCREEN('none');
		}).error(function (data) {
			SweetAlert.swal("Cancelled", "Error: " + data.ERROR, "error");
			ngDialog.closeAll();
			LOADING_SCREEN('none');
		});
	}

	function removeDomain(environmentId) {
		ngDialog.closeAll();
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
			title: "Are you sure?",
			text: "Delete environment domain!",
			type: "warning",
			showCancelButton: true,
			confirmButtonColor: "#ff3f3c",
			confirmButtonText: "Delete",
			cancelButtonText: "Cancel",
			closeOnConfirm: false,
			closeOnCancel: true,
			showLoaderOnConfirm: true
		},
		function (isConfirm) {
			if (isConfirm) {
				window.onkeydown = previousWindowKeyDown;
				environmentService.removeDomain(environmentId).success(function (data) {
					SweetAlert.swal("Deleted!", "Your domain has been deleted.", "success");
				}).error(function (data) {
					SweetAlert.swal("ERROR!", "Your domain is safe. Error: " + data.ERROR, "error");
				});
			}
		});
	}

	function setSSHKey(sshKey) {
		if(sshKey === undefined || sshKey.length <= 0 || sshKey === null) return;
		LOADING_SCREEN();
		environmentService.setSshKey(sshKey, vm.sshKeyForEnvironment).success(function (data) {
			SweetAlert.swal("Success!", "You have successfully added SSH key for " + vm.sshKeyForEnvironment + " environment!", "success");
			ngDialog.closeAll();
			LOADING_SCREEN('none');
		}).error(function (data) {
			SweetAlert.swal("Cancelled", "Error: " + data.ERROR, "error");
			ngDialog.closeAll();
			LOADING_SCREEN('none');
		});
	}


	vm.setHtml = setHtml;
	function setHtml (html) {
		return $sce.trustAsHtml(html.toString());
	};


	function addNewNode() {
		if(vm.nodeStatus == 'Add to') {
			var tempNode = vm.advancedEnv.currentNode;

			if(tempNode === undefined) return;
			if(tempNode.name === undefined || tempNode.name.length < 1) return;
			if(tempNode.numberOfContainers === undefined || tempNode.numberOfContainers < 1) return;
			if(tempNode.sshGroupId === undefined) return;
			if(tempNode.hostsGroupId === undefined) return;

			if( jQuery.grep( vm.nodeList, function( i ) {
					return tempNode.name == i.name;
				}).length != 0
			) return;

			vm.nodeList.push(tempNode);
		} else {
			vm.nodeStatus = 'Add to';
		}


		vm.advancedEnv.currentNode = angular.copy( vm.advancedEnv.currentNode );
		vm.advancedEnv.currentNode.name = "";
	}

	function setNodeData(key) {
		vm.nodeStatus = 'Update in';
		vm.advancedEnv.currentNode = vm.nodeList[key];
	}

	function removeNodeGroup(key)
	{
		vm.nodeList.splice(key, 1);
	}

	function getDefaultValues() {
		var defaultVal = {
			'templateName': 'master',
			'numberOfContainers': 2,
			'sshGroupId': 0,
			'hostsGroupId': 0,
			'type': 'TINY'
		};
		return defaultVal;
	}

	function getQuotaColor(quotaSize) {
		return quotaColors[quotaSize];
	}

	function minimizeLogs() {
		var that = $('.ngdialog');
		var dialogOverlay = that.find('.ngdialog-overlay');
		if(vm.popupLogState == 'full') {
			vm.popupLogState = 'min';
			that.addClass('ngdialog_minimize');
			dialogOverlay.css({
				'top': ($(window).height() - 47) + 'px'
			});
		} else {
			vm.popupLogState = 'full';
			that.removeClass('ngdialog_minimize');
			dialogOverlay.css({
				'top': 0
			});
		}
	}
}

function imageExists(image_url){
    var http = new XMLHttpRequest();

    http.open('HEAD', image_url, false);
    http.send();

    return http.status != 404;
}

function initScrollbar() {
	$('.js-scrollbar').perfectScrollbar({
		"wheelPropagation": true,
		"swipePropagation": false
	});
}

function getDateFromString(string) {
	var dateString = string.split(' ');
	var temp = dateString[0].split('.');
	var result = [temp[2], temp[1], temp[0]].join('-') + " " + dateString[1];

	var a = result.split(/[^0-9]/);

	var localDate = moment(Date.parse(new Date(a[0],a[1]-1,a[2],a[3],a[4],a[5] ))).local();

	return localDate.format('HH:mm:ss');
}

function fileModel($parse) {
	return {
		restrict: 'A',
		link: function(scope, element, attrs) {
			var model = $parse(attrs.fileModel);
			var modelSetter = model.assign;

			element.bind('change', function(){
				document.getElementById("js-uploadFile").value = element[0].files[0].name;
				scope.$apply(function(){
					modelSetter(scope, element[0].files[0]);
					fileUploader = element[0].files[0];
				});
			});
		}
	};
}

