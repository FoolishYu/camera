package cc.fotoplace.camera.platform;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.os.Build;
import android.util.Log;

import java.util.List;

public class PlatformHelper {

    public static String TAG = "PlatformHelper";
    
    public static interface PLATFORM_CODES {
        public static final int MTK_6589 = 0;
        public static final int QUALCOMM = 1;
    }

    private static PlatfromInterface sInstance;

    static {
        Log.v(TAG, "Build.HARDWARE:"+Build.HARDWARE);
        if (Build.HARDWARE.contains("6571")) {
            sInstance = new Mtk6571();
        } else if (Build.HARDWARE.contains("6572")) {
            sInstance = new Mtk6572();
        } else if (Build.HARDWARE.contains("6589")) {
            sInstance = new Mtk6589();
        } else if (Build.HARDWARE.contains("6582")) {
            sInstance = new Mtk6582();
        } else if (Build.HARDWARE.contains("6592")) {
            sInstance = new Mtk6592();
        } else if (Build.HARDWARE.contains("hammerhead")) {
            sInstance = new QcomNexus5();
        } else {
            sInstance = new DefaultHardware();
        }
    }

    public static boolean supportExternalSDCard() {
        return sInstance.supportExternalSDCard();
    }

    public static boolean supportVideoSnapShot(Parameters params) {
        return sInstance.supportVideoSnapShot(params);
    }

    public static boolean supportVideoQuality() {
        return sInstance.supportVideoQuality();
    }
    
    public static boolean supportVideoStopAsync() {
        return sInstance.supportVideoStopAsync();
    }
    
    public static boolean handleBeforeStopAsync(cc.fotoplace.camera.VideoModule vm) throws Exception {
        return sInstance.handleBeforeStopAsync(vm);
    }
    
    public static boolean supportVideoStartAsync() {
        return sInstance.supportVideoStartAsync();
    }
    
    public static boolean handleBeforeVideoStartAsync(cc.fotoplace.camera.VideoModule vm) throws Exception {
        return sInstance.handleBeforeVideoStartAsync(vm);
    }
    
    public static CamcorderProfile CamcorderProfile_get(int cameraId, int quality) {
        return sInstance.CamcorderProfile_get(cameraId, quality);
    }

    public static boolean CamcorderProfile_hasProfile(int cameraId, int quality) {
        return sInstance.CamcorderProfile_hasProfile(cameraId, quality);
    } 
    
    public static int getDropInitialPreviewCount(int cameraId) {
        return sInstance.getDropInitialPreviewCount(cameraId);
    } 

    public static Size getScanResolution(List<Size> supported) {
        return sInstance.getScanResolution(supported);
    }
    
    public static boolean supportZSD(int cameraId) {
        return sInstance.supportZSD(cameraId);
    } 

    public static boolean setZSD(boolean enable, Parameters param) {
        return sInstance.setZSD(enable, param);
    }
    
    public static boolean supportHDR(int cameraId) {
        return sInstance.supportHDR(cameraId);
    } 

    public static boolean setHDR(boolean enable, Parameters param) {
        return sInstance.setHDR(enable, param);
    }
    
    public static boolean supportSwitchBlur() {
        return sInstance.supportSwitchBlur();
    }
}
