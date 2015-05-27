
package cc.fotoplace.camera;

import android.util.Log;

public class CameraErrorCallback
        implements android.hardware.Camera.ErrorCallback {
    private static final String TAG = "CameraErrorCallback";

    @Override
    public void onError(int error, android.hardware.Camera camera) {
        Log.e(TAG, "Got camera error callback. error=" + error);
        if (error == android.hardware.Camera.CAMERA_ERROR_SERVER_DIED) {
            // We are not sure about the current state of the app (in preview or
            // snapshot or recording). Closing the app is better than creating a
            // new Camera object.
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
