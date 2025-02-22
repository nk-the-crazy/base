'use strict';

angular.module('subutai.kurjun.controller', [])
	.controller('KurjunCtrl', KurjunCtrl)
	.directive('fileModel', fileModel);

KurjunCtrl.$inject = ['$scope', '$rootScope', 'kurjunSrv', 'SettingsKurjunSrv', 'identitySrv', 'SweetAlert', 'DTOptionsBuilder', 'DTColumnDefBuilder', '$resource', '$compile', 'ngDialog', '$timeout', 'cfpLoadingBar'];
fileModel.$inject = ['$parse'];

var fileUploader = {};

function KurjunCtrl($scope, $rootScope, kurjunSrv, SettingsKurjunSrv, identitySrv, SweetAlert, DTOptionsBuilder, DTColumnDefBuilder, $resource, $compile, ngDialog, $timeout, cfpLoadingBar) {

	var vm = this;
	vm.activeTab = 'templates';
	vm.repositories = [];
	vm.templates = [];
	vm.apts = [];
	vm.files = [];
	vm.isUploadAllowed = false;
	vm.listOfUsers = [];
	vm.users2Add = [];

	vm.uploadFileWindow = uploadFileWindow;
	vm.uploadFile = uploadFile;
	vm.openTab = openTab;
	vm.addTemplate = addTemplate;
	vm.proceedTemplate = proceedTemplate;
	vm.deleteTemplate = deleteTemplate;
	vm.deleteAPT = deleteAPT;
	vm.openShareWindow = openShareWindow;
	vm.shareTemplate = shareTemplate;
	vm.checkRepositoryStatus = checkRepositoryStatus;
	vm.setDefaultRepository = setDefaultRepository;
	vm.formatSize = formatSize;

	vm.addUser2Stack = addUser2Stack;
	vm.removeUserFromStack = removeUserFromStack;

	//get Lists functions
	vm.getTemplates = getTemplates;
	vm.getAPTs = getAPTs;
	vm.getRawFiles = getRawFiles;

	identitySrv.getCurrentUser().success (function (data) {
		vm.currentUser = data;
	});


	/*** Get templates according to repositories ***/
	function sturtup() {
		LOADING_SCREEN();
		SettingsKurjunSrv.getConfig().success (function (data) {
			GLOBAL_KURJUN_URL = data.globalKurjunUrls[0];
			getTemplates();
			getAPTs();
			getRawFiles();
		}).error(function (error){
			SweetAlert.swal("ERROR!", error, "error");
			LOADING_SCREEN('none');
		});
	}
	sturtup();

	function getTemplates() {
		LOADING_SCREEN();
		/*kurjunSrv.getRepositories().success(function (repositories) {
			vm.repositories = repositories;

			kurjunSrv.getTemplates().success(function (data) {
				vm.templates = data;
				LOADING_SCREEN('none');
			}).error(function (error){
				LOADING_SCREEN('none');
			});
		});*/

		kurjunSrv.getTemplates().success(function (data) {
			vm.templates = data;
			LOADING_SCREEN('none');
		}).error(function (error){
			LOADING_SCREEN('none');
		});
	}

	/*** Get all APTs ***/
	function getAPTs() {
		LOADING_SCREEN();
		kurjunSrv.getAPTList().success(function (aptList) {
			vm.aptList = aptList;
			LOADING_SCREEN('none');
		}).error(function (error){
			LOADING_SCREEN('none');
		});
	}


	function getRawFiles() {
		LOADING_SCREEN();
		kurjunSrv.getRawFiles().success (function (data) {
			vm.files = data;
			LOADING_SCREEN('none');
		}).error(function (error){
			LOADING_SCREEN('none');
		});
	}

	function openTab(tab) {
		vm.dtOptions = DTOptionsBuilder
			.newOptions()
			.withOption('order', [[0, "desc"]])
			.withOption('stateSave', true)
			.withPaginationType('full_numbers');

		switch (tab) {
			case 'templates':
				vm.dtColumnDefs = [
					DTColumnDefBuilder.newColumnDef(0),
					DTColumnDefBuilder.newColumnDef(1),
					DTColumnDefBuilder.newColumnDef(2),
					DTColumnDefBuilder.newColumnDef(3),
					DTColumnDefBuilder.newColumnDef(4),
					DTColumnDefBuilder.newColumnDef(5),
					DTColumnDefBuilder.newColumnDef(6).notSortable()
				];
				break;
			case 'apt':
				vm.dtColumnDefs = [
					DTColumnDefBuilder.newColumnDef(0),
					DTColumnDefBuilder.newColumnDef(1),
					DTColumnDefBuilder.newColumnDef(2),
					DTColumnDefBuilder.newColumnDef(3),
					DTColumnDefBuilder.newColumnDef(4).notSortable()
				];
				break;
			case 'raw':
				vm.dtColumnDefs = [
					DTColumnDefBuilder.newColumnDef(0),
					DTColumnDefBuilder.newColumnDef(1),
					DTColumnDefBuilder.newColumnDef(2).notSortable(),
				];
				default:
				break;
		}
		vm.activeTab = tab;
	}

	function addTemplate(repository) {
		switch (vm.activeTab) {
			case 'templates':
				vm.currentTemplate = {file: null};
				ngDialog.open({
					template: 'subutai-app/kurjun/partials/template-form.html',
					scope: $scope
				});
				break;
			case 'apt':
				vm.currentTemplate = {repository: null, file: null};
				ngDialog.open({
					template: 'subutai-app/kurjun/partials/apt-form.html',
					scope: $scope
				});
				break;
			default:
				break;
		}
	}

	function proceedTemplate(template) {
		switch (vm.activeTab) {
			case 'templates':
				kurjunSrv.addTemplate(template.repository, template.file).then(function (response) {
					$timeout(function () {
						template.file.result = response.data;
						LOADING_SCREEN('none');
						SweetAlert.swal("Success!", "You have successfully uploaded template", "success");
						getTemplates();
					}, 2000);
				}, function (response) {
					if (response.status > 0) {
						$timeout(function () {
							ngDialog.closeAll();
							LOADING_SCREEN('none');
							SweetAlert.swal("ERROR!", response.data, "error");
						}, 2000);
					}
				}, function (event) {
					template.file.progress = Math.min(100, parseInt(100.0 * event.loaded / event.total));
					if (template.file.progress == 100) {
						$timeout(function () {
							ngDialog.closeAll();
							LOADING_SCREEN();
						}, 1000);
					}
				});
				break;
			case 'apt':
				kurjunSrv.addApt(template.file).then(function (response) {
					template.file.result = response.data;
					$timeout(function () {
						LOADING_SCREEN('none');
						SweetAlert.swal("Success!", "You have successfully uploaded APT", "success");
						getAPTs();
					}, 2000);
				}, function (response) {
					if (response.status > 0) {
						$timeout(function () {
							ngDialog.closeAll();
							LOADING_SCREEN('none');
							SweetAlert.swal("ERROR!", response.data, "error");
						}, 2000);
					}
				}, function (event) {
					template.file.progress = Math.min(100, parseInt(100.0 * event.loaded / event.total));
					if (template.file.progress == 100) {
						$timeout(function () {
							ngDialog.closeAll();
							LOADING_SCREEN();
						}, 1000);
					}
				});
				break;
			default:
				break;
		}
	}

	function deleteTemplate(template) {
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
				title: "Are you sure?",
				text: "Delete template!",
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
					LOADING_SCREEN();
					kurjunSrv.deleteTemplate(template.id).success(function (data) {
						LOADING_SCREEN('none');
						SweetAlert.swal("Deleted!", "Template has been deleted.", "success");
						getTemplates();
					}).error(function (data) {
						LOADING_SCREEN('none');
						SweetAlert.swal("ERROR!", data, "error");
					});
				}
			});
	}

	function deleteAPT(apt) {
		var previousWindowKeyDown = window.onkeydown;
		SweetAlert.swal({
				title: "Are you sure?",
				text: "Delete template!",
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
					LOADING_SCREEN();
					kurjunSrv.deleteAPT(apt.md5Sum).success(function (data) {
						LOADING_SCREEN('none');
						SweetAlert.swal("Deleted!", "APT package has been deleted.", "success");
						getAPTs();
					}).error(function (data) {
						LOADING_SCREEN('none');
						SweetAlert.swal("ERROR!", data, "error");
					});
				}
			});
	}



	function openShareWindow(template) {
		vm.listOfUsers = [];
		vm.checkedUsers = [];
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
			kurjunSrv.getShared(template.id).success(function (data2) {
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
				vm.currentTemplate = angular.copy(template);
				ngDialog.open ({
					template: "subutai-app/environment/partials/popups/share-template.html",
					scope: $scope
				});
			});
		});
	}

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

	function shareTemplate() {
		var users = [];
		for (var i = 0; i < vm.users2Add.length; ++i) {
			users.push ({
				id: vm.users2Add[i].id,
				read: vm.users2Add[i].read,
				write: vm.users2Add[i].write,
				update: vm.users2Add[i].update,
				delete: vm.users2Add[i].delete
			});
		}
		kurjunSrv.shareTemplate(JSON.stringify (users), vm.currentTemplate.id).success(function (data) {
			SweetAlert.swal("Success!", "Your template was successfully shared.", "success");
			ngDialog.closeAll();
		}).error(function (data) {
			SweetAlert.swal("ERROR!", data.ERROR, "error");
		});
	}


	function checkRepositoryStatus(repository) {
		console.log(repository);
		kurjunSrv.isUploadAllowed(repository).success(function (data) {
			vm.isUploadAllowed = (data === 'false' ? false : true);
		});
	}

	function setDefaultRepository() {
		vm.currentTemplate.repository = vm.repositories[0];
		vm.checkRepositoryStatus(vm.currentTemplate.repository)
	}


	function uploadFileWindow() {
		ngDialog.open ({
			template: "subutai-app/kurjun/partials/uploadFile.html",
			scope: $scope
		});
	}

	function uploadFile() {
		kurjunSrv.uploadFile (fileUploader).success (function (data) {
			SweetAlert.swal("Success!", "You have successfully uploaded file", "success");
			getRawFiles();
			ngDialog.closeAll();
		}).error (function (error) {
			SweetAlert.swal("ERROR!", error, "error");
		});
	}

	cfpLoadingBar.start();
	angular.element(document).ready(function () {
		$timeout(function () {
			cfpLoadingBar.complete();
		}, 500);
	});

	function formatSize(size) {
		var value = ((size / 1024) / 1024).toFixed(2);
		if(value < 1) {
			value = (size / 1024).toFixed(2).toString() + ' Kb';
		} else {
			value = value.toString() + ' Mb';
		}
		return value;
	}

}

function fileModel($parse) {
	return {
		restrict: 'A',
		link: function (scope, element, attrs) {
			var model = $parse(attrs.fileModel);
			var modelSetter = model.assign;

			element.bind('change', function () {
				document.getElementById("filename").value = element[0].files[0].name;
				scope.$apply(function () {
					modelSetter(scope, element[0].files[0]);
					fileUploader = element[0].files[0];
				});
			});
		}
	};
}

