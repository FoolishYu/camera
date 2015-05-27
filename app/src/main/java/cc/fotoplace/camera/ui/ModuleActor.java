package cc.fotoplace.camera.ui;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera.Parameters;
import android.view.View;
import android.widget.ImageView;
import cc.fotoplace.camera.CameraActivity;
import cc.fotoplace.camera.ComboPreferences;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.ShutterButton;
import cc.fotoplace.camera.Util;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;

public abstract class ModuleActor {
    
    private Context mContext;
    protected CameraControls mControls;
    protected View mIndicator;                        // all
    protected ImageView mLowerControls;               // all
    protected ModuleIndicatorPanel mIndicatorPanel;  // all
    protected ShutterButton mPhotoShutter;            // all
    protected RotateImageButton mPhotoFlash;          // all
    protected RotateImageButton mVideoFlash;          // all
    protected ThumbnailLayout mPhotoThumbnailView;    // all

    public ModuleActor(Context context, CameraControls controls) {
        mContext = context;
        mControls = controls;
        initializeCommonControls();
        initializeSpecificControls();
    }

    protected void initializeCommonControls() {
        mIndicator = mControls.getModuleIndicator();
        mLowerControls = (ImageView) mControls.getLowerControls();
        mIndicatorPanel = (ModuleIndicatorPanel) mControls.getModuleIndicatorPanel();
        mPhotoShutter = (ShutterButton) mControls.getPhotoShutter();
        mPhotoFlash = (RotateImageButton) mControls.getPhotoFlash();
        mVideoFlash = (RotateImageButton) mControls.getVideoFlash();
        mPhotoThumbnailView = (ThumbnailLayout) mControls.getPhotoThumbnailView();
    }

    protected abstract void initializeViews(ComboPreferences pref);

    protected abstract void initializeSpecificControls();

    protected abstract void initializeControlsByIntent(Intent intent, ComboPreferences prefs);

    protected abstract void animateToPrevModule(int moduleIndex, ComboPreferences pref, Parameters params, AnimatioinCallback callback);

    protected abstract void animateToNextModule(int moduleIndex, ComboPreferences pref, Parameters params, AnimatioinCallback callback);

    protected void fadeIn(View view) {
        if (view != null) {
            Util.fadeIn(view, 0F, 1F, CameraActivity.ANIMATION_DURATION);
        }
    }

    protected void fadeOut(View view) {
        if (view != null) {
            Util.fadeOut(view, 1F, 0F, CameraActivity.ANIMATION_DURATION);
        }
    }

    protected void transistTo(ImageView view, int fromResId, int toResId) {
        if (view != null) {
            Util.transistTo(mContext, view, fromResId, toResId, CameraActivity.ANIMATION_DURATION);
        }
    }

    protected void updateFlashButton(String flashmode) {
        if (Parameters.FLASH_MODE_OFF.equals(flashmode)) {
            mVideoFlash.setImageResource(R.drawable.ic_camera_light_off);
        } else {
            mVideoFlash.setImageResource(R.drawable.ic_camera_light_on);
        }
    }

    protected boolean supportFrontFlash(Parameters params) {
        List<String> supportedFlashMode = params.getSupportedFlashModes();
        if (supportedFlashMode == null) {
            return false;
        }
        
        for (String s : supportedFlashMode) {
            if (s.equals(Parameters.FLASH_MODE_TORCH)) {
                return true;
            }
        }
        return false;
    }

}
