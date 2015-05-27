package cc.fotoplace.camera.platform;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;

import java.util.List;

interface PlatfromInterface {

    boolean supportExternalSDCard();

    boolean supportVideoSnapShot(Parameters parame);

    boolean supportVideoQuality();

    boolean supportVideoStopAsync();

    boolean handleBeforeStopAsync(cc.fotoplace.camera.VideoModule vm)
            throws Exception;

    boolean supportVideoStartAsync();

    boolean handleBeforeVideoStartAsync(cc.fotoplace.camera.VideoModule vm)
            throws Exception;

    CamcorderProfile CamcorderProfile_get(int cameraId, int quality);

    boolean CamcorderProfile_hasProfile(int cameraId, int quality);

    int getDropInitialPreviewCount(int cameraId);

    Size getScanResolution(List<Size> supported);
    
    boolean supportSwitchBlur();

    /**
     * @param cameraId
     *            front and back camera may not support zsd both
     * @return true:support false:not support
     */
    boolean supportZSD(int cameraId);

    /**
     * @param enable
     *            true:enable false:disable
     * @param param
     *            platform will set zsd with camera parameter
     * @return true:success false:fail
     */
    boolean setZSD(boolean enable, Parameters param);

    /**
     * @param cameraId
     *            front and back camera may not support hdr both
     * @return true:support false:not support
     */
    boolean supportHDR(int cameraId);

    /**
     * @param enable
     *            true:enable false:disable
     * @param param
     *            platform will set hdr with camera parameter
     * @return true:success false:fail
     */
    boolean setHDR(boolean enable, Parameters param);
}
