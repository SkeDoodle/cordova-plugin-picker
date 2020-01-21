const exec = cordova.require('cordova/exec');

class CordovaPluginPicker {
    constructor() { }

    share(message, onSuccess, onError) {
        exec(onSuccess, onError, 'Picker', 'share', [message]);
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = CordovaPluginPicker;
}