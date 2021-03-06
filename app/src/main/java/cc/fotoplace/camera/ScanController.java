
package cc.fotoplace.camera;

import android.view.SurfaceHolder;
import android.view.View;

import cc.fotoplace.camera.ShutterButton.OnShutterButtonListener;

public interface ScanController extends OnShutterButtonListener {

    public static final int FLASH_MODE_TORCH = 0;
    public static final int FLASH_MODE_OFF = 1;

    // returns the actual set zoom value
    public int onZoomChanged(int requestedZoom);

    public boolean isCameraIdle();

    public void cancelAutoFocus();

    public void stopPreview();

    public int getCameraState();

    public void onSingleTapUp(View view, int x, int y);

    public void onSurfaceCreated(SurfaceHolder holder);

    public void onCountDownFinished();
    
    public void onAnimationEnd();

    public void onScreenSizeChanged(int width, int height, int previewWidth, int previewHeight);

}
