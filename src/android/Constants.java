package com.reku.motobite.cordova;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;

import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.GeofenceStatusCodes;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by rakeshkalyankar on 29/05/15.
 * Constants used in this plugin.
 */
public final class Constants {

    public Constants() {
    }

    public static final String PACKAGE_NAME = "com.reku.motobite.cordova";
    public static final String BROADCAST_ACTION = PACKAGE_NAME + ".BROADCAST_ACTION";
    public static final String ACTIVITY_EXTRA = PACKAGE_NAME + ".ACTIVITY_EXTRA";
    public static final String ACTIVITY_PROBABLE = PACKAGE_NAME + ".ACTIVITY_PROBABLE";
    public static final String SHARED_PREFERENCES_NAME = PACKAGE_NAME + ".SHARED_PREFERENCES";
    public static final String ACTIVITY_UPDATES_REQUESTED_KEY = PACKAGE_NAME + ".ACTIVITY_UPDATES_REQUESTED";
    public static final String SHARED_NOTIFYID_COUNT = PACKAGE_NAME + ".SHARED_NOTIFYID_COUNT";
    public static final String GEOFENCES_ADDED_KEY = PACKAGE_NAME + ".GEOFENCES_ADDED_KEY";
    public static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS = 12 * 60 * 60 * 1000;
    public static final float GEOFENCE_RADIUS_IN_METERS = 200;
    public static final int GEOFENCE_LOITERING_DELAY = 10000;
    public static final int GEOFENCE_RESPONSE = 10000;

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";
    public static final String ACTION_ECHO  = "echo";
    public static final String ACTION_GETLOCATION = "getLocation";
    public static final String ACTION_ADDGEOFENCE = "addGeofence";
    public static final String ACTION_REMOVEGEOFENCE  = "removeGeofence";
    public static final String ACTION_REMOVEALLGEOFENCES = "removeallGeofences";
    public static final String ACTION_NOTIFYABOUT = "notifyAbout";

    public static final int MSG_ACTION_START = 1;
    public static final int MSG_ACTION_STOP = 2;
    public static final int MSG_ACTION_CONFIGURE = 3;
    public static final int MSG_ACTION_ECHO  = 4;
    public static final int MSG_ACTION_GETLOCATION = 5;
    public static final int MSG_ACTION_ADDGEOFENCE = 6;
    public static final int MSG_ACTION_REMOVEGEOFENCE  = 7;
    public static final int MSG_ACTION_REMOVEALLGEOFENCES = 8;
    public static final int MSG_ACTION_NOTIFYABOUT = 9;

    public static final int MSG_POST_LOCATION = 110;

    public static final String CLOSE_ACTION = "close";

    /**
     * Command to the service to register a client, receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    public static final int MSG_REGISTER_CLIENT = 101;
    /**
     * Command to the service to unregister a client, ot stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 201;

    /**
     * Command to service to set a new value.  This can be sent to the
     * service to supply a new value, and will be sent by the service to
     * any registered clients with the new value.
     */
    public static final int MSG_SET_VALUE = 301;
    public static final String BROADCAST_GEOFENCE_RESULT = PACKAGE_NAME + ".GEOFENCE_RESULT";
    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000; // For Ex: 5000 for 5 seconds
    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2; // 16ms = 60fps
    /**
     * The desired time between activity detections. Larger values result in fewer activity
     * detections while improving battery life. A value of 0 results in activity detections at the
     * fastest possible rate. Getting frequent updates negatively impact battery life and a real
     * app may prefer to request less frequent updates.
     */
    public static final long DETECTION_INTERVAL_IN_MILLISECONDS = 20000;
    // to group notifications based on type to prevent stacking
    public static final  String GROUP_GEOFENCE_NOTIFICATIONS = "group_geofence_notifications";
    /**
     * Returns a human readable String corresponding to a detected activity type.
     */
    public static String getActivityString(Context context, int detectedActivityType) {
        Resources resources = context.getResources();
        switch(detectedActivityType) {
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.TILTING:
                return "TILTING";
            case DetectedActivity.UNKNOWN:
                return "UNKNOWN";
            case DetectedActivity.WALKING:
                return "WALKING";
            default:
                return "unidentifiable_activity : "+ detectedActivityType;
        }
    }
    /**
     * Returns the error string for a geofencing error code.
     */
    public static String getGeofenceErrorString(Context context, int errorCode) {
        Resources mResources = context.getResources();
        switch (errorCode) {
            case GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE:
                return "Geofence service is not available now";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES:
                return "Your app has registered too many geofences";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS:
                return "You have provided too many PendingIntents to the addGeofences() call";
            default:
                return "Unknown error: the Geofence service is not available now";
        }
    }

    public static boolean isActionValid(String action) {
        boolean result = false;

        if(ACTION_START.equalsIgnoreCase(action)) result = true;
        if(ACTION_STOP.equalsIgnoreCase(action)) result = true;
        if(ACTION_CONFIGURE.equalsIgnoreCase(action)) result = true;
        if(ACTION_ECHO.equalsIgnoreCase(action)) result = true;
        if(ACTION_GETLOCATION.equalsIgnoreCase(action)) result = true;
        if(ACTION_ADDGEOFENCE.equalsIgnoreCase(action)) result = true;
        if(ACTION_REMOVEGEOFENCE.equalsIgnoreCase(action)) result = true;
        if(ACTION_REMOVEALLGEOFENCES.equalsIgnoreCase(action)) result = true;
        if(ACTION_NOTIFYABOUT.equalsIgnoreCase(action)) result = true;


        return result;
    }
    public static JSONObject returnLocationJSON(Location loc) {
        JSONObject o = new JSONObject();

        try {
            o.put("latitude", loc.getLatitude());
            o.put("longitude", loc.getLongitude());
            o.put("altitude", (loc.hasAltitude() ? loc.getAltitude() : null));
            o.put("accuracy", loc.getAccuracy());
            o.put("heading",
                    (loc.hasBearing() ? (loc.hasSpeed() ? loc.getBearing()
                            : null) : null));
            o.put("velocity", loc.getSpeed());
            o.put("timestamp", loc.getTime());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return o;
    }
}



