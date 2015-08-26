//
//  CDVGPSTracker.swift
//  MBTEST
//
//  Created by Rakesh Kalyankar on 24/08/15.
//
//

import CoreLocation

class CDVGPSTracker: NSObject, CLLocationManagerDelegate {
    // MARK: Global Variables
    var locationManager: CLLocationManager!
    var locationStatus = "Not Started"
    // MARK: Methods
    override init () {
        super.init()
        locationManager = CLLocationManager()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }
    
    func isAuthorized() -> Bool {
        let authorizationStatusClassPropertyAvailable = CLLocationManager.respondsToSelector("authorizationStatus")
        if authorizationStatusClassPropertyAvailable {
            let authStatus = CLLocationManager.authorizationStatus()
            // prompt to the user requesting for Always authorization to use location services
            if #available(iOS 8.0, *) {
                locationManager.requestWhenInUseAuthorization()
                return (authStatus == .AuthorizedWhenInUse) || (authStatus == .AuthorizedAlways) || (authStatus == .NotDetermined)
            }
            return (authStatus == .Authorized) || (authStatus == .NotDetermined)
        }
        return true
    }
    func isLocationServicesEnabled() -> Bool {
        let locationServicesEnabledClassPropertyAvailable = CLLocationManager.respondsToSelector("locationServicesEnabled")
        if locationServicesEnabledClassPropertyAvailable {
            return CLLocationManager.locationServicesEnabled()
        }
        return false
    }
    // MARK: Exposed Methods
    func startLocation() {
        locationManager.stopUpdatingLocation()
        locationManager.startUpdatingLocation()
    }
    func stopLocation() {
        locationManager.stopUpdatingLocation()
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
            //start location services
            locationManager.startUpdatingLocation()
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
    }
}
