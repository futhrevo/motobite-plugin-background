package com.reku.motobite.cordova;


import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class GPStrackerHelper extends CordovaPlugin implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String TAG = GPStrackerHelper.class.getSimpleName();
    private static CordovaWebView webView;
    private Constants constants;
    private GoogleApiClient mGApiClient;
    private Location mLocation = null;
    private final int ACTIVITY_LOCATION_DIALOG = 100;
    private final LocationRequest mLocationRequestHighAccuracy = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    private static final float MIN_ACCURACY = 25.0f;
    private static final float MIN_LAST_READ_ACCURACY = 500.0f;

    private String getLocationCallbackId;
    private static String updateLocationCallbackId;

    private Intent updateServiceIntent;
    public static boolean isEnabled = false;
    public static boolean isInsideSafeHouse = false;
    public static boolean isInsidePickup = false;
    public static boolean isInsidePoi = false;
    public static boolean isLocationDialogShowing = false;
    private String stopOnTerminate = "false";
    private static HashMap<Integer,JSONObject> callbackHashMap = new HashMap<Integer,JSONObject>();
    public HashMap<String,String> safeHouses = new HashMap<String,String>();
    public HashMap<String,String> pickup = new HashMap<String, String>();
    public HashMap<String,String> poi = new HashMap<String, String>();

    public static boolean isBackgroundUpdatesRequired = false;
    /**
     * The list of geofences used in this sample
     */
    protected ArrayList<Geofence> mGeofenceList;
    /**
     * Used to keep track of whether geofences were added
     */
    private boolean mGeofencesAdded;
    /**
     * Used when requesting to add or remove geofences
     */
    private PendingIntent mGeofencePendingIntent;
    private BroadcastReceiver geofencingReceiver;
    private BroadcastReceiver activityReceiver;

    /** Messenger for communicating with service. */
    Messenger mService = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.i(TAG, "Plugin Init");
        this.webView = webView;
        doBindService();
        mGApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
            mGApiClient.connect();
         _checkLocationSettings();
        // Empty list for storing geofences.
        mGeofenceList = new ArrayList<Geofence>();
        // Initially set the PendingIntent used in addGeofences() and removeGeofences() to null
        mGeofencePendingIntent = null;
        // Get the value of mGeofencesAdded
        //mGeofencesAdded = getSharedPreferencesInstance().getBoolean(Constants.GEOFENCES_ADDED_KEY,false);
        geofencingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(Constants.BROADCAST_GEOFENCE_RESULT)){
                    Log.i(TAG, "Received broadcast " + intent.getAction());
                    onTransitionReceived(intent.getBundleExtra("geoBundle"));
                }
            }
        };
        activityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(Constants.BROADCAST_ACTION)){
                    Log.i(TAG,"Broadcast received");
                    ArrayList<DetectedActivity> updatedActivities =
                            intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);
                    DetectedActivity mostProbable = intent.getParcelableExtra(Constants.ACTIVITY_PROBABLE);
                }
            }
        };
        cordova.getActivity().registerReceiver(geofencingReceiver, new IntentFilter(Constants.BROADCAST_GEOFENCE_RESULT));
        cordova.getActivity().registerReceiver(activityReceiver, new IntentFilter(Constants.BROADCAST_ACTION));
    }


    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        Log.i(TAG, "TODO: handle onPause Called, handle the functions");
        if (isBackgroundUpdatesRequired && isEnabled){
            Message request = Message.obtain();
            request.what = Constants.MSG_ACTION_STOP;
            request.replyTo = mMessenger;
            try {
                mService.send(request);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if(mGApiClient != null){
            if (!mGApiClient.isConnected() || !mGApiClient.isConnecting()){
                mGApiClient.connect();
            }
        }
        if (isBackgroundUpdatesRequired && isEnabled){
            Message request = Message.obtain();
            request.what = Constants.MSG_ACTION_START;
            request.replyTo = mMessenger;
            try {
                mService.send(request);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        Log.i(TAG, "TODO: handle onResume");
    }

    @Override
    public void onReset() {
        super.onReset();
        Log.i(TAG, "TODO: onReset called, implement how to handle it");
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case ACTIVITY_LOCATION_DIALOG:
                // User was asked to enable the location setting.
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // All required changes were successfully made
                        Log.i(TAG, "user accepted to allow location");
                        requestLocationUpdates();
                        sendLastLocation();
                        break;
                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        Log.i(TAG, "user rejected to allow location");
                        break;
                    default:
                        break;
                }
                isLocationDialogShowing = false;
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onOverrideUrlLoading(String url) {
        Log.i(TAG,url);
        return super.onOverrideUrlLoading(url);
    }

    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) throws JSONException{
        Boolean result = false;
        if (this.constants == null){
            this.constants = new Constants();
        }
        try{
            if(mIsBound && constants.isActionValid(action)) {
                        if (Constants.ACTION_CONFIGURE.equalsIgnoreCase(action)) {

                            Log.i(TAG, "Configure called");

                        } else if (Constants.ACTION_ECHO.equalsIgnoreCase(action)) {
                            String message = null;
                            try {
                                message = data.getString(0);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Message request = Message.obtain();
                            request.what = Constants.MSG_ACTION_ECHO;
                            Bundle mBundle = new Bundle();
                            mBundle.putString("message", message);
                            request.setData(mBundle);
                            request.replyTo = mMessenger;
                            try {
                                addCallback(callbackContext.getCallbackId(), Constants.MSG_ACTION_ECHO);
                                mService.send(request);
                            } catch (RemoteException e) {
                                callbackContext.error("Could not reach service : " + e.getMessage());
                            }

                            Log.i(TAG, message);
                        } else if (Constants.ACTION_GETLOCATION.equalsIgnoreCase(action)){
                            Log.i(TAG, "get recent location");
                            getLocationCallbackId = callbackContext.getCallbackId();
                            _checkLocationSettings();
                            // user is only interested in result for last callbackId
                            sendLastLocation();
                        } else if (Constants.ACTION_ADDGEOFENCE.equalsIgnoreCase(action)){
                            Log.i(TAG,"Add Geofence");
                            final JSONObject cfg = data.getJSONObject(0);
                            cordova.getThreadPool().execute(new Runnable() {
                                @Override
                                public void run() {
                                    try{
                                        if (!addGeofence(cfg,callbackContext)){
                                            callbackContext.error("Error while adding a geofence");
                                        }
                                    }catch (Exception e){
                                        e.printStackTrace();
                                    }
                                }
                            });

                        } else if (Constants.ACTION_REMOVEGEOFENCE.equalsIgnoreCase(action)){
                            Log.i(TAG, "Remove a Geofence with given Id");
                            final JSONObject cfg = data.getJSONObject(0);
                            cordova.getThreadPool().execute(new Runnable() {
                                @Override
                                public void run() {
                                    String id = null;
                                    try {
                                        id = cfg.getString("id");
                                        if (removeGeofenceWithId(id)){
                                            callbackContext.success(id);
                                        } else{
                                            callbackContext.error("Unable to find geofence with ID");
                                        }
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });


                        } else if(Constants.ACTION_REMOVEALLGEOFENCES.equalsIgnoreCase(action)){
                            Log.i(TAG, "Remove all Geofences");
                            cordova.getThreadPool().execute(new Runnable() {
                                @Override
                                public void run() {
                                    if (!removeAllFences(callbackContext.getCallbackId())){
                                        callbackContext.error("Error while removing all geofences");
                                    }
                                }
                            });
                        }else if (Constants.ACTION_START.equalsIgnoreCase(action) && !isEnabled) {
                            Log.i(TAG, "start called - sending task to execute thread");
                            isEnabled = true;
                            final JSONObject cfg = data.getJSONObject(0);
                            Log.i(TAG,cfg.toString());
                            isBackgroundUpdatesRequired = cfg.getBoolean("background");
                            Log.i(TAG, "does background updates required : " + isBackgroundUpdatesRequired);
                            cordova.getThreadPool().execute(new Runnable() {
                                @Override
                                public void run() {
                                    updateLocationCallbackId = callbackContext.getCallbackId();
                                    Message request = Message.obtain();
                                    request.what = Constants.MSG_ACTION_START;
                                    request.replyTo = mMessenger;
                                    try {
                                        mService.send(request);
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });

                        } else if (Constants.ACTION_STOP.equalsIgnoreCase(action)) {
                            Log.i(TAG, "stop called - sending task to execute thread");
                            isEnabled = false;
                            cordova.getThreadPool().execute(new Runnable() {
                                @Override
                                public void run() {
                                    updateLocationCallbackId = null;
                                    Message request = Message.obtain();
                                    request.what = Constants.MSG_ACTION_STOP;
                                    request.replyTo = mMessenger;
                                    try {
                                        mService.send(request);
                                        callbackContext.success();
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } else {
                            Log.d(TAG, "Execution should not come here, check");
                        }


                result = true;
            }
        } catch (Exception ex){
            Log.d(TAG, "Cordova Execute Exception - " + ex.getMessage());
        }
        return result;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "received intent " + intent.getStringExtra("fences"));
        super.onNewIntent(intent);
    }
    @Override
    public void onDestroy(){
        Log.i(TAG, "onDestroy called");
        doUnbindService();
        // TODO: find out if it is really required to do this
        removeAllFences(null);
        cordova.getActivity().unregisterReceiver(geofencingReceiver);
        cordova.getActivity().unregisterReceiver(activityReceiver);
        executeStop();
        super.onDestroy();

    }

    private void executeInit(final CallbackContext callbackContext){
        Log.d(TAG, "Google Play services init");
        final Activity activity = this.cordova.getActivity();
       // updateServiceIntent.putExtra("userId",this.userId);
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                activity.startService(updateServiceIntent);
                callbackContext.success();
            }
        });
    }

    private void executeStop(){
        Log.d(TAG, "Stopping the geolocation service");
        Context context = this.cordova.getActivity().getApplicationContext();
        Intent serviceIntent = new Intent(this.cordova.getActivity(),GPStracker.class);
        context.stopService(serviceIntent);
    }

    private void sendLastLocation(){
        Log.i(TAG,"send last location to user");
        if (getLocationCallbackId != null) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    mLocation = LocationServices.FusedLocationApi.getLastLocation(mGApiClient);
                    Log.i(TAG, "Trying to send location to callbackId " + getLocationCallbackId + " with location at " + mLocation);
                    if (mLocation != null) {
                        PluginResult getLocationresult = new PluginResult(PluginResult.Status.OK,
                                Constants.returnLocationJSON(mLocation));
                        getLocationresult.setKeepCallback(false);
                        Log.i(TAG, "sending get location");
                        CallbackContext callbackContext = new CallbackContext(getLocationCallbackId, webView);
                        callbackContext.sendPluginResult(getLocationresult);
                        getLocationCallbackId = null;
                    }
                    Log.i(TAG, "location not accepted");

                }
            });
        }

    }
    /**
     * Handler of incoming messages from service.
     */
    private static Handler IncomingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Boolean keep;
            CallbackContext callbackContext;
            switch (msg.what) {
                case Constants.MSG_ACTION_ECHO:
                    String str = msg.getData().getString("message");
                    Log.i(TAG, "Received from service: " + str);
                    PluginResult echoResult = new PluginResult(PluginResult.Status.OK,str);
                    keep = false;
                    String callbackId = getCallback(msg.what,keep);
                    callbackContext = new CallbackContext(callbackId, webView);
                    echoResult.setKeepCallback(keep);
                    callbackContext.sendPluginResult(echoResult);
                    break;
                case Constants.MSG_POST_LOCATION:
                    try {
                        String locString = msg.getData().getString("location");
                        JSONObject loc = new JSONObject(locString);
                        PluginResult postLocationResult = new PluginResult(PluginResult.Status.OK,loc);
                        keep = true;
                        callbackContext = new CallbackContext(updateLocationCallbackId, webView);
                        postLocationResult.setKeepCallback(keep);
                        callbackContext.sendPluginResult(postLocationResult);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(IncomingHandler);
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        Constants.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                // As part of the sample, tell the user what happened.
                Log.i(TAG,"Service Connected");
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }


        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;

            // As part of the sample, tell the user what happened.
            Log.i(TAG,"Service Disconnected");
        }
    };

    void doBindService() {
        Context context = this.cordova.getActivity().getApplicationContext();
        Intent intent = new Intent(context,GPStracker.class);
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        Log.i(TAG, "bind Service "+intent.toString());
        context.startService(intent);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
    void doUnbindService() {
        if (mIsBound) {
            Context context = this.cordova.getActivity().getApplicationContext();

            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null,
                            Constants.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            context.unbindService(mConnection);
            mIsBound = false;
        }
    }
    private void addCallback(String callback, Integer code) {
        JSONArray cbList = new JSONArray();
        JSONObject temp = new JSONObject();
        if (callbackHashMap.containsKey(code)) {
            temp  = callbackHashMap.get(code);
            try {
                temp.getJSONArray("callbacks").put(callback);
                Log.i(TAG,"inserted callback into existing jsonarray");

            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else{
            cbList.put(callback);
            try {
                temp.put("callbacks",cbList);
                callbackHashMap.put(code,temp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private static String getCallback(Integer code, Boolean keep){
        if(callbackHashMap.containsKey(code)){
            JSONObject temp = new JSONObject();
            temp = callbackHashMap.get(code);
            try {
                String res = temp.getJSONArray("callbacks").optString(0);
                Log.i(TAG, "extracted callback is " + res);
                if(!keep){
                    JSONArray ja =  remove(0,temp.getJSONArray("callbacks"));
                    temp.put("callbacks",ja);
                }
                return res;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }else{
            return null;
        }
    }
    public void _checkLocationSettings() {
        //LocationRequest mLocationRequestBalancedPowerAccuracy = LocationRequest.create().setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (isLocationDialogShowing){
            return;
        }
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(mLocationRequestHighAccuracy);
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                final LocationSettingsStates states= locationSettingsResult.getLocationSettingsStates();

                Log.i(TAG,"location Status code " + status.getStatusCode());
                switch (status.getStatusCode()){
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        Log.i(TAG, "Location Services Enabled");
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        Log.i(TAG, "Location Services Resolution Required");
                        try {
                            isLocationDialogShowing = true;
                            cordova.setActivityResultCallback (GPStrackerHelper.this);
                            status.startResolutionForResult(cordova.getActivity(),ACTIVITY_LOCATION_DIALOG);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.i(TAG,"Location settings are not satisfied. However, we have no way to fix the settings so we won't show the dialog.");
                        break;
                }
            }
        });

    }


    private boolean isGPSdisabled() {
        boolean gps_enabled;
        LocationManager lm = (LocationManager) this.cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            ex.printStackTrace();
            gps_enabled = false;
        }
        return gps_enabled;
    }
    // function to remove an item from JSON array which is supported in KitKat+
    //https://gist.github.com/emmgfx/0f018b5acfa3fd72b3f6
    public static JSONArray remove(final int idx, final JSONArray from) {
        final List<String> objs = asList(from);
        objs.remove(idx);

        final JSONArray ja = new JSONArray();
        for (final String obj : objs) {
            ja.put(obj);
        }

        return ja;
    }

    public static List<String> asList(final JSONArray ja) {
        final int len = ja.length();
        Log.i(TAG, "length of list ja is " + len);
        final ArrayList<String> result = new ArrayList<String>(len);
        for (int i = 0; i < len; i++) {
            final String obj = ja.optString(i);
            if (obj != null) {
                result.add(obj);
            }
        }
        Log.i(TAG,"length of list ja is "+ result.size());
        return result;
    }

    private boolean servicesAvailable() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(cordova.getActivity());

        if (ConnectionResult.SUCCESS == resultCode) {
            return true;
        }
        else {
            GooglePlayServicesUtil.getErrorDialog(resultCode, cordova.getActivity(), 0).show();
            return false;
        }
    }

    // MARK: GoogleApiClient Connection callbacks
    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "GoogleApiClient ===> onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient ===> onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient ===> onConnectionFailed");
        mGApiClient.disconnect();
    }

    // MARK: Location Listener callbacks implementation
    @Override
    public void onLocationChanged(Location location) {
        if (mLocation == null || location.getAccuracy() <= mLocation.getAccuracy()){
            mLocation = location;
            removeLocationUpdates();
            sendLastLocation();

        }
    }
    private void requestLocationUpdates(){
        LocationServices.FusedLocationApi.requestLocationUpdates(mGApiClient, mLocationRequestHighAccuracy, GPStrackerHelper.this);
    }
    private void removeLocationUpdates(){
        LocationServices.FusedLocationApi.removeLocationUpdates(mGApiClient, this);
    }
    public  void onTransitionReceived(Bundle fenceBundle) {
        // Get the transition type.
        int geofenceTransition = fenceBundle.getInt("transition",-1);
        // Get the geofences that were triggered. A single event can trigger multiple geofences.
        ArrayList<String> triggeringGeofencesIdsList = fenceBundle.getStringArrayList("idList");
        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER){
            didEnterGeofence(triggeringGeofencesIdsList);
        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            didExitGeofence(triggeringGeofencesIdsList);
        }else{
            Log.i(TAG,"cannot read transition type from Bundle");
        }
    }
    private  void didEnterGeofence(ArrayList<String> triggeringGeofences){
        for (String geofenceId : triggeringGeofences) {
            Log.i(TAG,"Geofence Entered "+ geofenceId);
            int type =  getGeofenceType(geofenceId);
            switch (type){
                case Constants.SAFEHOUSE:
                    isInsideSafeHouse = true;
                    break;
                case Constants.PICKUP:
                    isInsidePickup = true;
                    break;
                case Constants.POI:
                    isInsidePoi = true;
                    break;
                default:
                    Log.i(TAG,"unknown type detected in Geofence Entry");
                    break;
            }
            String callbackId = getGeofenceCallbackWithId(type,geofenceId);
            JSONObject o = new JSONObject();
            try {
                o.put("transition","enter");
                o.put("Id",geofenceId);
                PluginResult addGeofenceResult = new PluginResult(PluginResult.Status.OK, o);
                addGeofenceResult.setKeepCallback(true);
                Log.i(TAG, "sending enter geofence");
                CallbackContext callbackContext = new CallbackContext(callbackId, webView);
                callbackContext.sendPluginResult(addGeofenceResult);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (isInsideSafeHouse){
            Log.i(TAG,"Inside a safehouse");
            if (isEnabled && isBackgroundUpdatesRequired){

            }
        }
        if(isInsidePickup){
            Log.i(TAG,"Inside a pickup location");
        }
        if(isInsidePoi){
            Log.i(TAG,"Inside a POI");
        }

    }
    private  void didExitGeofence(ArrayList<String> triggeringGeofences){
        for (String geofenceId : triggeringGeofences) {
            Log.i(TAG,"Geofence Exit "+ geofenceId);
            int type =  getGeofenceType(geofenceId);
            switch (type){
                case Constants.SAFEHOUSE:
                    isInsideSafeHouse = false;
                    break;
                case Constants.PICKUP:
                    isInsidePickup = false;
                    break;
                case Constants.POI:
                    isInsidePoi = false;
                    break;
                default:
                    Log.i(TAG,"unknown type detected in Geofence Exit");
                    break;
            }
            String callbackId = getGeofenceCallbackWithId(type,geofenceId);
            JSONObject o = new JSONObject();
            try {
                o.put("transition","exit");
                o.put("Id",geofenceId);
                PluginResult addGeofenceResult = new PluginResult(PluginResult.Status.OK, o);
                addGeofenceResult.setKeepCallback(true);
                Log.i(TAG, "sending enter geofence");
                CallbackContext callbackContext = new CallbackContext(callbackId, webView);
                callbackContext.sendPluginResult(addGeofenceResult);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (!isInsideSafeHouse){
            Log.i(TAG,"Outside a safehouse");
        }
        if(!isInsidePickup){
            Log.i(TAG,"Outside a pickup location");
        }
        if(!isInsidePoi){
            Log.i(TAG,"Outside a POI");
        }
    }
    private String getGeofenceCallbackWithId(int type,String id){
        String res = null;
        if(type == Constants.SAFEHOUSE){
            res = safeHouses.get(id);
        } else if(type == Constants.PICKUP){
            res = pickup.get(id);
        } else if(type == Constants.POI){
            res = poi.get(id);
        }
        return res;
    }
    private int getGeofenceType(String id){
        int res = 0;
        if (safeHouses.containsKey(id)){
            res = Constants.SAFEHOUSE;
        }else if(pickup.containsKey(id)){
            res = Constants.PICKUP;
        }else if(poi.containsKey(id)){
            res = Constants.POI;
        }
        return res;
    }
    // TODO: Before removal check if the user is in geofence
    private boolean removeGeofenceWithId(String id){
        String res;
        if (safeHouses.containsKey(id)){
            res = safeHouses.get(id);
            safeHouses.remove(id);
        } else if (pickup.containsKey(id)){
            res = pickup.get(id);
            pickup.remove(id);
        }else if (poi.containsKey(id)){
            res = poi.get(id);
            poi.remove(id);
        } else {
            return false;
        }
        JSONObject o = new JSONObject();
        try {
            o.put("transition","removed");
            o.put("Id",id);
            PluginResult addGeofenceResult = new PluginResult(PluginResult.Status.OK, o);
            addGeofenceResult.setKeepCallback(false);
            Log.i(TAG, "sending enter geofence with status removed");
            CallbackContext callbackContext = new CallbackContext(res, webView);
            callbackContext.sendPluginResult(addGeofenceResult);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        removeGeofence(id);
        return true;
    }
    /**
     * Method to build Geofence object based on location
     */
    public Geofence buildGeofenceObject(String id, double lat, double lng, float radius){
        //float f1 = (float) d1
        return (new Geofence.Builder()
                // set the request ID for the geofence. This is a string to identify this geofence
                .setRequestId(id)
                        // set loitering delay for dwell
                .setLoiteringDelay(Constants.GEOFENCE_LOITERING_DELAY)
                        // set the time delay for triggering notification
                .setNotificationResponsiveness(Constants.GEOFENCE_RESPONSE)
                        // set the circular region of this geofence
                .setCircularRegion(lat, lng, radius)
                        // set the expiration duration of the geofence. This geofence gets automatically removed after this period of time
                .setExpirationDuration(Constants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                        // set the transition types of interest. Alerts are only generated for these transitions.
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                        // Create the geofence
                .build());
    }
    /**
     * Builds and returns a GeofencingRequest. Specifies  geofence to be monitored. ALso specifies how the geofence notifications are initially triggered
     */
    private GeofencingRequest getGeofencingRequest(Geofence geofence){
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        // The INITIAL_TRIGGER_ENTER flag indicates that geofencing service should trigger a
        // GEOFENCE_TRANSITION_ENTER notification when the geofence is added and if the device
        // is already inside that geofence.
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        // Add the geofences to be monitored by geofencing service.
        builder.addGeofence(geofence);
        // Return a GeofencingRequest.
        return builder.build();
    }
    /**
     * Gets a PendingIntent to send with the request to add or remove Geofences. Location Services
     * issues the Intent inside this PendingIntent whenever a geofence transition occurs for the
     * current list of geofences.
     *
     * @return A PendingIntent for the IntentService that handles geofence transitions.
     */
    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(cordova.getActivity(), GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        return PendingIntent.getService(cordova.getActivity(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    public boolean addGeofence(JSONObject cfg, CallbackContext callbackContext){
        try{
            String type = cfg.getString("type");
            Double lat = cfg.getDouble("lat");
            Double lng = cfg.getDouble("lng");
            Float radius = (float) cfg.getDouble("radius");
            String callbackId = callbackContext.getCallbackId();
            String id = cfg.getString("id");
            Log.i(TAG, "Add circle with id: " + id + "and lat: " + lat + ", " + lng + " and with radius: " + radius);
            LocationServices.GeofencingApi.addGeofences(mGApiClient,
                    getGeofencingRequest(buildGeofenceObject(id, lat, lng,radius)),
                    getGeofencePendingIntent()).setResultCallback(new fenceResults());
            if (type.equalsIgnoreCase("safeHouse")){
                safeHouses.put(id,callbackId);
            } else if (type.equalsIgnoreCase("pickup")){
                pickup.put(id,callbackId);
            } else{
                poi.put(id,callbackId);
            }
            return true;
        }catch (SecurityException securityException){
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
    public void removeGeofence(String id){
        List<String> toDeleteList = Collections.singletonList(id);
        try{
            if(toDeleteList.size() > 0){
                LocationServices.GeofencingApi.removeGeofences(mGApiClient,toDeleteList);
            }
        }catch (SecurityException securityException){
            Log.e(TAG, "got security Exception in removeGeofence - ACCESS_FINE_LOCATION permission");
        }
    }
    public boolean removeAllFences(final String callbackId){
        safeHouses.clear();
        pickup.clear();
        poi.clear();

        try{

            LocationServices.GeofencingApi.removeGeofences(mGApiClient,getGeofencePendingIntent()).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(Status status) {
                    Log.i(TAG,"removed all geofences");
                    if (callbackId != null){
                        PluginResult removeAllGeofenceResult = new PluginResult(PluginResult.Status.OK);
                        removeAllGeofenceResult.setKeepCallback(false);
                        Log.i(TAG, "sending enter geofence with status removed");
                        CallbackContext callbackContext = new CallbackContext(callbackId, webView);
                        callbackContext.sendPluginResult(removeAllGeofenceResult);
                    }
                }
            });
            return true;
        }catch (SecurityException securityException){
            Log.e(TAG,"got security Exception in removeAllGeofences - ACCESS_FINE_LOCATION permission");
        }
        return false;
    }



    private class fenceResults implements ResultCallback<Status> {
        private final String TAG = fenceResults.class.getSimpleName();
        @Override
        public void onResult(Status status) {
            Log.i(TAG, "Geofence status "+ status.getStatus());
            if (status.isSuccess()) {
                Toast.makeText(
                        cordova.getActivity(),
                        "Geofences Added",
                        Toast.LENGTH_SHORT
                ).show();
            }else{
                // Get the status code for the error and log it using a user-friendly message.
                String errorMessage = Constants.getGeofenceErrorString(cordova.getActivity(),
                        status.getStatusCode());
                Log.e(TAG, errorMessage);
            }


        }
    }
}