//
//  CDVGPSTracker.swift
//  MBTEST
//
//  Created by Rakesh Kalyankar on 23/08/15.
//
//

import Foundation
import CoreLocation
import CoreMotion
import AudioToolbox

let TAG = "CDVGPSTracker"
let iOS8 = floor(NSFoundationVersionNumber) > floor(NSFoundationVersionNumber_iOS_7_1)
let iOS7 = floor(NSFoundationVersionNumber) <= floor(NSFoundationVersionNumber_iOS_7_1)

func log(message: String){
    NSLog("%@ - %@", TAG, message)
}


@objc(CDVGPSTracker) class CDVGPSTracker: CDVPlugin, CLLocationManagerDelegate {
    // MARK: Global Variables
    var locationManager: CLLocationManager?
    var activityManager: CMMotionActivityManager?
    var activityArray = [String]()
    var locationStatus = "Not Started"
    var locationData: CDVLocationData?
    var locationUpdateMode: CDVLocationMode = .NONE
    /// Flag to determine whether to command start or stop updating location.
    var commandStartUpdatingLocation = true
    var __locationStarted = false
    var alertShowing = false
    var __badgecount:Int = 0
    var safeHouses = jsCollection()
    var pickup = jsCollection()
    var poi = jsCollection()
    let priority = DISPATCH_QUEUE_PRIORITY_DEFAULT
    
    // MARK: CDVPlugin overrides
    override func pluginInitialize() {
        self.locationManager = CLLocationManager()
        self.locationManager!.delegate = self
        self.locationManager!.desiredAccuracy = kCLLocationAccuracyBest
        promptForNotificationPermission()
        if !CLLocationManager.locationServicesEnabled(){
            log("Location services are disabled")
        } else {
            log("Location services are enabled")
        }
        if #available(iOS 8.0, *) {
            self.locationManager!.requestAlwaysAuthorization()
        } else {
            // Fallback on earlier versions
        }
        if !CLLocationManager.isMonitoringAvailableForClass(CLRegion){
            log("Geofencing not available")
        }
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "onPause", name: UIApplicationDidEnterBackgroundNotification, object: nil)
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "onResume", name: UIApplicationDidBecomeActiveNotification, object: nil)
        log("gpsagent Initialized")
    }
    
    override func onReset() {
        log("onReset called, check the implementation")
        super.onReset()
    }
    override func onAppTerminate() {
        log("onAppTerminate called , check the implementation")
        super.onAppTerminate()
    }
    override func onMemoryWarning() {
        log("onMemory warning called, check the implementation")
        super.onMemoryWarning()
    }
    // MARK: methods exposed to JS
 
    func configure(command: CDVInvokedUrlCommand) {
        
    }
    func start(command: CDVInvokedUrlCommand) {
        
    }
    
    func stop(command: CDVInvokedUrlCommand){
        
    }
    func echo(command: CDVInvokedUrlCommand){
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            var message = command.arguments[0] as! String
            message = message.uppercaseString
            log(message)
            self.notifyLocalAbout(message)
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsString: message)
            self.commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
        }
        
    }
    // function to get location
    func getLocation(command: CDVInvokedUrlCommand) {
        stopActivityMonitoring()
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            let callbackId: String? = command.callbackId
            let enableHighAccuracy = command.arguments[0].boolValue ?? true
            if !self.isLocationServicesEnabled() {
                let result = self.returnLocationError(UInt(CDVLocationStatus.PERMISSIONDENIED.rawValue), withMessage: "Location services are disabled.")
                self.commandDelegate!.sendPluginResult(result, callbackId: callbackId)
                log("getLocation: LocationServicesDisabled")
            } else {
                if self.locationData == nil {
                    self.locationData = CDVLocationData()
                    log("getLocation: locationData is nill")
                }
                let lData  = self.locationData
                if lData?.locationCallbacks == nil {
                    lData?.locationCallbacks = NSMutableArray.init(capacity: 1)
                }
                if !self.__locationStarted {
                    // add the callbackId into the array so we can call back when get data
                    if (callbackId != nil) {
                        lData?.locationCallbacks.addObject(callbackId!)
                    }
                    // Tell the location manager to start notifying us of location updates
                    print("getLocation: to start location")
                    self.startLocation(CDVLocationMode.ONESHOT)
                } else {
                    self.returnLocationInfo(callbackId!, andKeepCallback: false)
                    print("getLocation: to return location")
                }
            }
        }
    }
    
    func addGeofence(command: CDVInvokedUrlCommand){
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            if !CLLocationManager.isMonitoringAvailableForClass(CLCircularRegion){
                log("Geofencing is not supported on this device!")
                let result = self.returnLocationError(UInt(CDVLocationStatus.PERMISSIONDENIED.rawValue), withMessage: "Location services are disabled.")
                self.commandDelegate!.sendPluginResult(result, callbackId: command.callbackId)
                return
            }
            let cfg = command.argumentAtIndex(0)
            let callbackId = command.callbackId as String
            // check if location services are enabled
            if !self.isLocationServicesEnabled() {
                let result = self.returnLocationError(UInt(CDVLocationStatus.PERMISSIONDENIED.rawValue), withMessage: "Location services are disabled.")
                self.commandDelegate!.sendPluginResult(result, callbackId: command.callbackId)
                log("addGeofence: LocationServicesDisabled")
                return
            }
            let location = CLLocationCoordinate2DMake(cfg["lat"] as! Double, cfg["lng"] as! Double)
            let radius = cfg["radius"] as! Double as CLLocationDistance
            let id = cfg["id"] as! String
            let type = cfg["type"] as! String
            let obj = ["id":id,"callbackId":callbackId]
            switch type {
            case "safeHouse":
                self.safeHouses.insert(obj)
            case "pickup":
                self.pickup.insert(obj)
            default:
                self.poi.insert(obj)
            }
            let region = CLCircularRegion(center: location, radius: radius, identifier: id)
            self.locationManager?.startMonitoringForRegion(region)
            log("addGeofence: a new geofence added with id: \(id)")
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
            pluginResult.setKeepCallbackAsBool(true)
            self.commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
        }
    }
    func removeGeofence(command: CDVInvokedUrlCommand){
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            let cfg = command.argumentAtIndex(0)
            let id = cfg["id"] as! String
            let type = self.checkFenceName(id)
            // check if the id already exist
            if (type.type != nil) {
                let type = type.type as String!
                switch type {
                case "safeHouse":
                    self.safeHouses.remove(id)
                case "pickup":
                    self.pickup.remove(id)
                default:
                    self.poi.remove(id)
                }
                self.removeGeofenceWithId(id)
                log("removeGeofence: geofence removed with id: \(id)")
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
                self.commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
                return
            }
            log("removeGeofence: unable to remove geofence with id: \(id)")
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
            self.commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
        }
    }
    func removeAllGeofences(command: CDVInvokedUrlCommand) {
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            for region in (self.locationManager?.monitoredRegions)! {
                self.locationManager?.stopMonitoringForRegion(region)
            }
            self.safeHouses.coll = []
            self.pickup.coll = []
            self.poi.coll = []
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
            self.commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
        }
    }
    // MARK: Delegate method for Location Manager
    func locationManager(manager: CLLocationManager, didChangeAuthorizationStatus status: CLAuthorizationStatus) {
        var shouldIAllow = false
        switch status {
        case CLAuthorizationStatus.Restricted:
            locationStatus = "Restricted Access to location"
        case CLAuthorizationStatus.Denied:
            locationStatus = "User denied access to location"
        case CLAuthorizationStatus.NotDetermined:
            locationStatus = "Status not determined"
        default:
            locationStatus = "Allowed to access location"
            shouldIAllow = true
        }
        if(shouldIAllow) {
            log("Location Allowed")
            //            //start location services
            //            locationManager!.startUpdatingLocation()
        }else {
            log("Denied access:  \(locationStatus)")
            
        }
    }
    
    func locationManager(manager: CLLocationManager, didFailWithError error: NSError) {
        print("didFailWithError: \(error.description)")
        log("Error: Failed to get Your Location")
    }
    
    func locationManager(manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let newLocation: CLLocation! = locations.last
        print("current position: \(newLocation.coordinate.longitude) , \(newLocation.coordinate.latitude)")
        let cData = self.locationData
        cData?.locationInfo = newLocation
        if self.locationData?.locationCallbacks.count > 0 {
            log("Send to returnLocationInfo with callbackId")
            for callbackId in (self.locationData?.locationCallbacks)! {
                self.returnLocationInfo(callbackId as! String, andKeepCallback: false)
            }
            self.locationData?.locationCallbacks.removeAllObjects()
        } else {
            // No callbacks waiting on us anymore, turn off listening.
            log("No callbacks waiting on us anymore, turn off listening")
            self._stopLocation()
        }
        
    }
    
    func locationManagerDidPauseLocationUpdates(manager: CLLocationManager) {
        log("Location updating is paused")
    }
    func locationManagerDidResumeLocationUpdates(manager: CLLocationManager) {
        log("Location updating was resumed")
    }
    // MARK: Delegate methods for Geofences
    func locationManager(manager: CLLocationManager, monitoringDidFailForRegion region: CLRegion?, withError error: NSError) {
        let id = region?.identifier
        let type = checkFenceName(id!)
        if (type.type != nil) {
            log("locationManager Monitoring failed for \(type.type) with id: \(id)")
            log(error.description)
        }
    }
    func locationManager(manager: CLLocationManager, didEnterRegion region: CLRegion) {
        if region is CLCircularRegion {
            log("Region entered \(region.identifier)")
            let type = checkFenceName(region.identifier)
            let callbackId:String
            if (type.type != nil && type.index != nil) {
                let ltype = type.type as String!
                switch ltype {
                case "safeHouse":
                    callbackId = safeHouses.getCallbackid(type.index!)!
                case "pickup":
                    callbackId = pickup.getCallbackid(type.index!)!
                default:
                    callbackId = poi.getCallbackid(type.index!)!
                }
                var returnInfo = [NSObject : AnyObject]()
                let timestamp  = NSDate().timeIntervalSince1970
                returnInfo["timestamp"] = NSNumber(double:timestamp)
                returnInfo["_id"] = NSString(string:region.identifier)
                returnInfo["entry"] = true
                returnInfo["type"] = NSString(string:ltype)
                log("Entry at \(timestamp) with callbackId \(callbackId)")
                dispatch_async(dispatch_get_main_queue()) {
                let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: returnInfo)
                result.setKeepCallbackAsBool(true)
                    if (result != nil) {
                        self.commandDelegate?.sendPluginResult(result, callbackId: callbackId)
                    }

                }
            }
        }
    }
    func locationManager(manager: CLLocationManager, didExitRegion region: CLRegion) {
        if region is CLCircularRegion {
            log("Region exit \(region.identifier)")
            let type = checkFenceName(region.identifier)
            let callbackId:String
            if (type.type != nil && type.index != nil) {
                let ltype = type.type as String!
                switch ltype {
                case "safeHouse":
                    callbackId = safeHouses.getCallbackid(type.index!)!
                case "pickup":
                    callbackId = pickup.getCallbackid(type.index!)!
                default:
                    callbackId = poi.getCallbackid(type.index!)!
                }
                var returnInfo = [NSObject : AnyObject]()
                let timestamp  = NSDate().timeIntervalSince1970
                returnInfo["timestamp"] = NSNumber(double: timestamp)
                returnInfo["_id"] = NSString(string: region.identifier)
                returnInfo["exit"] = true
                returnInfo["type"] = NSString(string:type.type!)
                dispatch_async(dispatch_get_main_queue()) {
                let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: returnInfo)
                result.setKeepCallbackAsBool(true)
                if (result != nil) {
                        self.commandDelegate?.sendPluginResult(result, callbackId: callbackId)
                    }
                }
            }
        }
    }
    func locationManager(manager: CLLocationManager, didDetermineState state: CLRegionState, forRegion region: CLRegion) {
        switch state {
        case .Inside:
            log("User already inside geofence, check if callbackId works")
            locationManager(locationManager!, didEnterRegion: region)
        
        default:
            log("TODO handling if no one is inside geofence")
        }
    }
    // MARK: GPS Helper methods
    func isAuthorized() -> Bool {
        let authorizationStatusClassPropertyAvailable = CLLocationManager.respondsToSelector("authorizationStatus")
        if authorizationStatusClassPropertyAvailable {
            let authStatus = CLLocationManager.authorizationStatus()
            // prompt to the user requesting for Always authorization to use location services
            if #available(iOS 8.0, *) {
                locationManager!.requestWhenInUseAuthorization()
                return (authStatus == .AuthorizedWhenInUse) || (authStatus == .AuthorizedAlways) || (authStatus == .NotDetermined)
            }
            return (authStatus == .Authorized) || (authStatus == .NotDetermined)
        }
        return true
    }
    // check if location is enabled
    func isLocationServicesEnabled() -> Bool {
        let locationServicesEnabledClassPropertyAvailable = CLLocationManager.respondsToSelector("locationServicesEnabled")
        if locationServicesEnabledClassPropertyAvailable {
            return CLLocationManager.locationServicesEnabled()
        }
        return false
    }
    // start location updates
    func startLocation(mode: CDVLocationMode) -> Bool{
        var enableHighAccuracy: Bool = true
        // if location services are not available
        if !self.isLocationServicesEnabled() {
            let result = self.returnLocationError(UInt(CDVLocationStatus.PERMISSIONDENIED.rawValue), withMessage: "Location services are disabled.")
            print("startLocation: location services disabled")
            sendLocationErrorResult(result)
            return false
        }
        if !self.isAuthorized() {
            print("startLocation: not Authorized")
            var message: String?
            let code = CLLocationManager.authorizationStatus()
            if code == .NotDetermined {
                message = "User undecided on application's use of location services"
            } else if code == .Restricted {
                message = "Application's use of location services is restricted"
            }
            let result = self.returnLocationError(UInt(CDVLocationStatus.PERMISSIONDENIED.rawValue), withMessage: message!)
            sendLocationErrorResult(result)
            return false
        }
        if #available(iOS 8.0, *) {
            print("startLocation: ios8 available")
            let code = CLLocationManager.authorizationStatus()
            if code == .NotDetermined {
                if (NSBundle.mainBundle().objectForInfoDictionaryKey("NSLocationAlwaysUsageDescription") != nil) {
                    self.locationManager?.requestAlwaysAuthorization()
                } else if (NSBundle.mainBundle().objectForInfoDictionaryKey("NSLocationWhenInUseUsageDescription") != nil) {
                    self.locationManager?.requestWhenInUseAuthorization()
                } else {
                    NSLog("[Warning] No NSLocationAlwaysUsageDescription or NSLocationWhenInUseUsageDescription key is defined in the Info.plist file.")
                }
                return false
            }
        }
        // Tell the location manager to start notifying us of location updates. We
        // first stop, and then start the updating to ensure we get at least one
        // update, even if our location did not change.
        _stopLocation()
        __locationStarted = true
        switch mode {
            case .ONESHOT:
                locationUpdateMode = .ONESHOT
                if #available(iOS 9.0, *) {
                    locationManager?.requestLocation()
                } else {
                    // Fallback on earlier versions
                    self.locationManager?.startUpdatingLocation()
                }
            case .NAVMODE:
                locationUpdateMode = .NAVMODE
                locationManager?.desiredAccuracy = kCLLocationAccuracyBestForNavigation
                locationManager?.startUpdatingLocation()
                return true
            case .BESTMODE:
                locationUpdateMode = .BESTMODE
                locationManager?.desiredAccuracy = kCLLocationAccuracyBest
                locationManager?.startUpdatingLocation()
                return true
            case .SIGMODE:
                locationUpdateMode = .SIGMODE
                if CLLocationManager.significantLocationChangeMonitoringAvailable() {
                    return true
                } else {
                    locationManager?.startUpdatingLocation()
                    return true
            }
            default:
                locationUpdateMode = .NONE
                log("No mode specified, nothing to start")
                return true
            
        }
        return false
    }
    // stop location updates
    func _stopLocation(){
        if __locationStarted {
            if !self.isLocationServicesEnabled(){
                return
            }
            log("Stopping monitoring location changes")
            switch locationUpdateMode {
            case .SIGMODE:
                    self.locationManager?.stopMonitoringSignificantLocationChanges()
            default:
                self.locationManager?.stopUpdatingLocation()
            }
            locationUpdateMode = .NONE
            __locationStarted = false
        }
    }
    // return location received to callback
    func returnLocationInfo(callbackId: String, andKeepCallback keepCallback: Bool){
        var result: CDVPluginResult?
        let lData = self.locationData
        log("locationdata \(lData?.locationInfo)")
        print("returnLocationInfo: lData \(lData)")
        if (lData == nil) && (lData?.locationInfo == nil) {
            // TODO: change hardcoded value to enum
            print("returnLocationInfo: lData is nil")
            result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageToErrorObject: 2)
        } else {
            let lInfo: CLLocation = (lData?.locationInfo)!
            print("returnLocationInfo: lInfo \(lInfo)")
            let timestamp  = NSNumber(double: lInfo.timestamp.timeIntervalSince1970 * 1000 )
            var returnInfo = [NSObject : AnyObject]()
            returnInfo["timestamp"] = timestamp
            returnInfo["velocity"] = NSNumber(double: lInfo.speed)
            returnInfo["altitudeAccuracy"] = NSNumber(double: lInfo.verticalAccuracy)
            returnInfo["accuracy"] = NSNumber(double: lInfo.horizontalAccuracy)
            returnInfo["heading"] = NSNumber(double: lInfo.course)
            returnInfo["altitude"] = NSNumber(double: lInfo.altitude)
            returnInfo["latitude"] = NSNumber(double: lInfo.coordinate.latitude)
            returnInfo["longitude"] = NSNumber(double: lInfo.coordinate.longitude)
            result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: returnInfo)
            result?.setKeepCallbackAsBool(keepCallback)
        }
        if (result != nil) {
            self.commandDelegate?.sendPluginResult(result, callbackId: callbackId)
        }
    }
    // packing error code to be sent as pluginresult
    func returnLocationError(errorCode: UInt, withMessage message:String) -> CDVPluginResult{
        var posError = [NSObject : AnyObject]()
        posError["code"] = errorCode
        posError["message"] = message.isEmpty ? message : ""
        return CDVPluginResult(status: CDVCommandStatus_ERROR, messageAsDictionary: posError)
        
    }
    // send result to js
    func sendLocationErrorResult(result: CDVPluginResult) {
        for callbackId in (self.locationData?.locationCallbacks)! {
            self.commandDelegate!.sendPluginResult(result, callbackId: callbackId as! String)
        }
        self.locationData?.locationCallbacks.removeAllObjects()
    }
    
    // MARK: Activity Manager Helpers
    func startActivityMonitoring() {
        // If activity updates are supported, start updates on the motionQueue
        if CMMotionActivityManager.isActivityAvailable() {
            print("Start Activity updates")
            let manager = CMMotionActivityManager()
            manager.startActivityUpdatesToQueue(NSOperationQueue(), withHandler: {(activity) -> Void in
                switch activity!.confidence {
                case CMMotionActivityConfidence.High, CMMotionActivityConfidence.Medium:
                    if activity!.walking {
                        if self.updateActivityArray("walking") {
                            print("MotionTypeWalking")
                        }
                    } else if activity!.automotive {
                        if self.updateActivityArray("automotive") {
                            print("MotionTypeDriving")
                        }
                    } else if activity!.stationary || activity!.unknown {
                        if self.updateActivityArray("stationary") {
                            print("MotionTypeNotMoving")
                        }
                        
                    }
                default:
                    print(activity!.confidence.rawValue)
                }
                
            })
            self.activityManager = manager
        } else {
            print("Activity not Available")
        }
    }
    func stopActivityMonitoring() {
        print("Stop Activity updates")
        if let activityManager = activityManager {
            activityManager.stopActivityUpdates()
        }
        activityManager = nil
    }
    // function to return stable activity sampled for 5 times
    func updateActivityArray(activity: String) -> Bool{
        print(activityArray.count)
        print(activityArray.last)
        // if count is 5 then time to change activity
        if activityArray.count > 4 {
            if activityArray.last == activity {
                activityArray = []
                return true
            }else{
                // if array contains different value rest the array
                activityArray = []
                activityArray.append(activity)
            }
        } else if activityArray.count > 0 {
            // if array is not full append the same value
            if activityArray.last == activity {
                activityArray.append(activity)
            } else {
                // if array contains different value rest the array
                activityArray = []
                activityArray.append(activity)
            }
        } else {
            // if array is empty append the value
            activityArray.append(activity)
        }
        return false
    }
    // MARK: Geofence Helper methods
    func checkFenceName(id:String) -> (type:String? , index: Int?){
        var res = safeHouses.contains(id)
        if res.result{
            return ("safeHouse",res.index)
        }
        res = pickup.contains(id)
        if res.result{
            return ("pickup",res.index)
        }
        res = poi.contains(id)
        if res.result{
            return ("poi",res.index)
        }
        return (nil,nil)
    }
    // remove a geofence with id provided from removeGeofence
    func removeGeofenceWithId(id:String) {
        for region in (locationManager?.monitoredRegions)! {
            if let circularRegion = region as? CLCircularRegion {
                if circularRegion.identifier == id {
                    locationManager?.stopMonitoringForRegion(circularRegion)
                }
            }
        }
    }
    
    // MARK: Notification Helpers
    func promptForNotificationPermission(){
        if #available(iOS 8.0, *) {
            let types: UIUserNotificationType = [.Badge, .Alert, .Sound]
            let settings = UIUserNotificationSettings(forTypes: types, categories: nil)
            UIApplication.sharedApplication().registerUserNotificationSettings(settings)
        } else {
            // Fallback on earlier versions
            log("pre ios8 doesnt ask any permissions")
        }
        
    }
    func notifyLocalAbout(message: String){
        log("Creating local notification")
        let notification = UILocalNotification()
        notification.timeZone = NSTimeZone.defaultTimeZone()
        notification.fireDate = NSDate()
        notification.soundName  = UILocalNotificationDefaultSoundName
        notification.alertAction = "View Details"
        notification.alertBody = "\(message) Woww it works!!"
        notification.applicationIconBadgeNumber = 1
        if #available(iOS 8.2, *) {
            notification.alertTitle = "MotoBite"
        } else {
            // Fallback on earlier versions
        }
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
//    // Location mode for navigation when inside a geofence
//    func locBestNavMode(){
//        if locationManager != nil {
//            _stopLocation()
//            
//        }
//    }
//    // Location mode for significant changes
//    func locSignificantMode(){
//        
//    }
//    // Location mode for best accuracy
//    func locBestAccMode(){
//        
//    }
    // MARK: NSNotificationCenter helpers
    func onPause() {
        log("onPause payload: ")
    }
    func onResume() {
        log("onResume payload: ")
    }
}
