package cc.fotoplace.camera.ui;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera.Parameters;

import cc.fotoplace.camera.ComboPreferences;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;


public class PanoramaModuleActor extends ModuleActor {
    
    public PanoramaModuleActor(Context context, CameraControls controls) {
        super(context, controls);
    }

    @Override
    protected void initializeSpecificControls() {
        // No special controls
    }
    
    @Override
    protected void initializeViews(ComboPreferences pref) {
    }

    @Override
    protected void initializeControlsByIntent(Intent intent, ComboPreferences prefs) {
        // No third-party invocation
    }

    @Override
    protected void animateToPrevModule(int moduleIndex, ComboPreferences pref, Parameters params,
            AnimatioinCallback callback) {
        // from pano to scan module
        mIndicatorPanel.setAnimationCallback(callback);
        mIndicatorPanel.setSelected(moduleIndex);
        fadeOut(mPhotoShutter);
        if(supportFrontFlash(params)){
            fadeIn(mVideoFlash);
        }
        fadeOut(mPhotoThumbnailView);
        fadeOut(mLowerControls);
    }

    @Override
    protected void animateToNextModule(int moduleIndex, ComboPreferences pref, Parameters params,
            AnimatioinCallback callback) {
        // no next module
    }

}
