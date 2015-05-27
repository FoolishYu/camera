package cc.fotoplace.camera.platform;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.util.Log;

import cc.fotoplace.camera.Util;
import cc.fotoplace.camera.VideoModule;

import java.lang.reflect.Method;
import java.util.List;

class Mtk6589 implements PlatfromInterface {

    private static final String TAG = "Mtk6589";

    public static final int QUALITY_LOW = 0; // 176 x 144
    public static final int QUALITY_HIGH = 1; // 640 x 480
    public static final int QUALITY_QCIF = 2; // 176 x 144
    public static final int QUALITY_CIF = 3; // 255 x 288, Android Original, not
                                             // supported on Mtk platform.
    public static final int QUALITY_480P = 4; // 640 x 480
    public static final int QUALITY_720P = 5; // 1280 x 720, Android Original,
                                              // not supported on Mtk platform.
    public static final int QUALITY_1080P = 6; // 1920 x 1080, Android Original,
                                               // not supported on Mtk platform.
    public static final int QUALITY_QVGA = 7; // 320 x 240, Android Original,
                                              // not supported on Mtk platform.
    public static final int QUALITY_MTK_LOW = 8; // 176 x 144
    public static final int QUALITY_MTK_MEDIUM = 9; // 640 x 480
    public static final int QUALITY_MTK_HIGH = 10; // 1280 x 720
    public static final int QUALITY_MTK_FINE = 11; // 1920 x 1088
    public static final int QUALITY_MTK_NIGHT_LOW = 12; // 176 x 144
    public static final int QUALITY_MTK_NIGHT_MEDIUM = 13; // 640 x 480
    public static final int QUALITY_MTK_NIGHT_HIGH = 14; // 1280 x 720
    public static final int QUALITY_MTK_NIGHT_FINE = 15; // 1920 x 1080
    public static final int QUALITY_MTK_LIVE_EFFECT = 16; // 480 x 320
    public static final int QUALITY_MTK_H264_HIGH = 17; // ? x ?, Android
                                                        // Original, not
                                                        // supported on Mtk
                                                        // platform.
    public static final int QUALITY_MTK_1080P = 18; // 1920 x 1080
    // private static final int QUALITY_LIST_START = QUALITY_LOW;
    // private static final int QUALITY_LIST_END = QUALITY_QVGA;
    public static final int QUALITY_MTK_LIST_END = QUALITY_MTK_1080P;
    public static final int QUALITY_TIME_LAPSE_LOW = 1000;
    public static final int QUALITY_TIME_LAPSE_HIGH = 1001;
    public static final int QUALITY_TIME_LAPSE_QCIF = 1002;
    public static final int QUALITY_TIME_LAPSE_CIF = 1003;
    public static final int QUALITY_TIME_LAPSE_480P = 1004;
    public static final int QUALITY_TIME_LAPSE_720P = 1005;
    public static final int QUALITY_TIME_LAPSE_1080P = 1006;
    public static final int QUALITY_TIME_LAPSE_QVGA = 1007;

    public Mtk6589() {
        Log.v(TAG, "platform is Mtk6589");
    }

    @Override
    public boolean supportExternalSDCard() {
        return true;
    }

    @Override
    public boolean supportVideoSnapShot(Parameters param) {
        return Util.isVideoSnapshotSupported(param);
    }

    @Override
    public boolean supportVideoQuality() {
        return true;
    }

    @Override
    public boolean supportVideoStopAsync() {
        return MtkCommon.getInstance().supportVideoStopAsync();
    }

    @Override
    public boolean handleBeforeStopAsync(VideoModule vm) throws Exception {
        return MtkCommon.getInstance().handleBeforeStopAsync(vm);
    }

    @Override
    public boolean supportVideoStartAsync() {
        return MtkCommon.getInstance().supportVideoStartAsync();
    }

    @Override
    public boolean handleBeforeVideoStartAsync(VideoModule vm) throws Exception {
        return MtkCommon.getInstance().handleBeforeVideoStartAsync(vm);
    }

    @Override
    public CamcorderProfile CamcorderProfile_get(int cameraId, int quality) {
        int mtkQuality = convertToMtkQuality(quality);
        Class<?> clazz = null;
        Method method = null;
        CamcorderProfile cp = null;
        try {
            clazz = Class.forName(CamcorderProfile.class.getName());
            method = clazz.getDeclaredMethod("getMtk", new Class[] { int.class,
                    int.class });
            cp = (CamcorderProfile) method.invoke(
                    null,
                    new Object[] { Integer.valueOf(0),
                            Integer.valueOf(mtkQuality) });
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            clazz = null;
            method = null;
        }
        return cp;
    }

    @Override
    public boolean CamcorderProfile_hasProfile(int cameraId, int quality) {
        int mtkQuality = convertToMtkQuality(quality);
        return CamcorderProfile.hasProfile(cameraId, mtkQuality);
    }

    private int convertToMtkQuality(int quality) {
        int mtkQuality = 0;
        switch (quality) {
        case CamcorderProfile.QUALITY_1080P:
            mtkQuality = QUALITY_MTK_1080P;
            break;
        case CamcorderProfile.QUALITY_720P:
            mtkQuality = QUALITY_MTK_HIGH;
            break;
        case CamcorderProfile.QUALITY_480P:
            mtkQuality = QUALITY_480P;
            break;
        case CamcorderProfile.QUALITY_CIF: // force changed to QCIF
            mtkQuality = QUALITY_QCIF;
            break;
        case CamcorderProfile.QUALITY_QCIF:
            mtkQuality = QUALITY_QCIF;
            break;
        case CamcorderProfile.QUALITY_QVGA:
            mtkQuality = QUALITY_480P; // force changed to QUALITY_480P
            break;
        case CamcorderProfile.QUALITY_HIGH:
            mtkQuality = QUALITY_HIGH;
            break;
        case CamcorderProfile.QUALITY_LOW:
            mtkQuality = QUALITY_LOW;
            break;
        default:
            break;
        }
        return mtkQuality;
    }

    @Override
    public int getDropInitialPreviewCount(int cameraId) {
        return 2;
    }

    @Override
    public Size getScanResolution(List<Size> supported) {
        int optimalRes = 1280 * 720;
        int minDiff = 100000000;
        int minIndex = 0;
        for (int i = 0; i < supported.size(); i++) {
            int curDiff = Math.abs(supported.get(i).width
                    * supported.get(i).height - optimalRes);
            if (curDiff < minDiff) {
                minDiff = curDiff;
                minIndex = i;
            }
        }
        return supported.get(minIndex);
    }

    @Override
    public boolean supportZSD(int cameraId) {
        return false;
    }

    @Override
    public boolean setZSD(boolean enable, Parameters param) {
        return false;
    }

    @Override
    public boolean supportHDR(int cameraId) {
        return MtkCommon.getInstance().supportHDR(cameraId);
    }

    @Override
    public boolean setHDR(boolean enable, Parameters param) {
        return MtkCommon.getInstance().setHDR(enable, param);
    }

    @Override
    public boolean supportSwitchBlur() {
        // TODO Auto-generated method stub
        return true;
    }
}
