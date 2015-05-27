package cc.fotoplace.camera;

import android.view.SurfaceHolder;
import android.view.View;

import cc.fotoplace.camera.ShutterButton.OnShutterButtonListener;


//拍照接口
public interface PhotoController extends OnShutterButtonListener {

    public static final int FLASH_MODE_OFF = 0;
    public static final int FLASH_MODE_AUTO = 1;
    public static final int FLASH_MODE_ON = 2;

    // returns the actual set zoom value
    public int onZoomChanged(int requestedZoom, boolean isSmooth);

    public boolean isCameraIdle();
    
    public boolean isSnapshotting();

    public void onCaptureDone();

    public void onCaptureCancelled();

    public void onCaptureRetake();

    public void cancelAutoFocus();

    public void stopPreview();

    public int getCameraState();

    public void onSingleTapUp(View view, int x, int y);

    public void onSurfaceCreated(SurfaceHolder holder);

    public void onCountDownFinished();

    public void onScreenSizeChanged(int width, int height, int previewWidth,
            int previewHeight);

    public void onAnimationEnd();

    public String getFlashMode(int moduleIndex);

    public void onFaceBeautyChanged();

    public void onLinesChanged();
    
    public void onHDRChanged();
    
    public boolean supportHDR();

    public boolean changeFullscreenState(boolean isExpand);

    public int getCameraId();

}
