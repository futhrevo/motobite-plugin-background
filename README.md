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
var success = function(obj){Date.now() + console.log(obj)}
var failure = function(){"failed"}
mb.configure()
mb.addGeofence(config,success,failure)
