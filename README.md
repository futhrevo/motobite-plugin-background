var config = {name:"Home", id:"123uniq", type:"safeHouse", lat: 37.422, lng:-122.084058, radius:1000}
var success = function(obj){Date.now() + console.log(obj)}
var failure = function(){"failed"}
var mb = window.motobite.location
mb.removeallGeofences(success,failure)
mb.addGeofence(config,success,failure)