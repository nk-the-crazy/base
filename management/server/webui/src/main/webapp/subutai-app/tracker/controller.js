'use strict';

angular.module('subutai.tracker.controller', [])
	.controller('TrackerCtrl', TrackerCtrl);


TrackerCtrl.$inject = ['trackerSrv', '$scope', '$rootScope', 'DTOptionsBuilder', 'DTColumnBuilder', '$resource', '$compile', 'ngDialog', '$timeout', 'cfpLoadingBar'];


function TrackerCtrl(trackerSrv, $scope, $rootScope, DTOptionsBuilder, DTColumnBuilder, $resource, $compile, ngDialog, $timeout, cfpLoadingBar) {

	var vm = this;
	vm.logs = [];
	vm.currentLog = [];

	cfpLoadingBar.start();
	angular.element(document).ready(function () {
		cfpLoadingBar.complete();
	});

	vm.loadOperations = loadOperations;
	vm.viewLogs = viewLogs;

	vm.selectedModule = 'ENVIRONMENT MANAGER';
	vm.startDate = new Date(new Date().getFullYear(), 0, 1);;
	vm.endDate = new Date(new Date().getFullYear(), 11, 1);;

	trackerSrv.getModules().success(function (data) {
		vm.modules = data;
	});

	vm.dtInstance = {};
	vm.dtOptions = DTOptionsBuilder
		.fromFnPromise(function() {
			var logsDates = getDateInStringFormat();
			var url  = trackerSrv.getBaseUrl() + 'operations/' + vm.selectedModule + '/' + logsDates.startDateString + '/' + logsDates.endDateString + '/' + 100;
			return $resource( url ).query().$promise;
		})
		.withPaginationType('full_numbers')
		.withOption('createdRow', createdRow)
		.withOption('order', [[ 0, "desc" ]])
		.withOption('columnDefs', [ {className: "b-main-table__status-col", "targets": [2, 3]} ])
		.withOption('stateSave', true);

	vm.dtColumns = [
		DTColumnBuilder.newColumn('createDate').withTitle('Date'),
		DTColumnBuilder.newColumn('description').withTitle('Operation'),
		DTColumnBuilder.newColumn(null).withTitle('Status').renderWith(statusHTML),
		DTColumnBuilder.newColumn(null).withTitle('Logs').notSortable().renderWith(viewLogsButton),
	];

	var refreshTable;
	var reloadTableData = function() {
		refreshTable = $timeout(function myFunction() {
			if(typeof(vm.dtInstance.reloadData) == 'function') {
				vm.dtInstance.reloadData(null, false);
			}
			refreshTable = $timeout(reloadTableData, 30000);
		}, 30000);
	};
	reloadTableData();

	$rootScope.$on('$stateChangeStart',	function(event, toState, toParams, fromState, fromParams){
		console.log('cancel');
		$timeout.cancel(refreshTable);
	});

	function createdRow(row, data, dataIndex) {
		$compile(angular.element(row).contents())($scope);
	}

	function statusHTML(data, type, full, meta) {
		vm.logs[data.id] = data;
		return '<div class="b-status-icon b-status-icon_' + data.state + '" tooltips tooltip-template="' + data.state + '" tooltip-smart="true"></div>';
	}

	function viewLogsButton(data, type, full, meta) {
		return '<a href class="b-btn b-btn_green" ng-click="trackerCtrl.viewLogs(\'' + data.id + '\')">View logs</a>';
	}

	function loadOperations() {
		var logsDates = getDateInStringFormat();
		if(logsDates) {
			vm.dtInstance.reloadData(null, false);
		}
	}

	function viewLogs(id) {
		vm.currentLog = [];
		ngDialog.open({
			template: 'subutai-app/tracker/partials/logsPopup.html',
			scope: $scope
		});

		trackerSrv.getOperation(vm.selectedModule, id).success(function (data) {
			var logsArray = data.log.split(/(?:\r\n|\r|\n)/g);
			var logs = [];
			for(var i = 0; i < logsArray.length; i++) {
				var currentLog = JSON.parse(logsArray[i].substring(0, logsArray[i].length - 1));
				currentLog.date = moment(currentLog.date).format('HH:mm:ss');
				logs.push(currentLog);
			}
			vm.currentLog = logs;
			console.log(vm.currentLog);
		});
	}

	function getDateInStringFormat() {
		var result = {};
		if(vm.startDate === null || isNaN(Date.parse(vm.startDate))) return;
		if(vm.endDate === null || isNaN(Date.parse(vm.endDate))) return;

		result.startDateString = vm.startDate.getFullYear() + '-' 
			+ vm.startDate.getMonthFormatted() + '-' 
			+ vm.startDate.getDateFormatted();

		result.endDateString = vm.endDate.getFullYear() + '-' 
			+ vm.endDate.getMonthFormatted() + '-' 
			+ vm.endDate.getDateFormatted();
		
		return result;
	}
}

Date.prototype.getMonthFormatted = function() {
	var month = this.getMonth() + 1;
	return month < 10 ? '0' + month : '' + month;
}

Date.prototype.getDateFormatted = function() {
	var day = this.getDate();
	return day < 10 ? '0' + day : '' + day;
}
