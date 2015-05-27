package cc.fotoplace.camera.platform;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.text.StaticLayout;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import cc.fotoplace.camera.VideoModule;
import cc.fotoplace.camera.platform.MtkCommon;


public class Mtk6592 implements PlatfromInterface {

    private static final String TAG = "Mtk6592";

    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_HIGH = 1;
    public static final int QUALITY_QCIF = 2;
    public static final int QUALITY_CIF = 3;
    public static final int QUALITY_480P = 4;
    public static final int QUALITY_720P = 5;
    public static final int QUALITY_1080P = 6;
    public static final int QUALITY_QVGA = 7;

    // Mtk qualities
    public static final int QUALITY_MTK_LOW = 8;
    public static final int QUALITY_MTK_MEDIUM = 9;
    public static final int QUALITY_MTK_HIGH = 10;
    public static final int QUALITY_MTK_FINE = 11;
    public static final int QUALITY_MTK_NIGHT_LOW = 12;
    public static final int QUALITY_MTK_NIGHT_MEDIUM = 13;
    public static final int QUALITY_MTK_NIGHT_HIGH = 14;
    public static final int QUALITY_MTK_NIGHT_FINE = 15;
    public static final int QUALITY_MTK_LIVE_EFFECT = 16;
    public static final int QUALITY_MTK_H264_HIGH = 17;
    public static final int QUALITY_MTK_1080P = 18;

    // Start and end of quality list
    private static final int QUALITY_LIST_START = QUALITY_LOW;
    private static final int QUALITY_LIST_END = QUALITY_QVGA;

    public Mtk6592() {
        Log.v(TAG, "platform is mtk6592");
    }

    @Override
    public boolean supportExternalSDCard() {
        return false;
    }

    @Override
    public boolean supportVideoSnapShot(Parameters parame) {
        // return Util.isVideoSnapshotSupported(param);
        // x2 front camera return incorrect "false".
        return true;
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
        int mtkQuality = convertToMtkQuality(cameraId, quality);
        Class<?> clazz = null;
        Method method = null;
        CamcorderProfile cp = null;
        try {
            clazz = Class.forName("com.mediatek.camcorder.CamcorderProfileEx");
            method = clazz.getDeclaredMethod("getProfile", new Class[] {
                    int.class, int.class });
            cp = (CamcorderProfile) method.invoke(
                    null,
                    new Object[] { Integer.valueOf(cameraId),
                            Integer.valueOf(mtkQuality) });
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            clazz = null;
            method = null;
        }
        return cp;
    }

    private int convertToMtkQuality(int cameraId, int quality) {
        int mtkQuality = 0;
        switch (quality) {
        case CamcorderProfile.QUALITY_1080P:
            mtkQuality = QUALITY_MTK_FINE;
            break;
        case CamcorderProfile.QUALITY_720P:
            mtkQuality = QUALITY_MTK_HIGH;
            break;
        case CamcorderProfile.QUALITY_480P:
            mtkQuality = QUALITY_MTK_MEDIUM;
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
    public boolean CamcorderProfile_hasProfile(int cameraId, int quality) {
        return quality > CamcorderProfile.QUALITY_LOW
                && quality <= CamcorderProfile.QUALITY_1080P;
    }

    @Override
    public int getDropInitialPreviewCount(int cameraId) {
        return 2;
    }

    @Override
    public Size getScanResolution(List<Size> supported) {
        int optimalRes = 1920 * 1080;
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
        return MtkCommon.getInstance().supportZSD(cameraId);
    }

    @Override
    public boolean setZSD(boolean enable, Parameters param) {
        return MtkCommon.getInstance().setZSD(enable, param);
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
