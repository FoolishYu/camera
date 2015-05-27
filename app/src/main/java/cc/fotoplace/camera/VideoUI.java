package cc.fotoplace.camera;

import java.util.List;

import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.AsyncTask;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import cc.fotoplace.camera.CameraPreference.OnPreferenceChangedListener;
import cc.fotoplace.camera.FocusOverlayManager.FocusUI;
import cc.fotoplace.camera.platform.PlatformHelper;
import cc.fotoplace.camera.ui.CameraControls;
import cc.fotoplace.camera.ui.CameraSurfaceView;
import cc.fotoplace.camera.ui.FocusIndicator;
import cc.fotoplace.camera.ui.FocusIndicatorView;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;
import cc.fotoplace.camera.ui.RenderOverlay;
import cc.fotoplace.camera.ui.RotateImageButton;
import cc.fotoplace.camera.ui.RotateImageView;
import cc.fotoplace.camera.ui.ThumbnailLayout;
import cc.fotoplace.camera.ui.VideoRecordingPanel;
import cc.fotoplace.camera.ui.ZoomRenderer;

public class VideoUI implements FocusUI, PreviewGestures.SingleTapListener,
        AnimatioinCallback {

    private final static String TAG = "CAM_VideoUI";
    // module fields
    private CameraActivity mActivity;
    private View mRootView;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewLayout;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private View mReviewRetakeButton;
    private ShutterButton mShutterButton;
    private CameraControls mCameraControls;
    private VideoRecordingPanel mRecordingPanel;
    private RotateImageView mSwitcherButton;
    private RotateImageButton mSnapshotButton;
    private RotateImageView mFlashButton;
    private RotateImageView mSettingButton;
    private ComboPreferences mPreferences;

    // private View mTimeLapseLabel;
    private ZoomRenderer mZoomRenderer;
    private PreviewGestures mGestures;
    // private OnScreenIndicators mOnScreenIndicators;
    // private RotateLayout mRecordingTimeRect;
    private VideoController mController;

    // Zoom
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private ZoomChangeListener mZoomChangeListener = new ZoomChangeListener();
    private CameraSurfaceView mGLRootView;
    private FocusIndicatorView mFocusIndicatorView;
    private Thumbnail mVideoThumbnail;
    private Thumbnail mPhotoThumbnail;
    private ThumbnailLayout mPhotoThumbnailView;
    private ThumbnailLayout mVideoThumbnailView;

    private OnPreferenceChangedListener mListener;
    private boolean mSupportFlash = false;
    private int mFlashMode;
    private Parameters mParameters;

    private boolean mIsVideoCaptureIntent = false;

    public void setListener(OnPreferenceChangedListener listener) {
        mListener = listener;
    }

    public VideoUI(CameraActivity activity, VideoController controller, View parent,
            ComboPreferences prefs) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mPreferences = prefs;
        // Parse Intent
        mIsVideoCaptureIntent = Util.isVideoCaptureIntent(mActivity.getIntent());
        initViews();
        initializeOverlay();
        initializeControlByIntent();
        initializeMiscControls();
    }

    private void initViews() {
        // GLRootView
        mGLRootView = mActivity.getGLRootView();

        // Controls from video_module.xml
        mActivity.getLayoutInflater().inflate(R.layout.video_module, (ViewGroup) mRootView, true);
        mSnapshotButton = (RotateImageButton) mRootView.findViewById(R.id.snapshot);
        mFocusIndicatorView = (FocusIndicatorView) mRootView.findViewById(R.id.focus_indicator);

        // Camera Controls
        mCameraControls = (CameraControls) mActivity.findViewById(R.id.camera_controls);
        mCameraControls.initViews(CameraActivity.VIDEO_MODULE_INDEX, mPreferences);
        mSettingButton = (RotateImageView) mCameraControls.getVideoSetting();
        mShutterButton = (ShutterButton) mCameraControls.getVideoShutter();
        mFlashButton = (RotateImageView) mCameraControls.getVideoFlash();
        mVideoThumbnailView = (ThumbnailLayout) mActivity.findViewById(R.id.video_thumbnail_layout);
        mPhotoThumbnailView = (ThumbnailLayout) mActivity.findViewById(R.id.photo_thumbnail_layout);
    }

    private void showRecordingTimePanel() {
        if (mRecordingPanel == null) {
            mRecordingPanel = new VideoRecordingPanel(mActivity);
            mCameraControls.addView(mRecordingPanel);
            mRecordingPanel.setTextOrientation(mActivity.getOrientation());
            mRecordingPanel.setVisibility(View.GONE);
            Log.v("mk",
                    "initilizeRecordingTimePanel() -- mOrientation = " + mActivity.getOrientation());
            Util.fadeIn(mRecordingPanel);
        }
    }

    private void hideRecordingTimePanel() {
        if (mRecordingPanel != null) {
            Util.fadeOut(mRecordingPanel);
            mCameraControls.removeView(mRecordingPanel);
            mRecordingPanel = null;
        }
    }

    // Third-party Calling, from review_module_control.xml
    private void initializeControlByIntent() {
        mCameraControls.initilizeControlByIntent(CameraActivity.VIDEO_MODULE_INDEX,
                mActivity.getIntent(), mPreferences);
        if (mIsVideoCaptureIntent) {
            mSnapshotButton.setVisibility(View.GONE);
            mActivity.getLayoutInflater().inflate(R.layout.review_module, mCameraControls);
            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // // RotateImageView.
            mReviewLayout = mActivity.findViewById(R.id.review_panel);
            mReviewDoneButton = mActivity.findViewById(R.id.btn_done);
            mReviewCancelButton = mActivity.findViewById(R.id.btn_cancel);
            mReviewPlayButton = mActivity.findViewById(R.id.btn_play);
            mReviewRetakeButton = mActivity.findViewById(R.id.btn_retake);
            if (mReviewCancelButton != null) {
                mGestures.addTouchReceiver(mReviewCancelButton);
                mReviewCancelButton.setVisibility(View.VISIBLE);
                mReviewCancelButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mController.onReviewCancelClicked(v);
                    }
                });
            }
            if (mReviewDoneButton != null) {
                mGestures.addTouchReceiver(mReviewDoneButton);
                mReviewDoneButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mController.onReviewDoneClicked(v);
                    }
                });
            }
            if (mReviewPlayButton != null) {
                mGestures.addTouchReceiver(mReviewPlayButton);
                mReviewPlayButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mController.onReviewPlayClicked(v);
                    }
                });
            }
            if (mReviewRetakeButton != null) {
                mGestures.addTouchReceiver(mReviewRetakeButton);
                mReviewRetakeButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mController.onReviewRetakeClicked(v);
                    }
                });
            }
        }
    }

    public boolean collapseCameraControls() {
        boolean ret = false;
        return ret;
    }

    public boolean removeTopLevelPopup() {
        return false;
    }

    public void enableCameraControls(boolean enable) {
        if (mGestures != null) {
            mGestures.setZoomOnly(!enable);
        }
    }

    public void overrideSettings(final String... keyvalues) {
        // mVideoMenu.overrideSettings(keyvalues);
    }

    public View getPreview() {
        // return mPreviewFrameLayout;
        return null;
    }

    public void setOrientationIndicator(int orientation, boolean animation) {
        if (mGestures != null) {
            mGestures.setOrientation(orientation);
        }
        // We change the orientation of the linearlayout only for phone UI
        // because when in portrait the width is not enough.
    }

    public void onOrientationChanged(int orientation) {
        mCameraControls.onOrientationChanged(orientation);
        if (mZoomRenderer != null)
            mZoomRenderer.setOrientation(orientation);
        if (mIsVideoCaptureIntent) {
            ((RotateImageView) mReviewPlayButton).setOrientation(orientation, true);
        }
        if (mSnapshotButton != null) {
            mSnapshotButton.setOrientation(orientation, true);
        }
        mSettingButton.setOrientation(orientation, true);
        ((RotateImageButton) mCameraControls.getPhotoSetting()).setOrientation(orientation, true);
        ((RotateImageButton) mCameraControls.getEffectsButton()).setOrientation(orientation, true);
     }

    public SurfaceHolder getSurfaceHolder() {
        return mGLRootView.getHolder();
    }

    public void hideSurfaceView() {
        // mPreviewSurfaceView.setVisibility(View.GONE);
    }

    public void showSurfaceView() {
        // mPreviewSurfaceView.setVisibility(View.VISIBLE);
    }

    // Controls from video_module.xml
    private void initializeOverlay() {
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
        }
        mRenderOverlay.addRenderer(mZoomRenderer);
        if (mGestures == null) {
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer,
                    mActivity);
        }
        mGestures.setRenderOverlay(mRenderOverlay);
        mGestures.reset();

        // Add Unclickable Area to block focus action
        mGestures.addUnclickableArea(mActivity.findViewById(R.id.lower_controls));
        mGestures.addUnclickableArea(mActivity.findViewById(R.id.upper_block_area));

        mGestures.addTouchReceiver(mSettingButton);
        mSettingButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isVideoRecording() || mController.isVideoStarting() || mController.isVideoStopping()) {
                    Log.v(TAG, "setting button click cancel while recording");
                    return;
                }
                Intent intent = new Intent(mActivity, CameraSettingActivity.class);
                       intent.putExtra("fromVideo", true);
                mActivity.startActivity(intent);
            }
        });
        mGestures.addTouchReceiver(mFlashButton);
        mFlashButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mController.isVideoStarting()
                        || mController.isVideoStopping()) {
                    Log.v(TAG, "flash button click cancel while starting or stopping recorder");
                    return;
                }
                mListener.onCameraFlashModeClicked(mFlashMode);
            }
        });
        mGestures.addTouchReceiver(mShutterButton);
        // mPreviewThumb = mActivity.findViewById(R.id.preview_thumb);
        // mPreviewThumb.setOnClickListener(new OnClickListener() {
        // @Override
        // public void onClick(View v) {
        // mActivity.gotoGallery();
        // }
        // });
    }

    private void initializeMiscControls() {
        // mPreviewFrameLayout = (PreviewFrameLayout)
        // mRootView.findViewById(R.id.frame);
        // mPreviewFrameLayout.setOnLayoutChangeListener(mActivity);
        mReviewImage = (ImageView) mActivity.findViewById(R.id.review_image);
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.requestFocus();
        mShutterButton.enableTouch(true);
        mGestures.addTouchReceiver(mSnapshotButton);
        mSnapshotButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.takeSnapshot();
            }
        });
        // Back/Front Switcher
        mSwitcherButton = (RotateImageView) mActivity.findViewById(R.id.switcher);
        mSwitcherButton.setVisibility(View.VISIBLE);
        mGestures.addTouchReceiver(mSwitcherButton);
        mSwitcherButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isVideoRecording() || mController.isVideoStarting() || mController.isVideoStopping()) {
                    Log.v(TAG, "swithcer button click cancel while recording");
                    return;
                }
                int cameraId = CameraSettings
                        .readPreferredCameraId(mPreferences);
                mListener.onCameraPickerClicked((cameraId + 1) % 2);
            }
        });
        // ThumbnailView
        mGestures.addTouchReceiver(mVideoThumbnailView);
        mVideoThumbnailView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isVideoRecording()
                        || mController.isVideoStarting()
                        || mController.isVideoStopping()) {
                    Log.v(TAG, "video thumbnail view click cancel while recording");
                    return;
                }
                
                mActivity.gotoVideo(mVideoThumbnail);
            }
        });
    }

    public void updateOnScreenIndicators(Parameters param, ComboPreferences prefs) {
        // mOnScreenIndicators.updateFlashOnScreenIndicator(param.getFlashMode());
        // boolean location = RecordLocationPreference.get(
        // prefs, mActivity.getContentResolver());
        // mOnScreenIndicators.updateLocationIndicator(location);
    }

    public void setAspectRatio(double ratio) {
        // mPreviewFrameLayout.setAspectRatio(ratio);
    }

    public void showTimeLapseUI(boolean enable) {
        // if (mTimeLapseLabel != null) {
        // mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        // }
    }

    // disable preview gestures after shutter is pressed
    public void setShutterPressed(boolean pressed) {
        if (mGestures == null)
            return;
    }

    public void enableShutter(boolean enable) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enable);
        }
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    public void showRecordingUI(boolean recording, boolean zoomSupported) {
        // mOnScreenIndicators.setVisibility(recording ? View.GONE :
        // View.VISIBLE);
        if (recording) {
            mShutterButton.setImageResource(R.drawable.btn_shutter_video_stop);
            mCameraControls.hideTopBottomArc();
            mCameraControls.hideIndicatorPanel();
            // Hide Camera UI
            Util.fadeOut(mSwitcherButton);
            if (!mIsVideoCaptureIntent) {
                Util.fadeOut(mVideoThumbnailView);
                if (PlatformHelper.supportVideoSnapShot(mParameters)) {
                    Util.fadeIn(mSnapshotButton);
                }
            } else {
                Util.fadeOut(mReviewCancelButton);
            }
            showRecordingTimePanel();
            Util.fadeOut(mSettingButton);
            // The camera is not allowed to be accessed in older api levels
            // during
            // recording. It is therefore necessary to hide the zoom UI on older
            // platforms.
            // See the documentation of android.media.MediaRecorder.start() for
            // further explanation.
            if (!ApiHelper.HAS_ZOOM_WHEN_RECORDING && zoomSupported) {
                // disable zoom UI here.
            }
        } else {
            mShutterButton.setImageResource(R.drawable.btn_shutter_video);
            mCameraControls.showTopBottomArc();
            mCameraControls.showIndicatorPanel();
            hideRecordingTimePanel();
            // Recover UI
            Util.fadeIn(mSwitcherButton);
            if (!mIsVideoCaptureIntent) {
                Util.fadeIn(mVideoThumbnailView);
                if (PlatformHelper.supportVideoSnapShot(mParameters)) {
                    Util.fadeOut(mSnapshotButton);
                }
            } else {
                Util.fadeIn(mReviewCancelButton);
            }
            Util.fadeIn(mSettingButton);
            if (!ApiHelper.HAS_ZOOM_WHEN_RECORDING && zoomSupported) {
                // enable zoom UI here.
            }
        }
    }

    public void showReviewImage(Bitmap bitmap) {
        mReviewImage.setImageBitmap(bitmap);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void showReviewUI() {
        Util.fadeIn(mReviewLayout);
        Util.fadeIn(mReviewPlayButton);
        Util.fadeOut(mShutterButton);
        Util.fadeOut(mSwitcherButton);
        Util.fadeIn(mReviewImage);
        Util.fadeOut(mReviewCancelButton);
        Util.fadeIn(mReviewRetakeButton);
        if (mSupportFlash) {
            Util.fadeOut(mFlashButton);
        }
        if (mRecordingPanel != null) {
            Util.fadeOut(mRecordingPanel);
        }
        mGestures.enableZoom(false);
        // mOnScreenIndicators.setVisibility(View.GONE);
    }

    public void hideReviewUI() {
        // mOnScreenIndicators.setVisibility(View.VISIBLE);
        Util.fadeOut(mReviewLayout);
        Util.fadeOut(mReviewImage);
        Util.fadeOut(mReviewPlayButton);
        Util.fadeIn(mShutterButton);
        Util.fadeIn(mSwitcherButton);
        Util.fadeIn(mReviewCancelButton);
        Util.fadeOut(mReviewRetakeButton);
        mGestures.enableZoom(true);
        if (mSupportFlash) {
            Util.fadeIn(mFlashButton);
            mFlashButton.setEnabled(true);
        }
        if (mRecordingPanel != null) {
            Util.fadeIn(mRecordingPanel);
            int color = mActivity.getResources().getColor(R.color.recording_time_elapsed_text);
            setRecordingTimeTextColor(color);
        }
    }

    private void setShowMenu(boolean show) {
        // if (mOnScreenIndicators != null) {
        // mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        // }
    }

    public void onFullScreenChanged(boolean full) {
        // if (mGestures != null) {
        // mGestures.setEnabled(full);
        // }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(full ? View.VISIBLE : View.GONE);
        }
        setShowMenu(full);
    }

    public void initializeZoom(Parameters param) {
        if (param == null || !param.isZoomSupported())
            return;
        mZoomMax = param.getMaxZoom();
        mZoomRatios = param.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomRenderer.setZoomMax(mZoomMax);
        mZoomRenderer.setZoom(param.getZoom(), false);
        mZoomRenderer.setZoomValue(mZoomRatios.get(param.getZoom()));
        mZoomRenderer.setOnZoomChangeListener(mZoomChangeListener);
    }

    public void clickShutter() {
        mShutterButton.performClick();
    }

    public void pressShutter(boolean pressed) {
        mShutterButton.setPressed(pressed);
    }

    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mGestures != null && mRenderOverlay != null) {
            return mGestures.dispatchTouch(m);
        }
        return false;
    }

    public void setRecordingTime(String text) {
        if (mRecordingPanel != null) {
            mRecordingPanel.setText(text);
            String second = text.substring(text.lastIndexOf(":") + 1, text.length());
            boolean visible = Integer.parseInt(second) % 2 == 0;
            if (visible) {
                mRecordingPanel.setIconVisibility(View.VISIBLE);
            } else {
                mRecordingPanel.setIconVisibility(View.INVISIBLE);
            }
        }
    }

    public void setRecordingTimeTextColor(int color) {
        if (mRecordingPanel != null) {
            mRecordingPanel.setTextColor(color);
        }
    }

    @Override
    public boolean hasFaces() {
        return false;
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null)
            indicator.clear();
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mFocusIndicatorView.setFocus(x, y);
    }

    @Override
    public void onFocusStarted(boolean manual) {
        getFocusIndicator().showStart(manual);
    }

    @Override
    public void onFocusSucceeded(boolean timeOut) {
        getFocusIndicator().showSuccess(timeOut);
    }

    @Override
    public void onFocusFailed(boolean timeOut) {
        getFocusIndicator().showFail(timeOut);
    }

    @Override
    public void pauseFaceDetection() {
    }

    @Override
    public void resumeFaceDetection() {
    }

    // private FocusRenderer mFocusRenderer;

    private FocusIndicator getFocusIndicator() {
        return mFocusIndicatorView;
    }

    public void zoomIn(Parameters param) {
        int currentZoom = param.getZoom();
        currentZoom++;
        if (currentZoom <= mZoomMax) {
            int newZoom = mController.onZoomChanged(currentZoom, false);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoomValue(mZoomRatios.get(newZoom));
            }
        }
        mZoomRenderer.setZoom(currentZoom, true);
    }

    public void zoomOut(Parameters param) {
        int currentZoom = param.getZoom();
        currentZoom--;
        Log.d("dyb", "zoom out zoom = " + currentZoom);
        if (currentZoom > 0) {
            int newZoom = mController.onZoomChanged(currentZoom, false);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoomValue(mZoomRatios.get(newZoom));
            }
        }
        mZoomRenderer.setZoom(currentZoom, true);
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            // Log.v("mk", "onZoomValueChanged(), index = " + index);
            int newZoom = mController.onZoomChanged(index, true);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoomValue(mZoomRatios.get(newZoom));
            }
        }

        @Override
        public void onZoomStart() {
        }

        @Override
        public void onZoomEnd() {
        }

    }

    /**
     * Enable or disable the preview thumbnail for click events.
     */
    // public void enablePreviewThumb(boolean enabled) {
    // if (enabled) {
    // // mGestures.addTouchReceiver(mPreviewThumb);
    // mPreviewThumb.setVisibility(View.VISIBLE);
    // } else {
    // // mGestures.removeTouchReceiver(mPreviewThumb);
    // mPreviewThumb.setVisibility(View.GONE);
    // }
    // }

    private RenderOverlay mRenderOverlay;

    public void onCameraOpened(PreferenceGroup prefGroup, ComboPreferences prefs,
            Camera.Parameters params, OnPreferenceChangedListener listener) {
        mParameters = params;
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }
        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer,
                    mActivity);
        }
        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();
    }

    public void initializeFlash(Parameters flash) {
        List<String> supportedFlashMode = flash.getSupportedFlashModes();
        if (supportedFlashMode == null) {
            mSupportFlash = false;
            mFlashButton.setVisibility(View.GONE);
            return;
        }
        for (String s : supportedFlashMode) {
            if (s.equals(Parameters.FLASH_MODE_TORCH)) {
                mSupportFlash = true;
                break;
            } else {
                mSupportFlash = false;
            }
        }
        if (!mSupportFlash) {
            mFlashButton.setVisibility(View.GONE);
        } else { 
            String currentFlashMode = CameraSettings.readFlashMode(mPreferences,
                    CameraActivity.VIDEO_MODULE_INDEX);
            if (currentFlashMode.equals(Parameters.FLASH_MODE_TORCH)) {
                mFlashButton.setImageResource(R.drawable.ic_camera_light_on);
                mFlashMode = VideoController.FLASH_MODE_TORCH;
            } else {
                mFlashButton.setImageResource(R.drawable.ic_camera_light_off);
                mFlashMode = VideoController.FLASH_MODE_OFF;
            }
            mFlashButton.setVisibility(View.VISIBLE);
        }
    }

    class UpdatePhotoThumbnailTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            if (mActivity.isSecureCamera() && !mActivity.isSecurePhotoTaken()) {
                // don't update thumbnail if no photo is taken in secure camera
            } else {
                mPhotoThumbnail = mActivity.getPhotoThumbnail();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (mPhotoThumbnail == null || (mActivity.isSecureCamera() && !mActivity.isSecurePhotoTaken())) {
                mPhotoThumbnailView.setImageResource(R.drawable.ic_camera_lock);
            } else {
                mPhotoThumbnailView.rotateIn(mPhotoThumbnail.getBitmap(), R.drawable.ic_camera_album_photo);
            }
        }
    }

    class UpdateVideoThumbnailTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            if (mActivity.isSecureCamera() && !mActivity.isSecureVideoTaken()) {
                // don't update thumbnail if no video is taken in secure camera
            } else {
                mVideoThumbnail = mActivity.getVideoThumbnail();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (mVideoThumbnail == null || (mActivity.isSecureCamera() && !mActivity.isSecureVideoTaken())) {
                mVideoThumbnailView.setImageResource(R.drawable.ic_camera_lock);
            } else {
                mVideoThumbnailView.rotateIn(mVideoThumbnail.getBitmap(), R.drawable.ic_camera_album_video);
            }
        }
    }

    public void updateVideoThumbnail() {
        UpdateVideoThumbnailTask mTask = new UpdateVideoThumbnailTask();
        mTask.execute();
    }

    public void updatePhotoThumbnail() {
        UpdatePhotoThumbnailTask task = new UpdatePhotoThumbnailTask();
        task.execute();
    }

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    public void animateToModule(int moduleIndex) {
        mCameraControls.animateToModule(moduleIndex, mPreferences, mParameters, this);
        // Recording Panel
        if (mRecordingPanel != null) {
            Util.fadeOut(mRecordingPanel);
            mCameraControls.removeView(mRecordingPanel);
            mRecordingPanel = null;
        }
    }

    @Override
    public void onAnimationEnd() {
        mController.onAnimationEnd();
    }

    public void startCaptureAnimation() {
        mCameraControls.startCaptureAnimation();
    }

    public void showIndicatorPanel() {
        mCameraControls.showIndicatorPanel();
    }

    public void hideIndicatorPanel() {
        mCameraControls.hideIndicatorPanel();
    }

    public void updateFlashButton(String flashmode) {
        if (Parameters.FLASH_MODE_OFF.equals(flashmode)) {
            mFlashMode = VideoController.FLASH_MODE_OFF;
        } else if (Parameters.FLASH_MODE_TORCH.equals(flashmode)) {
            mFlashMode = VideoController.FLASH_MODE_TORCH;
        }
        mCameraControls.updateVideoFlashButton(flashmode);
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    public void enableSnapShotButton(boolean enable) {
        mSnapshotButton.setEnabled(enable);
    }
    
    public void rotateOutThumbnail() {
        mVideoThumbnailView.rotateOut();
    }

    public void hideZoom() {
        mZoomRenderer.hideZoom();
    }

    public void animateBeforeSwitchingCamera(int cameraId) {
    }

    public void animateAfterSwitchingCamera(int cameraId) {
        mFlashButton.setEnabled(true);
    }
    
    public void showSaveUI(){
        Log.v(TAG, "showSaveUI");
    }
    
    public void hideSaveUI(){
        Log.v(TAG, "hideSaveUI");
    }
}
