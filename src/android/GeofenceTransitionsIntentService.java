package com.reku.motobite.cordova;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.futrevo.reku.motobite.MainActivity;


import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Admin on 6/28/2015.
 * Listener for geofence transition changes.
 *
 * Receives geofence transition events from Location Services in the form of an Intent containing
 * the transition type and geofence id(s) that triggered the transition. Creates a notification
 * as the output.
 */

public class GeofenceTransitionsIntentService extends IntentService{
    protected static final String TAG = GeofenceTransitionsIntentService.class.getSimpleName();
    private NotificationManager notifyManager;
    /**
     * This constructor is required, and calls the super IntentService(String)
     * constructor with the name for a worker thread.
     */
    public GeofenceTransitionsIntentService() {
        // Use the TAG to name the worker thread.
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        Intent i = new Intent(Constants.BROADCAST_GEOFENCE_RESULT);
        Log.i(TAG,intent.toString());
        if(geofencingEvent.hasError()){
            // if user turns off GPS when monitoring geofences all the geofences are removed, need to track this
            String errorMessage = Constants.getGeofenceErrorString(geofencingEvent.getErrorCode());
            Log.e(TAG, errorMessage);
            i.putExtra("GeofenceError", geofencingEvent.getErrorCode());
            sendBroadcast(i);
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
        // Get the geofences that were triggered. A single event can trigger multiple geofences.
        List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
        ArrayList<String> triggeringGeofencesIdsList = getGeofenceIdList(triggeringGeofences);
        try {
           Bundle data = new Bundle();
            data.putInt("transition", geofenceTransition);
            data.putStringArrayList("idList", triggeringGeofencesIdsList);
            i.putExtra("geoBundle",data);
            sendBroadcast(i);
        }catch (Exception e){
            e.printStackTrace();
        }




            // Get the transition details as a String.
            String geofenceTransitionDetails = getGeofenceTransitionDetails(
                    this,
                    geofenceTransition,
                    triggeringGeofencesIdsList
            );
            JSONArray jsonarrayFencesList = new JSONArray(triggeringGeofencesIdsList);

            // Send notification and log the transition details.
            sendNotificationIntent(geofenceTransitionDetails, jsonarrayFencesList.toString());
            Log.i(TAG, geofenceTransitionDetails);
        }else{
            // Log the error.
            Log.e(TAG, "Geofence transition error: invalid transition type %1$d" + geofenceTransition);
        }
    }

    private void sendNotificationIntent(String geofenceTransitionDetails, String jsonarrayFencesListString) {
        Log.i(TAG,"jsonarrayFencesListString"+ jsonarrayFencesListString);
        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resultIntent.putExtra("fences",jsonarrayFencesListString);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent pendingCloseIntent = PendingIntent.getActivity(this, 0,  new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .setAction(Constants.CLOSE_ACTION),
                0);
        // define sound URI, the sound to be played when there's a notification
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        // Create a notification builder that's compatible with platforms >= version 4
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("MotoBite")
                .setContentText(geofenceTransitionDetails)
                .setSmallIcon(android.R.drawable.star_big_on)
                .setAutoCancel(true)
                .setSound(soundUri)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close ", pendingCloseIntent)
                .setContentIntent(resultPendingIntent)
//                .setGroup(Constants.GROUP_GEOFENCE_NOTIFICATIONS)
                .build();

        // Issue the notification
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);
        notificationManager.notify(0, notification);
        setNotifyIdCountInc();
    }

    // Method to get GeofenceIds as ArrayList
    private ArrayList getGeofenceIdList(List<Geofence> triggeringGeofences){
        ArrayList triggeringGeofencesIdsList = new ArrayList();
        for (Geofence geofence : triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.getRequestId());
        }
        return triggeringGeofencesIdsList;
    }

    /**
     * Gets transition details and returns them as a formatted string.
     *
     * @param context               The app context.
     * @param geofenceTransition    The ID of the geofence transition.
     * @param triggeringGeofencesIdsList   The geofence(s) triggered.
     * @return                      The transition details formatted as String.
     */
    private String getGeofenceTransitionDetails(
            Context context,
            int geofenceTransition,
            ArrayList triggeringGeofencesIdsList) {

        String geofenceTransitionString = getTransitionString(geofenceTransition);

        // Get the Ids of each geofence that was triggered.

        String triggeringGeofencesIdsString = TextUtils.join(", ", triggeringGeofencesIdsList);

        return geofenceTransitionString + ": " + triggeringGeofencesIdsString;
    }


    /**
     * Maps geofence transition types to their human-readable equivalents.
     *
     * @param transitionType    A transition type constant defined in Geofence
     * @return                  A String indicating the type of transition
     */
    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return "Geofence Entered";
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return "Geofence Exited";
            default:
                return "Unknown Transition";
        }
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

    /**
     * Retrieves the boolean from SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private int getNotifyIdCount() {
        return getSharedPreferencesInstance()
                .getInt(Constants.SHARED_PREFERENCES_NAME, 1);
    }

    /**
     * Sets the boolean in SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private void setNotifyIdCountInc() {
        getSharedPreferencesInstance()
                .edit()
                .putInt(Constants.SHARED_PREFERENCES_NAME, (1 + getNotifyIdCount()) % Integer.MAX_VALUE)
                .commit();
    }
}
