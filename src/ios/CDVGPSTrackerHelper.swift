//
//  CDVGPSTrackerHelper.swift
//  MBTEST
//
//  Created by Rakesh Kalyankar on 23/08/15.
//
//

import Foundation
import CoreLocation
import CoreMotion

enum CDVLocationStatus: Int {
    case PERMISSIONDENIED = 1
    case POSITIONUNAVAILABLE = 2
    case TIMEOUT = 3
}
class CDVLocationData {
    var locationInfo: CLLocation!
    var locationCallbacks: NSMutableArray!
    var watchCallbacks: NSMutableDictionary!
    init () {
            self.locationInfo = nil
            self.locationCallbacks = nil
            self.watchCallbacks = nil
    }
}

@objc(CDVGPSTrackerHelper) class CDVGPSTrackerHelper: CDVPlugin, CLLocationManagerDelegate {
    // MARK: Global Variables
    var locationManager: CLLocationManager?
    var activityManager: CMMotionActivityManager?
    var activityArray = [String]()
    var locationStatus = "Not Started"
    var locationData: CDVLocationData?
    /// Flag to determine whether to command start or stop updating location.
    var commandStartUpdatingLocation = true
    var __locationStarted = false
    var __highAccuracyEnabled = false

    // MARK: CDVPlugin overrides
    override func pluginInitialize() {
        self.locationManager = CLLocationManager()
        self.locationManager!.delegate = self
        self.locationManager!.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        if #available(iOS 8.0, *) {
            self.locationManager!.requestAlwaysAuthorization()
        } else {
            // Fallback on earlier versions
        }
        print("gpsagent Initialized")
    }
    
    // MARK: methods exposed to JS
    func echo(command: CDVInvokedUrlCommand){
        dispatch_async(dispatch_get_main_queue()) {
            // do your stuff here
            var message = command.arguments[0] as! String
            message = message.uppercaseString
            print(message)
            //self.locationManager!.startUpdatingLocation()
            self.startActivityMonitoring()
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsString: message)
            self.commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
        }
        
    }
    
    func configure(command: CDVInvokedUrlCommand) {
        
    }
    // function to get location
    func getLocation(command: CDVInvokedUrlCommand) {
        stopActivityMonitoring()
        dispatch_async(dispatch_get_main_queue()) {
            let callbackId: String? = command.callbackId
            let enableHighAccuracy = command.arguments[0].boolValue ?? true
            if !self.isLocationServicesEnabled() {
                let result = self.returnLocationError(UInt(CDVLocationStatus.PERMISSIONDENIED.rawValue), withMessage: "Location services are disabled.")
                self.commandDelegate!.sendPluginResult(result, callbackId: callbackId)
                print("getLocation: LocationServicesDisabled")
            } else {
                if self.locationData == nil {
                    self.locationData = CDVLocationData()
                    print("getLocation: locationData is nill")
                }
                var lData  = self.locationData
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
                    self.startLocation(enableHighAccuracy)
                } else {
                    self.returnLocationInfo(callbackId!, andKeepCallback: false)
                    print("getLocation: to return location")
                }
            }
        }
    }
    func start(command: CDVInvokedUrlCommand) {
        
    }
    
    func stop(command: CDVInvokedUrlCommand){
        
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
            NSLog("Location Allowed")
//            //start location services
//            locationManager!.startUpdatingLocation()
        }else {
            NSLog("Denied access:  \(locationStatus)")
            
        }
    }
    
    func locationManager(manager: CLLocationManager, didFailWithError error: NSError) {
        print("didFailWithError: \(error.description)")
        let errorAlert = UIAlertView(title: "Error", message: "Failed to Get Your Location", delegate: nil, cancelButtonTitle: "Ok")
        errorAlert.show()
    }
    
    func locationManager(manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let newLocation: CLLocation! = locations.last
        print("current position: \(newLocation.coordinate.longitude) , \(newLocation.coordinate.latitude)")
        var cData = self.locationData
        cData?.locationInfo = newLocation
        if self.locationData?.locationCallbacks.count > 0 {
            for callbackId in (self.locationData?.locationCallbacks)! {
                self.returnLocationInfo(callbackId as! String, andKeepCallback: false)
            }
            self.locationData?.locationCallbacks.removeAllObjects()
        } else {
            // No callbacks waiting on us anymore, turn off listening.
            self._stopLocation()
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
    func startLocation(enableHighAccuracy: Bool) {
        // if location services are not available
        if !self.isLocationServicesEnabled() {
            let result = self.returnLocationError(UInt(CDVLocationStatus.PERMISSIONDENIED.rawValue), withMessage: "Location services are disabled.")
            print("startLocation: location services disabled")
            sendLocationErrorResult(result)
            return
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
            return
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
                return
            }
        }
        // Tell the location manager to start notifying us of location updates. We
        // first stop, and then start the updating to ensure we get at least one
        // update, even if our location did not change.
        self.locationManager?.stopUpdatingLocation()
        self.locationManager?.startUpdatingLocation()
        __locationStarted = true
        if enableHighAccuracy {
            print("startLocation: high accuracy location")
            __highAccuracyEnabled = true
            // Set distance filter to 5 for a high accuracy. Setting it to "kCLDistanceFilterNone" could provide a
            // higher accuracy, but it's also just spamming the callback with useless reports which drain the battery.
            self.locationManager?.distanceFilter = 5
            // Set desired accuracy to Best.
            self.locationManager?.desiredAccuracy = kCLLocationAccuracyBest
        } else {
            print("startLocation: low accuracy location")
            __highAccuracyEnabled = false
            self.locationManager?.distanceFilter = 10
            self.locationManager?.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        }
    }
    // stop location updates
    func _stopLocation(){
        if __locationStarted {
            if !self.isLocationServicesEnabled(){
                return
            }
            self.locationManager?.stopUpdatingLocation()
            __locationStarted = false
            __highAccuracyEnabled = false
        }
    }
    // return location received to callback
    func returnLocationInfo(callbackId: String, andKeepCallback keepCallback: Bool){
        var result: CDVPluginResult?
        let lData = self.locationData
        print("returnLocationInfo: lData \(lData)")
        if (lData != nil) && (lData?.locationInfo != nil) {
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
    
}
