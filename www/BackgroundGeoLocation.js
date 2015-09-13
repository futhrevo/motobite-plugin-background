var exec = require('cordova/exec');

module.exports = {
    config: {},

    start: function (config, success, failure) {
        exec(success || function () { },
            failure || function () { },
            'BackgroundGeoLocation',
            'start',
            []);
    },
    stop: function (config, success, failure) {
        exec(success || function () { },
            failure || function () { },
            'BackgroundGeoLocation',
            'stop',
            []);
    },
    configure: function (config, success, failure) {
        this.config = config;
        var user = "tester"; // TODO: use Meteor.userId here when integrating
        exec(success || function () { },
            failure || function () { },
            'BackgroundGeoLocation',
            'configure',
            [user]);
    },
    getLocation: function (config, success, failure) {
        exec(success || function () { },
            failure || function () { },
            'BackgroundGeoLocation',
            'getLocation',
            [true]);
    },
    // add geofences
    addGeofence: function (config, success, failure) {
        this.config = config || {};
        if (!config.name) {
            throw "www - addGeofence : name is not included in config";
        }
        // id is _id from collection, should be unique
        // type can be "safeHouse", "pickup", "poi"
        if (!config.id && !config.type) {
            throw "www - addGeofence : need a id and type for mapping to collection";
        }
        if (!config.lat && !config.lng) {
            throw "www - addGeofence : latitude and longitude are not defined";
        }
        if (!config.radius) {
            throw "www - addGeofence : radius is not mentioned";
        }
        exec(success || function () { },
            failure || function () { },
            'BackgroundGeoLocation',
            'addGeofence',
            [config]);
    },
    // remove a geofence
    removeGeofence: function (config, success, failure) {
        if (!config.id) {
            throw "www - removeGeofence : need to specify id in order to remove the geofence"
        }
        exec(success || function () { },
            failure || function () { },
            'BackgroundGeoLocation',
            'removeGeofence',
            [config]);
    },
     //remove all geofences registered
    removeallGeofences: function (success, failure) {
         exec(success || function () { },
            failure || function () { },
            'BackgroundGeoLocation',
            'removeAllGeofences',
            []);
     }
};

