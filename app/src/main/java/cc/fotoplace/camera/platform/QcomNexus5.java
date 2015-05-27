package cc.fotoplace.camera.platform;

import java.util.List;

import android.hardware.Camera.Size;
import android.util.Log;

public class QcomNexus5 extends DefaultHardware {
    
    public QcomNexus5() {
        Log.v(PlatformHelper.TAG, "platform is QcomNexus5");
    }
    
    @Override
    public Size getScanResolution(List<Size> supported) {
        int optimalRes = 1920 * 1080;
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
}
