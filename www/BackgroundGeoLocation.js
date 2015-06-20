var exec = require('cordova/exec');

module.exports = {
	config: {},

	start: function(success, failure, config) {
        exec(success || function() {},
             failure || function() {},
             'BackgroundGeoLocation',
             'start',
             []);
    },
	stop: function(success, failure, config) {
        exec(success || function() {},
            failure || function() {},
            'BackgroundGeoLocation',
            'stop',
            []);
    },
    configure: function(success, failure, config) {
        this.config = config;
        var user = "tester"; // TODO: use Meteor.userId here when integrating
            exec(success || function() {},
                failure || function() {},
                'BackgroundGeoLocation',
                'configure',
                [user]);
        },
}
;
