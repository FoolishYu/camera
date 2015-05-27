package cc.fotoplace.camera.platform;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cc.fotoplace.camera.VideoModule;

import android.hardware.Camera.Parameters;
import android.media.MediaRecorder;

public final class MtkCommon {

    private static final String TAG = "MtkCommon";
    private static MtkCommon sInstance = null;
    private static Method method_setOnCameraReleasedListener = null;

    private boolean mIsZSD = false;
    private boolean mIsHDR = false;

    {
        Class<?> clazz_MediaRecorder = null;
        try {
            clazz_MediaRecorder = Class.forName("android.media.MediaRecorder");
            method_setOnCameraReleasedListener = clazz_MediaRecorder
                    .getDeclaredMethod(
                            "setOnCameraReleasedListener",
                            new Class[] { android.media.MediaRecorder.OnInfoListener.class });
        } catch (Exception e) {
            Log.v(TAG, "inflect setOnCameraReleasedListener");
            e.printStackTrace();
        } finally {
            clazz_MediaRecorder = null;
        }
    }

    public static synchronized MtkCommon getInstance() {
        if (sInstance == null) {
            sInstance = new MtkCommon();
        }

        return sInstance;
    }

    private MtkCommon() {

    }

    public boolean supportVideoStopAsync() {
        if (method_setOnCameraReleasedListener == null) {
            Log.v(TAG,
                    "setOnCameraReleasedListener inflect error, so not support stop async");
            return false;
        } else {
            return false;
        }
    }

    public boolean handleBeforeStopAsync(VideoModule vm) throws Exception {
        Log.v(TAG, "handleBeforeStopAsync");

        try {
            method_setOnCameraReleasedListener.invoke(vm.getMediaRecorder(),
                    new Object[] { new MediaRecorderListener(vm) });
        } catch (Exception e) {
            Log.v(TAG, "setOnCameraReleasedListener execute error");
            throw e;
        }

        return true;
    }

    public boolean supportVideoStartAsync() {
        return false;
    }

    public boolean handleBeforeVideoStartAsync(VideoModule vm) throws Exception {
        try {
            vm.getMediaRecorder().setOnInfoListener(
                    new MediaRecorderListener(vm));
        } catch (Exception e) {
            Log.v(TAG, "setOnInfoListener error");
            throw e;
        }
        return true;
    }

    private final class MediaRecorderListener implements
            MediaRecorder.OnInfoListener {
        private cc.fotoplace.camera.VideoModule mVideoModule;
        private final static int MEDIA_RECORDER_INFO_CAMERA_RELEASE = 1999;
        private final static int MEDIA_RECORDER_INFO_START_TIMER = 1998;

        public MediaRecorderListener(cc.fotoplace.camera.VideoModule vm)
                throws Exception {
            mVideoModule = vm;
        }

        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            Log.v(TAG, "onInfo(), what = " + what + ", extra = " + extra);
            if (what == MEDIA_RECORDER_INFO_CAMERA_RELEASE) {
                mVideoModule.onAsyncStoped();
            } else if (what == MEDIA_RECORDER_INFO_START_TIMER) {
                mVideoModule.onAsyncStarted();
            } else {
                mVideoModule.onInfo(mr, what, extra);
            }
        }

    }

    public boolean supportZSD(int cameraId) {
        return true;
    }

    public boolean setZSD(boolean enable, Parameters param) {
        Log.v(TAG, "setZSD to " + enable);
        if (enable) {
            param.set("zsd-mode", "on");
            param.set("mtk-cam-mode", "1");
            mIsZSD = true;
        } else {
            param.set("zsd-mode", "off");
            if (!mIsHDR) {
                param.set("mtk-cam-mode", "0");
            }
            mIsZSD = false;
        }
        return true;
    }

    public boolean supportHDR(int cameraId) {
        return true;
    }

    public boolean setHDR(boolean enable, Parameters param) {
        Log.v(TAG, "setHDR to " + enable);
        if (enable) {
            param.set("scene-mode", "hdr");
            param.set("mtk-cam-mode", "1");
            mIsHDR = true;
        } else {
            param.set("scene-mode", "auto");
            if (!mIsZSD) {
                param.set("mtk-cam-mode", "0");
            }
            mIsHDR = false;
        }

        return true;
    }
}
