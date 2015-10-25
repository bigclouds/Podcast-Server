import angular from 'angular';

class deviceDetectorService {
    constructor($window) {
        this.$window = $window;
    }

    isTouchedDevice() {
        return 'ontouchstart' in this.$window;
    }
}

export default angular
    .module('ps.common.component.device-detection', [])
    .service('deviceDetectorService', deviceDetectorService);