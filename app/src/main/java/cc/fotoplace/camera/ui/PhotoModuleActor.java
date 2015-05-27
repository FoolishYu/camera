package cc.fotoplace.camera.ui;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import cc.fotoplace.camera.CameraActivity;
import cc.fotoplace.camera.CameraSettings;
import cc.fotoplace.camera.ComboPreferences;
import cc.fotoplace.camera.ShutterButton;
import cc.fotoplace.camera.Util;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;


public class PhotoModuleActor extends ModuleActor {
    
    private ShutterButton mVideoShutter;            // video, photo
    private View mSwitcher;                         // photo, video, scan
    private View mVideoThumbnailView;               // photo, video
    private RotateImageButton mEffectsButton;       // photo, video, scan
    private RotateImageButton mPhotoSetting;        // photo, video, scan
    private RotateImageButton mVideoSetting;        // photo, video
    private ImageView mPhotoLines;                  // photo, video, scan

    public PhotoModuleActor(Context context, CameraControls controls) {
        super(context, controls);
    }

    @Override
    protected void initializeViews(ComboPreferences pref) {
        mControls.updateEffectsButton(false, false);
        String flashmode = CameraSettings.readFlashMode(pref, CameraActivity.PHOTO_MODULE_INDEX);
        Log.v("mk", "PhotoModuleActor, flash mode = " + flashmode);
        mControls.updatePhotoFlashButton(flashmode);
        boolean linesOn = pref.getBoolean(CameraSettings.KEY_PHOTO_ALIGNMENT_LINES, false);
        mControls.updatePhotoSettingButton(false);
    }

    @Override
    protected void initializeSpecificControls() {
        mVideoShutter = (ShutterButton) mControls.getVideoShutter();
        mVideoThumbnailView = mControls.getVideoThumbnailView();
        mEffectsButton = (RotateImageButton) mControls.getEffectsButton();
        mSwitcher = mControls.getSwitcher();
        mPhotoSetting = (RotateImageButton) mControls.getPhotoSetting();
        mVideoSetting = (RotateImageButton) mControls.getVideoSetting();
        mPhotoLines = mControls.getPhotoLines();
    }

    @Override
    protected void initializeControlsByIntent(Intent intent, ComboPreferences prefs) {
        if (intent == null) {
            return;
        }
        if (Util.isImageCaptureIntent(intent)) {
            mPhotoThumbnailView.setVisibility(View.GONE);
            mVideoThumbnailView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void animateToPrevModule(int moduleIndex, ComboPreferences pref, Parameters params,
            AnimatioinCallback callback) {
        // from photo to video module
        String flashmode = CameraSettings.readFlashMode(pref, moduleIndex);
        updateFlashButton(flashmode);
        mIndicatorPanel.setAnimationCallback(callback);
        mIndicatorPanel.setSelected(moduleIndex);
        fadeIn(mVideoShutter);
        fadeOut(mPhotoShutter);
        Util.alphaIn(mLowerControls, 0F, 1F, 0);
        fadeOut(mEffectsButton);
        if (supportFrontFlash(params)) {
            fadeIn(mVideoFlash);
        }
        fadeOut(mPhotoFlash);
        fadeOut(mPhotoThumbnailView);
        fadeIn(mVideoThumbnailView);
        fadeOut(mPhotoLines);
        mControls.updatePhotoSettingButton(false);
        fadeOut(mPhotoSetting);
        fadeIn(mVideoSetting);
    }

    @Override
    protected void animateToNextModule(int moduleIndex, ComboPreferences pref, Parameters params,
            AnimatioinCallback callback) {
        // from photo to video scan module
        String flashmode = CameraSettings.readFlashMode(pref, moduleIndex);
        updateFlashButton(flashmode);
        mIndicatorPanel.setAnimationCallback(callback);
        mIndicatorPanel.setSelected(moduleIndex);
        fadeOut(mPhotoShutter);
        Util.alphaIn(mLowerControls, 0F, 1F, 0);
        if (supportFrontFlash(params)) {
            fadeIn(mVideoFlash);
        }
        fadeOut(mPhotoFlash);
        fadeOut(mEffectsButton);
        fadeOut(mSwitcher);
        fadeOut(mPhotoThumbnailView);
        fadeOut(mPhotoSetting);
        fadeOut(mLowerControls);
        fadeOut(mPhotoLines);
    }

}
