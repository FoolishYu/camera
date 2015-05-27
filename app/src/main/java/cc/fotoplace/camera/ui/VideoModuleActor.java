package cc.fotoplace.camera.ui;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera.Parameters;
import android.view.View;
import android.widget.ImageView;
import cc.fotoplace.camera.CameraActivity;
import cc.fotoplace.camera.CameraSettings;
import cc.fotoplace.camera.ComboPreferences;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.ShutterButton;
import cc.fotoplace.camera.Util;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;

public class VideoModuleActor extends ModuleActor {

    private ShutterButton mVideoShutter;            // video, photo
    private View mVideoThumbnailView;               // photo, video
    private RotateImageButton mBeautyButton;        // photo, video, scan
    private RotateImageButton mPhotoSettingButton;  // photo, video, scan
    private RotateImageButton mVideoSettingButton;  // photo, video
    private ImageView mPhotoLines;                  // photo, video, scan

    public VideoModuleActor(Context context, CameraControls controls) {
        super(context, controls);
    }

    @Override
    protected void initializeViews(ComboPreferences pref) {
    }

    @Override
    protected void initializeSpecificControls() {
        mVideoShutter = (ShutterButton) mControls.getVideoShutter();
        mVideoThumbnailView = mControls.getVideoThumbnailView();
        mBeautyButton = (RotateImageButton) mControls.getEffectsButton();
        mPhotoSettingButton = (RotateImageButton) mControls.getPhotoSetting();
        mVideoSettingButton = (RotateImageButton) mControls.getVideoSetting();
        mPhotoLines = mControls.getPhotoLines();
    }

    @Override
    protected void initializeControlsByIntent(Intent intent, ComboPreferences prefs) {
        if (intent == null) {
            return;
        }
        String flashmode = null;
        if (Util.isVideoCameraIntent(intent)) {
            mPhotoShutter.setVisibility(View.GONE);
            flashmode = CameraSettings.readFlashMode(prefs,
                    CameraActivity.PHOTO_MODULE_INDEX);
            mControls.updatePhotoFlashButton(flashmode);
            flashmode = CameraSettings.readFlashMode(prefs,
                    CameraActivity.VIDEO_MODULE_INDEX);
            mControls.updateVideoFlashButton(flashmode);
            mPhotoSettingButton.setVisibility(View.GONE);
            mPhotoFlash.setVisibility(View.GONE);
            mVideoShutter.setVisibility(View.VISIBLE);
            mBeautyButton.setVisibility(View.GONE);
            mPhotoThumbnailView.setVisibility(View.GONE);
            mVideoThumbnailView.setVisibility(View.VISIBLE);
            mVideoSettingButton.setVisibility(View.VISIBLE);
        } else if (Util.isVideoCaptureIntent(intent)){
            flashmode = CameraSettings.readFlashMode(prefs,
                    CameraActivity.VIDEO_MODULE_INDEX);
            mControls.updateVideoFlashButton(flashmode);
            mPhotoShutter.setVisibility(View.GONE);
            mVideoShutter.setVisibility(View.VISIBLE);
            mVideoThumbnailView.setVisibility(View.GONE);
            mPhotoThumbnailView.setVisibility(View.GONE);
            mBeautyButton.setVisibility(View.GONE);
            mVideoFlash.setVisibility(View.VISIBLE);
            mPhotoFlash.setVisibility(View.GONE);
            mPhotoSettingButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void animateToPrevModule(int moduleIndex, ComboPreferences pref, Parameters params,
            AnimatioinCallback callback) {
        // no previous module
    }

    @Override
    protected void animateToNextModule(int moduleIndex, ComboPreferences pref, Parameters params,
            AnimatioinCallback callback) {
        mIndicatorPanel.setAnimationCallback(callback);
        mIndicatorPanel.setSelected(moduleIndex);
        fadeIn(mPhotoShutter);
        fadeOut(mVideoShutter);
        fadeIn(mBeautyButton);
        fadeOut(mVideoFlash);
        fadeIn(mPhotoThumbnailView);
        fadeOut(mVideoThumbnailView);
        if (supportFrontFlash(params)) {
            fadeIn(mPhotoFlash);
        }
        fadeIn(mPhotoSettingButton);
        fadeOut(mVideoSettingButton);
        mControls.updateEffectsButton(false, false);
        mPhotoSettingButton.setImageResource(R.drawable.ic_camera_setting);
    }

}
