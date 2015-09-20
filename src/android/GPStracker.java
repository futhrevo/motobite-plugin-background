package com.reku.motobite.cordova;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * Created by rakeshkalyankar on 28/05/15.
 */
public class GPStracker extends Service implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener, ResultCallback<Status> {
    //TODO: Implement location listener provider method to know the status of gps provider
    private static final String TAG = GPStracker.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private NotificationManager notifyManager;
    private Location mCurrentLocation;
    private String user = "tester1";

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
    /**
     * A receiver for DetectedActivity objects broadcast by the
     * {@code ActivityDetectionIntentService}.
     */
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;
    /**
     * Used when requesting or removing activity detection updates.
     */
    private PendingIntent mActivityDetectionPendingIntent;

    public static final int MSG_ACTION_START = 1;
    public static final int MSG_ACTION_STOP = 2;
    public static final int MSG_ACTION_CONFIGURE = 3;
    public static final int MSG_ACTION_ECHO  = 4;
    public static final int MSG_ACTION_GETLOCATION = 5;
    public static final int MSG_ACTION_ADDGEOFENCE = 6;
    public static final int MSG_ACTION_REMOVEGEOFENCE  = 7;
    public static final int MSG_ACTION_REMOVEALLGEOFENCES = 8;
    public static final int MSG_ACTION_NOTIFYABOUT = 9;
    /**
    These settings are the same as the settings for the map. They will in fact give you updates
    at the maximal rates currently possible.
    */
    private static final LocationRequest REQUESTHIGH = LocationRequest.create()
            .setInterval(Constants.UPDATE_INTERVAL_IN_MILLISECONDS)
            .setFastestInterval(Constants.FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    private static final LocationRequest REQUESTLOW = LocationRequest.create()
            .setInterval(Constants.UPDATE_INTERVAL_IN_MILLISECONDS)
            .setFastestInterval(Constants.FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
            .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    private LocationSettingsRequest.Builder builder;

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onCreate(){
        super.onCreate();
        // Empty list for storing geofences.
        mGeofenceList = new ArrayList<Geofence>();
        // Initially set the PendingIntent used in addGeofences() and removeGeofences() to null
        mGeofencePendingIntent = null;
        // Retrieve an instance of SharedPreferences object
        getSharedPreferencesInstance();
        // Get the value of mGeofencesAdded
        mGeofencesAdded = getSharedPreferencesInstance().getBoolean(Constants.GEOFENCES_ADDED_KEY,false);
        // Get an instance of the Notification Manager
        if(notifyManager == null){
            notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        Log.i(TAG, "OnCreate");
        new ConnectThread().start();

        // Get a receiver for broadcasts from ActivityDetectionIntentService.
        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // Register the broadcast receiver that informs this activity of the DetectedActivity
        // object broadcast sent by the intent service.
        try {
            user = intent.getStringExtra("userId");
            Log.i(TAG, "- user: " + user);
        } catch (Exception ex){

        }
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                new IntentFilter(Constants.BROADCAST_ACTION));
        return START_STICKY;
    }


    @Override
    public boolean stopService(Intent intent) {
        return super.stopService(intent);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "onConnected");
        /**
         * Notifying user to turn on location services, continue otherwise
         */
        builder = new LocationSettingsRequest.Builder().addLocationRequest(REQUESTHIGH);
        builder.setAlwaysShow(true);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {

            @Override
            public void onResult(LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                final LocationSettingsStates locationSettingsStates = locationSettingsResult.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location requests here.
                        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                        String msg = "Last Location = " + mCurrentLocation;
                        Log.i(TAG, msg);
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. If getActivity is available it could be fixed by showing the user a dialog
                        showNotification();
                        break;
                }
            }
        });

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, REQUESTHIGH, this);  // LocationListener
        requestActivityUpdates();

        try{
            LocationServices.GeofencingApi.addGeofences(mGoogleApiClient,
                    getGeofencingRequest(buildGeofenceObject("Home", 14.6750928, 77.5920952)),
                    getGeofencePendingIntent()).setResultCallback(new fenceResults());
            LocationServices.GeofencingApi.addGeofences(mGoogleApiClient,
                    getGeofencingRequest(buildGeofenceObject("ATP", 14.6750928, 77.5920952)),
                    getGeofencePendingIntent()).setResultCallback(new fenceResults());
        }catch (SecurityException securityException){
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
        }

    }


    @Override
    public void onConnectionSuspended(int i) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        String msg = "Location = "+ location;
        Log.i(TAG, msg);
        doAction(location);
    }

    @Override
    public void onDestroy(){
        //clearAllNotify();
        removeActivityUpdates();
        removeAllFences();
        stopLocationUpdates();
        // Unregister the broadcast receiver that was registered during onResume().
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (this.notifyManager != null){
                //remove all notifications
                this.notifyManager.cancelAll();
                Log.i(TAG,"removed all notifications");
        }

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        Log.w(TAG, "------------------------------------------ Destroyed GPStracker Service");
        super.onDestroy();
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // Set the Intent action to open Location Settings
        Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        // Create a PendingIntent to start an Activity
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 2, gpsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        // Create a notification builder that's compatible with platforms >= version 4
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext());

        // Set the title, text, and icon
        builder.setContentTitle("MotoBite")
                .setContentText("Click to turn on GPS, or swipe to ignore")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setAutoCancel(true)
                        // Get the Intent that starts the Location settings panel
                .setContentIntent(pendingIntent);

        // Build the notification and post it
        notifyManager.notify(0, builder.build());
    }
    private void doAction(Location location){
        String geohash = GeoHashUtils.encode(location.getLatitude(), location.getLongitude());
        HashMap values = new HashMap();
        values.put("gh", geohash);
        values.put("heading", null);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    /**
     * Registers for activity recognition updates using
     * {@link com.reku.motobite.cordova.GPStracker#requestActivityUpdates} which
     * returns a {@link com.google.android.gms.common.api.PendingResult}. Since this activity
     * implements the PendingResult interface, the activity itself receives the callback, and the
     * code within {@code onResult} executes. Note: once {@code requestActivityUpdates()} completes
     * successfully, the {@code DetectedActivitiesIntentService} starts receiving callbacks when
     * activities are detected.
     */
    public void requestActivityUpdates(){
        if (!mGoogleApiClient.isConnected()) {
            Log.i(TAG, "API client needs to be connected before asking for activity updates");
            return;
        }
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient,
                Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    /**
     * Removes activity recognition updates using
     * {@link com.reku.motobite.cordova.GPStracker#removeActivityUpdates} which
     * returns a {@link com.google.android.gms.common.api.PendingResult}. Since this activity
     * implements the PendingResult interface, the activity itself receives the callback, and the
     * code within {@code onResult} executes. Note: once {@code removeActivityUpdates()} completes
     * successfully, the {@code DetectedActivitiesIntentService} stops receiving callbacks about
     * detected activities.
     */
    public void removeActivityUpdates(){
        if (!mGoogleApiClient.isConnected()) {
            Log.i(TAG, "API client needs to be connected before removing activity updates");
            return;
        }

        // Remove all activity updates for the PendingIntent that was used to request activity
        // updates.
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                mGoogleApiClient,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    /**
     * Gets a PendingIntent to be sent for each activity detection.
     */
    private PendingIntent getActivityDetectionPendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mActivityDetectionPendingIntent != null) {
            return mActivityDetectionPendingIntent;
        }
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Runs when the result of calling requestActivityUpdates() and removeActivityUpdates() becomes
     * available. Either method can complete successfully or with an error.
     *
     * @param status The Status returned through a PendingIntent when requestActivityUpdates()
     *               or removeActivityUpdates() are called.
     */
    @Override
    public void onResult(Status status) {
        if (status.isSuccess()) {
            // Toggle the status of activity updates requested, and save in shared preferences.
            boolean requestingUpdates = !getUpdatesRequestedState();
            setUpdatesRequestedState(requestingUpdates);
            Log.i(TAG, requestingUpdates ? "Activity updates added" : "Activity updates removed");
        }else{
            Log.e(TAG, "Error adding or removing activity detection: " + status.getStatusMessage());
            // Get the status code for the error and log it using a user-friendly message.
            String errorMessage = Constants.getGeofenceErrorString(this,
                    status.getStatusCode());
            Log.e(TAG, errorMessage);
        }
    }

    /**
     * Retrieves the boolean from SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private boolean getUpdatesRequestedState() {
        return getSharedPreferencesInstance()
                .getBoolean(Constants.ACTIVITY_UPDATES_REQUESTED_KEY, false);
    }

    /**
     * Sets the boolean in SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private void setUpdatesRequestedState(boolean requestingUpdates) {
        getSharedPreferencesInstance()
                .edit()
                .putBoolean(Constants.ACTIVITY_UPDATES_REQUESTED_KEY, requestingUpdates)
                .commit();
    }

    /**
     * Retrieves a SharedPreference object used to store or read values in this app. If a
     * preferences file passed as the first argument to {@link #getSharedPreferences}
     * does not exist, it is created when {@link SharedPreferences.Editor} is used to commit
     * data.
     */
    private SharedPreferences getSharedPreferencesInstance() {
        return getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, MODE_PRIVATE);
    }


    private void updateDetectedActivitiesList(ArrayList<DetectedActivity> updatedActivities) {
        for (DetectedActivity member : updatedActivities){
            Log.i(TAG,String.valueOf(member));
        }
    }

    /**
     * Receiver for intents sent by DetectedActivitiesIntentService via a sendBroadcast().
     * Receives a list of one or more DetectedActivity objects associated with the current state of
     * the device.
     */
    private class ActivityDetectionBroadcastReceiver extends BroadcastReceiver{
        protected static final String TAG = "BroadcastResponseRecvr";
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG,"Broadcast received");
            ArrayList<DetectedActivity> updatedActivities =
                    intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);
            DetectedActivity mostProable = intent.getParcelableExtra(Constants.ACTIVITY_PROBABLE);
            updateDetectedActivitiesList(updatedActivities);
            updateLocationMode(mostProable);
        }


    }

    private void updateLocationMode(DetectedActivity proable) {
        Log.i(TAG, String.valueOf(proable));
        if(proable.getConfidence() > 60){
            switch (proable.getType()){
                case DetectedActivity.STILL:
                    LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, REQUESTLOW, this);  // LocationListener
                    break;
                default:
                    LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, REQUESTHIGH, this);  // LocationListener
                    break;
            }
        }

    }
    /**
     * Method to build Geofence object based on location
     */
    public Geofence buildGeofenceObject(String id, double lat, double lng ){
        return (new Geofence.Builder()
                // set the request ID for the geofence. This is a string to identify this geofence
                .setRequestId(id)
                // set loitering delay for dwell
                .setLoiteringDelay(Constants.GEOFENCE_LOITERING_DELAY)
                // set the time delay for triggering notification
                .setNotificationResponsiveness(Constants.GEOFENCE_RESPONSE)
                // set the circular region of this geofence
                .setCircularRegion(lat, lng, Constants.GEOFENCE_RADIUS_IN_METERS)
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
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        return PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    public void removeAllFences(){
        try{
            LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient,getGeofencePendingIntent()).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(Status status) {
                    Log.i(TAG,"removed all geofences");
                }
            });
        }catch (SecurityException securityException){

        }

    }

    /**
     * GoogleApiClient thread for connection to a GoogleApiClient#blockingConnect
     * moves into a separate thread because UI can not be blocked .
     */
    private class ConnectThread extends Thread{
        protected synchronized void buildGoogleApiClient(){
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addConnectionCallbacks(GPStracker.this)
                    .addOnConnectionFailedListener(GPStracker.this)
                    .addApi(LocationServices.API)
                    .addApi(ActivityRecognition.API)
                    .build();
        }
        @SuppressLint("LongLogTag")
        @Override
        public void run() {
            buildGoogleApiClient();
            //We want this service to continue running until it is explicitly stopped
            mGoogleApiClient.blockingConnect();
            if(mGoogleApiClient.isConnected()){
                Log.d("GoogleApiClientConnectService","client is Connected");
            }else{
                Log.d("GoogleApiClientConnectService","client is not Connected!!");
            }

        }
    }

    private class fenceResults implements ResultCallback<Status> {
        private final String TAG = fenceResults.class.getSimpleName();
        @Override
        public void onResult(Status status) {
            Log.i(TAG, "Geofence status");
            if (status.isSuccess()) {
                Toast.makeText(
                        GPStracker.this,
                        "Geofences Added",
                        Toast.LENGTH_SHORT
                ).show();
            }else{
                // Get the status code for the error and log it using a user-friendly message.
                String errorMessage = Constants.getGeofenceErrorString(GPStracker.this,
                        status.getStatusCode());
                Log.e(TAG, errorMessage);
            }


        }
    }

    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    /** Holds last value set by a client. */
    int mValue = 0;
    /**
     * Command to the service to register a client, receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    public static final int MSG_REGISTER_CLIENT = 1;
    /**
     * Command to the service to unregister a client, ot stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 2;

    /**
     * Command to service to set a new value.  This can be sent to the
     * service to supply a new value, and will be sent by the service to
     * any registered clients with the new value.
     */
    public static final int MSG_SET_VALUE = 3;

    /**
     * Handler of incoming messages from clients.
     */
    private class IncomingHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ACTION_ECHO:
                    Bundle mBundle = msg.getData();
                    Log.i(TAG, "First Echo : " + mBundle.getString("message"));
                    Messenger temp = msg.replyTo;
                    Message resp = Message.obtain(null,MSG_ACTION_ECHO);
                    resp.setData(mBundle);
                    try {
                        temp.send(resp);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_SET_VALUE:
                    mValue = msg.arg1;
                    for (int i=mClients.size()-1; i>=0; i--) {
                        try {
                            mClients.get(i).send(Message.obtain(null,
                                    MSG_SET_VALUE, mValue, 0));
                        } catch (RemoteException e) {
                            // The client is dead.  Remove it from the list;
                            // we are going through the list from back to front
                            // so this is safe to do inside the loop.
                            mClients.remove(i);
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }

        }
    }
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());
}

