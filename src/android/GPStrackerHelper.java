package com.reku.motobite.cordova;


import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;

public class GPStrackerHelper extends CordovaPlugin{
    private static final String TAG = GPStrackerHelper.class.getSimpleName();

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";
    public static final String ACTION_ECHO  = "echo";
    public static final String ACTION_GETLOCATION = "getLocation";
    public static final String ACTION_ADDGEOFENCE = "addGeofence";
    public static final String ACTION_REMOVEGEOFENCE  = "removeGeofence";
    public static final String ACTION_REMOVEALLGEOFENCES = "removeallGeofences";
    public static final String ACTION_NOTIFYABOUT = "notifyAbout";


    private String userId;
    private Intent updateServiceIntent;
    private boolean isEnabled = false;
    private String stopOnTerminate = "false";
    private HashMap<Integer,ArrayList<CallbackContext>> callbackHashMap = new HashMap<Integer,ArrayList<CallbackContext>>();

    /** Messenger for communicating with service. */
    Messenger mService = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.i(TAG, "Plugin Init");
        doBindService();
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        Log.i(TAG, "TODO: handle onPause Called, handle the functions");
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        Log.i(TAG, "TODO: handle onResume");
    }

    @Override
    public void onReset() {
        super.onReset();
        Log.i(TAG,"TODO: onReset called, implement how to handle it");
    }
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException{
        Boolean result = false;
        try{
            if(mIsBound){
                if (ACTION_CONFIGURE.equalsIgnoreCase(action)){
                    result = true;
                    Log.i(TAG, "Configure called");

                } else if (ACTION_ECHO.equalsIgnoreCase(action)){
                    result = true;
                    String message = data.getString(0);
                    Message request = Message.obtain();
                    request.what = GPStracker.MSG_ACTION_ECHO;
                    Bundle mBundle = new Bundle();
                    mBundle.putString("message", message);
                    request.setData(mBundle);
                    request.replyTo = mMessenger;
                    try {
                        addCallback(callbackContext,GPStracker.MSG_ACTION_ECHO);
                        mService.send(request);
                    } catch (RemoteException e) {
                        callbackContext.error("Could not reach service : " + e.getMessage());
                    }

                    Log.i(TAG,message);
                } else if (ACTION_START.equalsIgnoreCase(action) && !isEnabled){
                    result = true;
                    Log.i(TAG, "start called - sending task to execute thread");
                    executeInit(callbackContext);
                    isEnabled = true;
                } else if(ACTION_STOP.equalsIgnoreCase(action)){
                    Log.i(TAG,"stop called - sending task to execute thread");
                    isEnabled = false;
                    result = true;
                    executeStop(callbackContext);
                } else if(ACTION_CONFIGURE.equalsIgnoreCase(action)){
                    result =true;
                    try {
                        this.userId = data.getString(0);
                    } catch (JSONException e){
                        callbackContext.error("authToken/url required as parameters: " + e.getMessage());
                    }
                }
            }else{
                Log.d(TAG,"Execution should not come here, check");
            }
        } catch (Exception ex){
            Log.d(TAG, "Exception - " + ex.getMessage());
        }
        return result;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

    }

    public void onDestroy(){
        doUnbindService();
    }

    private void executeInit(final CallbackContext callbackContext){
        Log.d(TAG, "Google Play services init");
        final Activity activity = this.cordova.getActivity();
        updateServiceIntent.putExtra("userId",this.userId);
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                activity.startService(updateServiceIntent);
                callbackContext.success();
            }
        });
    }

    private void executeStop(final CallbackContext callbackContext){
        Log.d(TAG, "Google Play services getting stopped");
        final Activity activity = this.cordova.getActivity();
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.stopService(updateServiceIntent);
                callbackContext.success();
            }
        });
    }
    /**
     * Handler of incoming messages from service.
     */
    private Handler IncomingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GPStracker.MSG_ACTION_ECHO:
                    String str = msg.getData().getString("message");
                    Log.i(TAG, "Received from service: " + str);
                    PluginResult result = new PluginResult(PluginResult.Status.OK,str);
                    Boolean keep = false;
                    CallbackContext callback = getCallback(msg.what,keep);
                    result.setKeepCallback(keep);
                    callback.sendPluginResult(result);
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
                        GPStracker.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);

                // Give it some value as an example.
                msg = Message.obtain(null,
                        GPStracker.MSG_SET_VALUE, this.hashCode(), 0);
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
                            GPStracker.MSG_UNREGISTER_CLIENT);
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
    private void addCallback(CallbackContext callback, Integer code) {
            ArrayList<CallbackContext> temp = new ArrayList<CallbackContext>();
        if (callbackHashMap.containsKey(code)) {
            temp  = callbackHashMap.get(code);
            temp.add(callback);
        } else{
            temp.add(callback);
        }
        callbackHashMap.put(code,temp);
    }

    private CallbackContext getCallback(Integer code, Boolean keep){
        if(callbackHashMap.containsKey(code)){
            ArrayList<CallbackContext> temp = callbackHashMap.get(code);
            if (temp.isEmpty()){
                return  null;
            } else{
                CallbackContext res = temp.get(0);
                if (!keep){
                    temp.remove(0);
                }
                callbackHashMap.put(code,temp);
                return res;
            }
        }else{
            return null;
        }
    }
}