
package cc.fotoplace.camera;

import android.media.MediaRecorder;
import android.view.View;

import cc.fotoplace.camera.ShutterButton.OnShutterButtonListener;

public interface VideoController extends OnShutterButtonListener {

    public static final int FLASH_MODE_TORCH = 0;
    public static final int FLASH_MODE_OFF = 1;

    public void onReviewDoneClicked(View view);

    public void onReviewCancelClicked(View viwe);

    public void onReviewPlayClicked(View view);

    public void onReviewRetakeClicked(View view);

    public boolean isInReviewMode();

    public int onZoomChanged(int index, boolean isSmooth);

    public void onSingleTapUp(View view, int x, int y);

    public void stopPreview();

    public void takeSnapshot();

    public int getOrientation();
    
    public void onAnimationEnd();
    
    public void onAsyncStoped();
    
    public void onAsyncStarted();
    
    public boolean isVideoRecording();
    
    public boolean isVideoStarting();
    
    public boolean isVideoStopping();
    
    public int getCameradId();
    
    public MediaRecorder getMediaRecorder();
}
