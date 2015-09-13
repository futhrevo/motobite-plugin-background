//
//  CDVGPSTracker.swift
//  MBTEST
//
//  Created by Rakesh Kalyankar on 13/09/15.
//
//

import Foundation
import CoreLocation

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

class jsCollection {
    var coll = [[String:AnyObject]]()
    init() {
        self.coll = []
    }
    func insert(obj: [String: AnyObject]) {
        let id = obj["id"] as! String
        let index = findIndexbyId(id)
        if (index != nil) {
            coll[index!] = obj
        } else {
            coll.append(obj)
        }
        
    }
    func remove(id: String) {
        let index = findIndexbyId(id)
        if (index != nil) {
            coll.removeAtIndex(index!)
        }
    }
    func contains(id: String) -> (result: Bool, index: Int?){
        let index = findIndexbyId(id)
        if (index != nil) {
            return (true,index!)
        }
        return (false,nil)
    }
    func findIndexbyId(id:String) -> Int?{
        for (index, obj) in EnumerateSequence(coll) {
            if obj["id"] as! String == id {
                return index
            }
        }
        return nil
    }
    func getCallbackid(index: Int) -> String?{
        return coll[index]["callbackId"] as? String
    }
}