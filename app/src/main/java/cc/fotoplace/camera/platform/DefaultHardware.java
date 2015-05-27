package cc.fotoplace.camera.platform;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;

import java.util.List;

import cc.fotoplace.camera.VideoModule;

public class DefaultHardware implements PlatfromInterface {

    @Override
    public boolean supportExternalSDCard() {
        return false;
    }

    @Override
    public boolean supportVideoSnapShot(Parameters parame) {
        return false;
    }

    @Override
    public boolean supportVideoQuality() {
        return true;
    }

    @Override
    public boolean supportVideoStopAsync() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean handleBeforeStopAsync(VideoModule vm) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean supportVideoStartAsync() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean handleBeforeVideoStartAsync(VideoModule vm) throws Exception {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public CamcorderProfile CamcorderProfile_get(int cameraId, int quality) {
        return CamcorderProfile.get(cameraId, quality);
    }

    @Override
    public boolean CamcorderProfile_hasProfile(int cameraId, int quality) {
        return CamcorderProfile.hasProfile(cameraId, quality);
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
            int curDiff = Math.abs(supported.get(i).width * supported.get(i).height - optimalRes);
            if (curDiff < minDiff) {
                minDiff = curDiff;
                minIndex = i;
            }
        }
        return supported.get(minIndex);
    }

    @Override
    public boolean supportZSD(int cameraId) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean setZSD(boolean enable, Parameters param) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean supportHDR(int cameraId) {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean setHDR(boolean enable, Parameters param) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean supportSwitchBlur() {
        // TODO Auto-generated method stub
        return true;
    }

}
