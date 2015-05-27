package cc.fotoplace.camera;

import java.util.List;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.Parameters;
import android.os.AsyncTask;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import cc.fotoplace.camera.CameraPreference.OnPreferenceChangedListener;
import cc.fotoplace.camera.FocusOverlayManager.FocusUI;
import cc.fotoplace.camera.PreviewGestures.SingleTapListener;
import cc.fotoplace.camera.filters.FiltersManager;
import cc.fotoplace.camera.ui.CameraControls;
import cc.fotoplace.camera.ui.EffectPanel;
import cc.fotoplace.camera.ui.EffectPanel.EffectListener;
import cc.fotoplace.camera.ui.FocusIndicator;
import cc.fotoplace.camera.ui.FocusIndicatorView;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;
import cc.fotoplace.camera.ui.RenderOverlay;
import cc.fotoplace.camera.ui.RotateImageButton;
import cc.fotoplace.camera.ui.RotateImageView;
import cc.fotoplace.camera.ui.ThumbnailLayout;
import cc.fotoplace.camera.ui.ZoomRenderer;

public class PhotoUI implements LocationManager.Listener,
        FaceDetectionListener, FocusUI, SingleTapListener,
        AnimatioinCallback {

    private final static String TAG = "CAM_PhotoUI";
    
    private CameraActivity mActivity;
    private PhotoController mController;
    //private FaceView mFaceView;
    private RenderOverlay mRenderOverlay;
    // private FocusRenderer mFocusRenderer;
    private FocusIndicatorView mFocusIndicator;
    private ZoomRenderer mZoomRenderer;
    private View mRootView;//fragment
    private Object mSurfaceTexture;
    private PreviewGestures mGestures;
    private CameraControls mCameraControls;
    private ShutterButton mShutterButton;
    private RotateImageView mSwitcherButton;
    private RotateImageButton mEffectsButton;
    private Face mBestFace;

    // Zoom
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private ZoomChangeListener mZoomChangeListener = new ZoomChangeListener();
    private View mPreviewThumb;
    private View mReviewLayout;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;
    private View mFlashButton;
    private Bitmap mReviewBitmap;
    private RotateImageView mPhotoSettingButton;
    private ComboPreferences mPreferences;
    private Thumbnail mPhotoThumbnail;
    private Thumbnail mVideoThumbnail;
    private ThumbnailLayout mPhotoThumbnailView;
    private ThumbnailLayout mVideoThumbnailView;
    private OnPreferenceChangedListener mListener;
    private int mFlashMode;

    private boolean mSettingsPopped = false;
    private View mSettingPanel;  //设置panel
    private View mSettingLinesView;
    private View mSettingHDRPanel;
    private View mSettingHDRView;
    private View mSettingBeautyView;
    private View mSettingMoreView;
    private View mLowerControls;
    private ImageView mLinesView;
    //private boolean mIsBeautyOn = false;
    private boolean mIsLinesOn = false;
    private boolean mIsInReviewMode = false;

    private boolean mEffectsPopped = false;
    private EffectPanel mEffectsPanel;
    private boolean mIsImageCaptureIntent = false;
    private boolean mSupportFlash = false;
    private Parameters mParameters;
    private int mOrientation = 0;

    public void setListener(OnPreferenceChangedListener listener) {
        mListener = listener;
    }

    public void setPreferenceGroup(ComboPreferences pref) {
        mPreferences = pref;
    }

    public PhotoUI(CameraActivity activity, PhotoController controller, View parent,
            ComboPreferences prefs) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mPreferences = prefs;
        mIsImageCaptureIntent = Util.isImageCaptureIntent(mActivity.getIntent());
        initViews();
    }

    private void initViews() {
        // Controls from photo_module.xml
        mActivity.getLayoutInflater().inflate(R.layout.photo_module, (ViewGroup) mRootView, true);
        // RenderOverlay
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        //ViewStub faceViewStub = (ViewStub) mRootView.findViewById(R.id.face_view_stub);
        //if (faceViewStub != null) {
            //faceViewStub.inflate();
            //mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
        //}
        mFocusIndicator = (FocusIndicatorView) mRootView.findViewById(R.id.focus_indicator);
        // Controls from camera_controls.xml
        mCameraControls = (CameraControls) mActivity.findViewById(R.id.camera_controls);
        mCameraControls.initViews(CameraActivity.PHOTO_MODULE_INDEX, mPreferences);
        mFlashButton = mCameraControls.getPhotoFlash();
        mPhotoSettingButton = (RotateImageView) mCameraControls.getPhotoSetting();
        mShutterButton = (ShutterButton) mCameraControls.getPhotoShutter();
        mPhotoThumbnailView = (ThumbnailLayout) mCameraControls.getPhotoThumbnailView();
        mVideoThumbnailView = (ThumbnailLayout) mCameraControls.getVideoThumbnailView();
        mEffectsButton = (RotateImageButton) mCameraControls.getEffectsButton();
        mLowerControls = mCameraControls.getLowerControls();
        mLinesView = mCameraControls.getPhotoLines();
    }

    public void initializeControlByIntent() {
        mCameraControls.initilizeControlByIntent(CameraActivity.PHOTO_MODULE_INDEX,
                mActivity.getIntent(), mPreferences);
        if (mIsImageCaptureIntent) {
            mActivity.getLayoutInflater().inflate(R.layout.review_module, mCameraControls);
            mReviewLayout = mCameraControls.findViewById(R.id.review_panel);
            mReviewDoneButton = mCameraControls.findViewById(R.id.btn_done);
            mReviewCancelButton = mCameraControls.findViewById(R.id.btn_cancel);
            mReviewRetakeButton = mCameraControls.findViewById(R.id.btn_retake);
            mReviewCancelButton.setVisibility(View.VISIBLE);
            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureDone();
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureCancelled();
                }
            });
            mReviewRetakeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureRetake();
                }
            });
        }
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setOnShutterButtonListener(mController);
        //if (mEffectsPanel != null)
            //mEffectsPanel.initializeEffectsPanel(mActivity, (EffectListener)mController);
    }

    // called from onResume every other time
    public void initializeSecondTime(Camera.Parameters params) {
        initializeZoom(params);
        if (mEffectsPanel != null)
            mEffectsPanel.updateEffectList();
    }

    // shutter button handling
    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    @Override
    public void onFaceDetection(Face[] faces, Camera arg1) {
        mBestFace = null;
        for (Face face : faces) {
            int score = 0;
            if (face.score > score) {
                mBestFace = face;
            }
        }
        //mFaceView.setFaces(faces);
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) {
        // inherited from com.yunos.camera.LocationManager.Listener.
        // no actions currently.
    }

    @Override
    public void hideGpsOnScreenIndicator() {
        // inherited from com.yunos.camera.LocationManager.Listener.
        // no actions currently.
    }

    public void enablePreviewThumb(boolean enabled) {
        if (enabled) {
            mGestures.addTouchReceiver(mPreviewThumb);
            mPreviewThumb.setVisibility(View.VISIBLE);
        } else {
            mGestures.removeTouchReceiver(mPreviewThumb);
            mPreviewThumb.setVisibility(View.GONE);
        }
    }

    private FocusIndicator getFocusIndicator() {
        return mFocusIndicator;
    }

    public Object getSurfaceTexture() {
        return mSurfaceTexture;
    }

    public void setSurfaceTexture(Object st) {
        mSurfaceTexture = st;
    }

    @Override
    public boolean hasFaces() {
        return false;
    }

    public void clearFaces() {
        //if (mFaceView != null)
            //mFaceView.clear();
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null)
            indicator.clear();
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mFocusIndicator.setFocus(x, y);
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
        //if (mFaceView != null)
            //mFaceView.pause();
    }

    public void onStartFaceDetection(int orientation, boolean mirror) {
        //mFaceView.clear();
        //mFaceView.setVisibility(View.VISIBLE);
        //mFaceView.setDisplayOrientation(orientation);
        //mFaceView.setMirror(mirror);
        //mFaceView.resume();
    }

    @Override
    public void resumeFaceDetection() {
        //if (mFaceView != null)
          //  mFaceView.resume();
    }

    public void onCameraOpened(PreferenceGroup prefGroup, ComboPreferences prefs,
            Camera.Parameters params, OnPreferenceChangedListener listener) {
        // if (mFocusRenderer == null) {
        // mFocusRenderer = new FocusRenderer(mActivity);
        // mRenderOverlay.addRenderer(mFocusRenderer);
        // }
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
        mGestures.setEnabled(false);
        mGestures.reset();
        mGestures.setRenderOverlay(mRenderOverlay);

        // Add Un-clickable Area to block focus action
        mGestures.addUnclickableArea(mLowerControls);
        mGestures.addUnclickableArea(mActivity.findViewById(R.id.upper_block_area));

        // Initialize flash light status
        if (!mIsInReviewMode) {
            initializeFlashButton(params);
        }
        mPreviewThumb = mCameraControls.findViewById(R.id.preview);

        // Initialize thumbnail view
        mGestures.addTouchReceiver(mFlashButton);
        mFlashButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isSnapshotting()) {
                    Log.v(TAG, "flash button click cancel while snapshotting");
                    return;
                }
                mListener.onCameraFlashModeClicked(mFlashMode);
            }
        });

        // Photo Setting Buttons
        mGestures.addTouchReceiver(mPhotoSettingButton);
        mSettingPanel = mRootView.findViewById(R.id.photo_setting_panel);
        mGestures.addTouchReceiver(mSettingPanel);
        mPhotoSettingButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isSnapshotting()) {
                    Log.v(TAG, "setting button click cancel while snapshotting");
                    return;
                }
                
                if (!mSettingsPopped) {
                    Util.fadeIn(mSettingPanel);
                } else {
                    Util.fadeOut(mSettingPanel);
                }
                mSettingsPopped = !mSettingsPopped;
                updatePhotoSettingButton();
            }
        });
        View parent = null;
        mSettingLinesView = mSettingPanel.findViewById(R.id.photo_setting_lines);
        parent = (View) mSettingLinesView.getParent();
        mGestures.addTouchReceiver(parent);
        parent.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isSnapshotting()) {
                    Log.v(TAG, "setting line button click cancel while snapshotting");
                    return;
                }
                mController.onLinesChanged();
            }
        });
        
        
        if (mController.supportHDR()) {
            mSettingHDRView = mSettingPanel.findViewById(R.id.photo_setting_hdr);
            parent = (View) mSettingHDRView.getParent();
            mGestures.addTouchReceiver(parent);
            parent.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mController.isSnapshotting()) {
                        Log.v(TAG,
                                "setting hdr button click cancel while snapshotting");
                        return;
                    }
                    mController.onHDRChanged();
                }
            });
        } else {
            mSettingHDRPanel = mSettingPanel.findViewById(R.id.photo_setting_hdr_panel);
            mSettingHDRPanel.setVisibility(View.GONE);
            mSettingHDRView = null;
        }
        
        mSettingBeautyView = mSettingPanel.findViewById(R.id.photo_setting_beauty);
        parent = (View) mSettingBeautyView.getParent();
        mGestures.addTouchReceiver(parent);
        parent.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isSnapshotting()) {
                    Log.v(TAG, "setting facebeauty button click cancel while snapshotting");
                    return;
                }
                mController.onFaceBeautyChanged();
            }
        });
        mSettingMoreView = mSettingPanel.findViewById(R.id.photo_setting_more);
        parent = (View) mSettingMoreView.getParent();
        if (!mIsImageCaptureIntent) {
            mGestures.addTouchReceiver(parent);
            parent.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mController.isSnapshotting()) {
                        Log.v(TAG, "setting more view button click cancel while snapshotting");
                        return;
                    }
                    mActivity.startActivity(new Intent(mActivity, CameraSettingActivity.class));
                }
            });
        } else {
            parent.setVisibility(View.GONE);
        }

        // Photo Effects Panel
        mEffectsPanel = (EffectPanel) mRootView.findViewById(R.id.photo_effects_panel);
        mGestures.addTouchReceiver(mEffectsPanel);
        FiltersManager.getFiltersDataFromDB(mActivity.getContentResolver());
        
        // Thumbnail View
        mGestures.addTouchReceiver(mPhotoThumbnailView);
        mPhotoThumbnailView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isSnapshotting()) {
                    Log.v(TAG, "photo thumbnai button click cancel while snapshotting");
                    return;
                }
                
                mActivity.gotoImage(mPhotoThumbnail);
            }
        });
        mRenderOverlay.requestLayout();
        // enablePreviewThumb(false);
        mSwitcherButton = (RotateImageView) mCameraControls.findViewById(R.id.switcher);
        if (!mIsInReviewMode) {
            mSwitcherButton.setVisibility(View.VISIBLE);
        }
        mGestures.addTouchReceiver(mSwitcherButton);
        mSwitcherButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mController.isSnapshotting()) {
                    Log.v(TAG, "switcher button click cancel while snapshotting");
                    return;
                }
                
                int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
                mListener.onCameraPickerClicked((cameraId + 1) % 2);
            }
        });
        mGestures.addTouchReceiver(mEffectsButton);
        mEffectsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isSnapshotting()) {
                    Log.v(TAG, "effect button click cancel while snapshotting");
                    return;
                }
                
                hideSettingPanel();
                int color;
                if (!mEffectsPopped) {
                    boolean isValid = mEffectsPanel.initializeEffectsPanel((EffectListener)mController);
                    if (isValid) {
                        Util.fadeIn(mEffectsPanel);
                        color = mActivity.getResources().getColor(R.color.lower_panel_filter_on_color);
                        mLowerControls.setBackgroundColor(color);
                        mEffectsPopped = !mEffectsPopped;
                    }
                } else {
                    Util.fadeOut(mEffectsPanel);
                    color = mActivity.getResources().getColor(R.color.lower_panel_filter_off_color);
                    mLowerControls.setBackgroundColor(color);
                    mEffectsPopped = !mEffectsPopped;
                }
                updateEffectsButton(mEffectsPopped);
            }
        });
        boolean linesOn = mPreferences.getBoolean(CameraSettings.KEY_PHOTO_ALIGNMENT_LINES, false);
        if (linesOn) {
            updateLinesButton(linesOn);
        }
        initializeZoom(params);
        updateThumbnail();
        mGestures.addTouchReceiver(mShutterButton);
    }

    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mGestures != null && mRenderOverlay != null) {
            return mGestures.dispatchTouch(m);
        }
        return false;
    }

    @Override
    public void onSingleTapUp(View v, int x, int y) {
        mController.onSingleTapUp(v, x, y);
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    public boolean collapseCameraControls() {
        // Remove all the popups/dialog boxes
        boolean ret = false;
        return ret;
    }

    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
            Log.d("dyb", "shutter button enabled");
        }
    }

    public void pressShutter(boolean pressed) {
        mShutterButton.setPressed(pressed);
    }

    public void pressShutterButton() {
        if (mShutterButton.isInTouchMode()) {
            mShutterButton.requestFocusFromTouch();
        } else {
            mShutterButton.requestFocus();
        }
        mShutterButton.setPressed(true);
    }

    public void initializeZoom(Parameters param) {
        if (param == null || !param.isZoomSupported())
            return;
        mZoomMax = param.getMaxZoom();
        mZoomRatios = param.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }
        mZoomRenderer.setZoomMax(mZoomMax);
        mZoomRenderer.setZoom(param.getZoom(), false);
        mZoomRenderer.setZoomValue(mZoomRatios.get(param.getZoom()));
        mZoomRenderer.setOnZoomChangeListener(mZoomChangeListener);
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
            int newZoom = mController.onZoomChanged(index, true);
            Log.d("dyb", "on zoom value changed " + newZoom);
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

    public void onOrientationChanged(int orientation) {
        mOrientation = orientation;
        mCameraControls.onOrientationChanged(orientation);
        if (mSettingLinesView != null) {
            ((RotateImageButton) mSettingLinesView).setOrientation(orientation, true);
        }
        if (mSettingHDRView != null) {
            ((RotateImageButton) mSettingHDRView).setOrientation(orientation, true);
        }
        if (mSettingBeautyView != null) {
            ((RotateImageButton) mSettingBeautyView).setOrientation(orientation, true);
        }
        if (mSettingMoreView != null) {
            ((RotateImageButton) mSettingMoreView).setOrientation(orientation, true);
        }
        if (mSettingsPopped) {
            mPhotoSettingButton.setOrientation(0, false);
        } else {
            mPhotoSettingButton.setOrientation(orientation, true);
        }
        if (mEffectsPopped) {
            mEffectsButton.setOrientation(0, false);
        } else {
            mEffectsButton.setOrientation(orientation, true);
        }
        if (mZoomRenderer != null)
            mZoomRenderer.setOrientation(orientation);
    }

    class UpdateThumbnailTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            if (mActivity.isSecureCamera() && !mActivity.isSecurePhotoTaken()) {
                // don't update thumbnail if no photo is taken in secure camera
            } else {
                mPhotoThumbnail = mActivity.getPhotoThumbnail();
            }
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
            if (mPhotoThumbnail == null || (mActivity.isSecureCamera() && !mActivity.isSecurePhotoTaken())) {
                mPhotoThumbnailView.setImageResource(R.drawable.ic_camera_lock);
            } else {
                mPhotoThumbnailView.rotateIn(mPhotoThumbnail.getBitmap(), R.drawable.ic_camera_album_photo);
            }
            if (mVideoThumbnail == null || (mActivity.isSecureCamera() && !mActivity.isSecureVideoTaken())) {
                mVideoThumbnailView.setImageResource(R.drawable.ic_camera_lock);
            } else {
                mVideoThumbnailView.rotateIn(mVideoThumbnail.getBitmap(), R.drawable.ic_camera_album_video);
            }
        }
    }

    public void updateThumbnail() {
        UpdateThumbnailTask mTask = new UpdateThumbnailTask();
        mTask.execute();
    }

    protected void showPostCaptureAlert() {
        mIsInReviewMode = true;
        Util.fadeIn(mReviewLayout);
        Util.fadeOut(mShutterButton);
        Util.fadeOut(mPhotoSettingButton);
        Util.fadeOut(mReviewCancelButton);
        Util.fadeOut(mEffectsButton);
        Util.fadeIn(mReviewRetakeButton);
        mGestures.enableZoom(false);
        if (mSupportFlash) {
            Util.fadeOut(mFlashButton);
        }
        mCameraControls.hideIndicatorPanel();
        Util.fadeOut(mSwitcherButton);
        if (mIsLinesOn)
            Util.fadeOut(mLinesView);
        pauseFaceDetection();
    }

    protected void hidePostCaptureAlert() {
        mIsInReviewMode = false;
        Util.fadeOut(mReviewLayout);
        Util.fadeIn(mShutterButton);
        Util.fadeIn(mPhotoSettingButton);
        Util.fadeIn(mReviewCancelButton);
        Util.fadeOut(mReviewRetakeButton);
        Util.fadeIn(mEffectsButton);
        mGestures.enableZoom(true);
        if (mSupportFlash) {
            Util.fadeIn(mFlashButton);
        }
        mCameraControls.showIndicatorPanel();
        Util.fadeIn(mSwitcherButton);
        if (mIsLinesOn)
            Util.fadeIn(mLinesView);
        resumeFaceDetection();
        if (mReviewBitmap != null && !mReviewBitmap.isRecycled()) {
            mReviewBitmap.recycle();
            mReviewBitmap = null;
        }
        mActivity.getGLRootView().hideReviewBitmap();
    }

    public void startCaptureAnimation() {
        mCameraControls.startCaptureAnimation();
    }

    public void animateToModule(int moduleIndex) {
        Util.fadeOut(mSettingPanel);
        if (mEffectsPopped) {
            Util.fadeOut(mEffectsPanel);
            int color = mActivity.getResources().getColor(R.color.lower_panel_filter_off_color);
            mLowerControls.setBackgroundColor(color);
            mEffectsPanel.clearPanel();
        }
        mCameraControls.animateToModule(moduleIndex, mPreferences, mParameters, this);
    }

    public void updatePhotoSettingButton() {
        if (mSettingsPopped) {
            mPhotoSettingButton.setOrientation(0, false);
        } else {
            mPhotoSettingButton.setOrientation(mOrientation, false);
        }
        mCameraControls.updatePhotoSettingButton(mSettingsPopped);
    }

    public void updateLinesButton(boolean on) {
        if (mIsInReviewMode) {
            return;
        }

        View parentView = (View)mSettingLinesView.getParent();
        TextView tv = (TextView) parentView.findViewById(R.id.camera_lines);
        if (on) {
            ((RotateImageButton) mSettingLinesView)
                    .setImageResource(R.drawable.ic_camera_setting_lines_on);
            tv.setTextColor(mActivity.getResources().getColor(
                    R.color.photo_setting_text_selected_color));

            boolean fullScreen = CameraSettings.getFullScreenPreference(mActivity, mPreferences);
            if (!fullScreen) {
                mLinesView.setImageResource(R.drawable.camera_setting_lines_fullscreen);
            } else {
                mLinesView.setImageResource(R.drawable.camera_setting_lines_screen);
            }
            Util.fadeIn(mLinesView);
        } else {
            ((RotateImageButton) mSettingLinesView)
                    .setImageResource(R.drawable.ic_camera_setting_lines);
            tv.setTextColor(Color.WHITE);
            Util.fadeOut(mLinesView);
        }
        mIsLinesOn = on;
    }
    
    public void updateHDRButton(boolean on) {
        Log.v("mk", "updateBeautyButton(), on = "  + on);
        View parentView = (View)mSettingHDRView.getParent();
        TextView tv = (TextView) parentView.findViewById(R.id.photo_hdr_text);
        
        
        if (on) {
            ((RotateImageButton) mSettingHDRView)
                    .setImageResource(R.drawable.ic_camera_setting_hdr_on);
            tv.setTextColor(mActivity.getResources().getColor(
                    R.color.photo_setting_text_selected_color));
        } else {
            ((RotateImageButton) mSettingHDRView)
                    .setImageResource(R.drawable.ic_camera_setting_hdr);
            tv.setTextColor(Color.WHITE);
        }
        //mIsBeautyOn = on;
    }

    public void updateBeautyButton(boolean on) {
        Log.v("mk", "updateBeautyButton(), on = "  + on);
        View parentView = (View)mSettingBeautyView.getParent();
        TextView tv = (TextView) parentView.findViewById(R.id.photo_beauty_text);
        if (on) {
            ((RotateImageButton) mSettingBeautyView)
                    .setImageResource(R.drawable.ic_camera_setting_beautify_on);
            tv.setTextColor(mActivity.getResources().getColor(
                    R.color.photo_setting_text_selected_color));
        } else {
            ((RotateImageButton) mSettingBeautyView)
                    .setImageResource(R.drawable.ic_camera_setting_beautify);
            tv.setTextColor(Color.WHITE);
        }
        //mIsBeautyOn = on;
    }

    public void updateEffectsButton(boolean on) {
        if (mEffectsPopped) {
            mEffectsButton.setOrientation(0, false);
        } else {
            mEffectsButton.setOrientation(mOrientation, false);
        }
        mCameraControls.updateEffectsButton(on, mEffectsPanel.inEffectMode());
    }

    private void initializeFlashButton(Parameters flash) {
        // flash control
        List<String> supportedFlashMode = flash.getSupportedFlashModes();
        if (supportedFlashMode != null && supportedFlashMode.size() > 1) {
            mSupportFlash = true;
        } else {
            mSupportFlash = false;
        }
        if (!mSupportFlash) {
            mFlashButton.setVisibility(View.GONE);
        } else {
            String currentFlashMode = CameraSettings.readFlashMode(mPreferences,
                    CameraActivity.PHOTO_MODULE_INDEX);
            if (Parameters.FLASH_MODE_OFF.equals(currentFlashMode)) {
                mFlashMode = PhotoController.FLASH_MODE_OFF;
            } else if (Parameters.FLASH_MODE_AUTO.equals(currentFlashMode)) {
                mFlashMode = PhotoController.FLASH_MODE_AUTO;
            } else if (Parameters.FLASH_MODE_ON.equals(currentFlashMode)) {
                mFlashMode = PhotoController.FLASH_MODE_ON;
            }
            mFlashButton.setVisibility(View.VISIBLE);
        }
    }

    public void updateFlashButton(String flashMode) {
        if (Parameters.FLASH_MODE_OFF.equals(flashMode)) {
            mFlashMode = PhotoController.FLASH_MODE_OFF;
        } else if (Parameters.FLASH_MODE_AUTO.equals(flashMode)) {
            mFlashMode = PhotoController.FLASH_MODE_AUTO;
        } else if (Parameters.FLASH_MODE_ON.equals(flashMode)) {
            mFlashMode = PhotoController.FLASH_MODE_ON;
        }
        mCameraControls.updatePhotoFlashButton(flashMode);
    }

    @Override
    public void onAnimationEnd() {
        mController.onAnimationEnd();
    }

    public void animateBeforeSwitchingCamera(int cameraId) {
    }

    public void animateAfterSwitchingCamera(int cameraId) {
        mFlashButton.setEnabled(true);
    }

    public void onFullScreenChanged(boolean full) {
        if (full) {
            mCameraControls.showTopBottomArc();
        } else {
            mCameraControls.hideTopBottomArc();
        }
    }

    public void rotateOutThumbnail() {
        mPhotoThumbnailView.rotateOut();
    }

    public String getCurrentFilterName() {
        return mEffectsPanel.getCurrentFilterName();
    }

    public void hideZoom() {
        mZoomRenderer.hideZoom();
    }

    public void hideSettingPanel() {
        if (mSettingsPopped) {
            mSettingsPopped = false;
            updatePhotoSettingButton();
            Util.fadeOut(mSettingPanel);
        }
    }

    public void hideEffectsPanel() {
        if (mEffectsPopped) {
            mEffectsPopped = false;
            Util.fadeOut(mEffectsPanel);
            int color = mActivity.getResources().getColor(R.color.lower_panel_filter_off_color);
            mLowerControls.setBackgroundColor(color);
            updateEffectsButton(mEffectsPopped);
        }
    }

    public void setReviewBitmap(byte[] data) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        int dW = Util.displayWidth;
        int dH = Util.displayHeight;
        int iW = opts.outWidth;
        int iH = opts.outHeight;
        opts.inJustDecodeBounds = false;
        if (iW > iH) {
            opts.inSampleSize = Math.max((int)(iW/dH), (int)(iH/dW));
        } else {
            opts.inSampleSize = Math.min((int)(iW/dW), (int)(iH/dH));
        }
        mReviewBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        mActivity.getGLRootView().showReviewBitmap(mReviewBitmap);
    }

}
