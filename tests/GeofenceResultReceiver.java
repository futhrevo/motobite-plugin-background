package com.reku.motobite.cordova;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

/**
 * Created by reku on 24/9/15.
 */
public class GeofenceResultReceiver extends ResultReceiver {
    private Receiver mReceiver;
    /**
     * Create a new ResultReceive to receive results.  Your
     * {@link #onReceiveResult} method will be called from the thread running
     * <var>handler</var> if given, or from an arbitrary thread if null.
     *
     * @param handler
     */
    public GeofenceResultReceiver(Handler handler) {
        super(handler);
    }
    public interface Receiver {
        void onReceiveResult(int resultCode, Bundle resultData);
    }
    public void setReceiver(Receiver receiver) {
        mReceiver = receiver;
    }
    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {

        if (mReceiver != null) {
            mReceiver.onReceiveResult(resultCode, resultData);
        }
    }
}
