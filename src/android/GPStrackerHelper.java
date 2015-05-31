package com.reku.motobite.cordova;


import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

public class GPStrackerHelper extends CordovaPlugin{
    private static final String TAG = "GPStrackerHelper";

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    private Intent updateServiceIntent;
    private boolean isEnabled = false;
    private String stopOnTerminate = "false";

    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException{
        Activity activity = this.cordova.getActivity();
        Boolean result = false;
        updateServiceIntent = new Intent(activity, GPStracker.class);

        if (ACTION_START.equalsIgnoreCase(action) && !isEnabled){
            result = true;
            Log.i(TAG, "start called - sending task to execute thread");
            executeInit(callbackContext);
            isEnabled = true;
        } else if(ACTION_STOP.equalsIgnoreCase(action)){
            Log.i(TAG,"stop called - sending task to execute thread");
            isEnabled = false;
            result = true;
            executeStop(callbackContext);
        }


        return result;
    }

    public void onDestroy(){
        Activity activity = this.cordova.getActivity();
        if(isEnabled && stopOnTerminate.equalsIgnoreCase("true")) {
            activity.stopService(updateServiceIntent);
        }
    }

    private void executeInit(final CallbackContext callbackContext){
        Log.d(TAG, "Google Play services init");
        final Activity activity = this.cordova.getActivity();
        final Intent intent = new Intent(activity, GPStracker.class);
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                activity.startService(intent);
                callbackContext.success();
            }
        });
    }

    private void executeStop(final CallbackContext callbackContext){
        Log.d(TAG, "Google Play services getting stopped");
        final Activity activity = this.cordova.getActivity();
        final Intent intent = new Intent(activity, GPStracker.class);
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.stopService(intent);
                callbackContext.success();
            }
        });
    }
}