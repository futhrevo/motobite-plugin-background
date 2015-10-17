window.motobite.location.echo("Hello World")

var config = {name:"Home", id:"123uniq", type:"safeHouse", lat: 37.422, lng:-122.084058, radius:1000}
var success = function(obj){Date.now() + console.log(obj)}
var failure = function(){"failed"}
var mb = window.motobite.location
window.motobite.location.getLocation(true,success,failure)
mb.removeallGeofences(success,failure)
mb.addGeofence(config,success,failure)

var mb = window.motobite.location
var config = {name:"Home", id:"123uniq", type:"safeHouse", lat: 14.6743, lng:77.5919, radius:1000}
var config = {name: "Bangalore", id:"456uniq", type:"safeHouse", lat:12.9556361, lng:77.7015912, radius:100}
var success = function(obj){Date.now() + console.log(obj)}
var failure = function(obj){console.log(obj)}
mb.configure()
mb.addGeofence(config,success,failure)
cordova.exec(success,failure,'BackgroundGeoLocation','start',[{background:true}])
mb.stop({},success,failure)
cordova.exec(success,failure,'BackgroundGeoLocation','syncLoc',[])
com.motobite.cordova.location.plugin@file://../cordova/motobite-plugin-background

TODO:
COrrect MainActivity.class path
setGroup in GeofenceTransitionIntentService.java is unknown symbol