package com.reku.motobite.cordova;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;

/**
 * Created by rakeshkalyankar on 29/05/15.
 * IntentService for handling incoming intents that are generated as a result of requesting
 *  activity updates using
 *  {@link com.reku.motobite.cordova.GPStracker#requestActivityUpdates}.
 */
public class DetectedActivitiesIntentService extends IntentService{
    protected static final String TAG = DetectedActivitiesIntentService.class.getSimpleName();

    /**
     * This constructor is required, and calls the super IntentService(String)
     * constructor with the name for a worker thread.
     */
    public DetectedActivitiesIntentService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
    /**
     * Handles incoming intents.
     * @param intent The Intent is provided (inside a PendingIntent) when requestActivityUpdates()
     *               is called.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        // If the intent contains an update
        if (ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            Intent localIntent = new Intent(Constants.BROADCAST_ACTION);

            DetectedActivity mostProbableActivity = result.getMostProbableActivity();
            Log.i(TAG, "Most probably " + Constants.getActivityString(getApplicationContext(), mostProbableActivity.getType()) + " " + mostProbableActivity.getConfidence() + "%");
            // Get the list of the probable activities associated with the current state of the
            // device. Each activity is associated with a confidence level, which is an int between
            // 0 and 100.
            ArrayList<DetectedActivity> detectedActivities = (ArrayList) result.getProbableActivities();

            // Log each activity.
            Log.i(TAG, "activities detected");
            for (DetectedActivity da : detectedActivities) {
                Log.i(TAG, Constants.getActivityString(
                                getApplicationContext(),
                                da.getType()) + " " + da.getConfidence() + "%"
                );
            }

            // Broadcast the list of detected activities.
            localIntent.putExtra(Constants.ACTIVITY_EXTRA, detectedActivities);
            localIntent.putExtra(Constants.ACTIVITY_PROBABLE, mostProbableActivity);
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
    }
}
