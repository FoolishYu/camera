package cc.fotoplace.camera;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import cc.fotoplace.camera.CameraPreference.OnPreferenceChangedListener;
import cc.fotoplace.camera.PreviewGestures.SingleTapListener;
import cc.fotoplace.camera.ui.CameraControls;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;
import cc.fotoplace.camera.ui.PanoramaIndicator;
import cc.fotoplace.camera.ui.RenderOverlay;
import cc.fotoplace.camera.ui.RotateImageButton;
import cc.fotoplace.camera.ui.ThumbnailLayout;

public class PanoramaUI implements LocationManager.Listener,
SingleTapListener, AnimatioinCallback {

    private CameraActivity mActivity;
    private PanoramaController mController;
    private RenderOverlay mRenderOverlay;
    //private FocusRenderer mFocusRenderer;
    private View mRootView;
    private Object mSurfaceTexture;
    private PreviewGestures mGestures;
    private CameraControls mCameraControls;
    private ShutterButton mShutterButton;
    private ComboPreferences mPreferences;
    private Thumbnail mThumbnail;
    private ThumbnailLayout mThumbnailView;
    private PanoramaIndicator mPanoramaIndicator;
    private Bitmap indicatorImageBitmap;
    private Parameters mParameters;

    public PanoramaUI(CameraActivity activity, PanoramaController controller, View parent, ComboPreferences pref) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mPreferences = pref;
        initViews();
    }

    private void initViews() {
        // Controls from panorama_module.xml
        mActivity.getLayoutInflater().inflate(R.layout.panorama_module, (ViewGroup) mRootView, true);
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mPanoramaIndicator = (PanoramaIndicator) mRootView.findViewById(R.id.panorama_indicator);

        // Controls from camera_controls.xml
        mCameraControls = (CameraControls) mActivity.findViewById(R.id.camera_controls);
        mCameraControls.initViews(CameraActivity.PANORAMA_MODULE_INDEX, mPreferences);
        mThumbnailView = (ThumbnailLayout) mCameraControls.getPhotoThumbnailView();
        mShutterButton = (ShutterButton) mCameraControls.getPhotoShutter();
    }

    public View getRootView() {
        return mRootView;
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
        enableShutter(true);
    }

    // shutter button handling
    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) {
    }

    @Override
    public void hideGpsOnScreenIndicator() {
    }

    public Object getSurfaceTexture() {
        return mSurfaceTexture;
    }

    public void setSurfaceTexture(Object st) {
        mSurfaceTexture = st;
    }

    public void onCameraOpened(ComboPreferences prefs,
            Camera.Parameters params, OnPreferenceChangedListener listener) {
        //        if (mFocusRenderer == null) {
        //            mFocusRenderer = new FocusRenderer(mActivity);
        //            mRenderOverlay.addRenderer(mFocusRenderer);
        //        }
        mParameters = params;
        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, null,
                    mActivity);
        }
        mGestures.reset();
        mGestures.setRenderOverlay(mRenderOverlay);

        // add blocker
        mGestures.addUnclickableArea(mActivity.findViewById(R.id.lower_controls));
        mGestures.addUnclickableArea(mActivity.findViewById(R.id.upper_block_area));

        mRenderOverlay.requestLayout();
        mGestures.addTouchReceiver(mThumbnailView);
        mThumbnailView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.gotoImage(mThumbnail);
            }
        });
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

    public void onOrientationChanged(int orientation) {
        mCameraControls.onOrientationChanged(orientation);
        ((RotateImageButton) mCameraControls.getPhotoSetting()).setOrientation(orientation, true);
        ((RotateImageButton) mCameraControls.getEffectsButton()).setOrientation(orientation, true);
    }

    class UpdateThumbnailTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            if (mActivity.isSecureCamera() && !mActivity.isSecurePhotoTaken()) {
                // do nothing
            } else {
                mThumbnail = mActivity.getPhotoThumbnail();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (mThumbnail == null || (mActivity.isSecureCamera() && !mActivity.isSecurePhotoTaken())) {
                mThumbnailView.setImageResource(R.drawable.ic_camera_lock);
            } else {
                mThumbnailView.rotateIn(mThumbnail.getBitmap(), R.drawable.ic_camera_album_photo);
            }
        }
    }

    public void updateThumbnail() {
        UpdateThumbnailTask mTask = new UpdateThumbnailTask();
        mTask.execute();
    }

    public void setOffset(int x, int y, int speed) {
        mPanoramaIndicator.setOffset(x, y, speed);
    }

    public void setMosaicBarLayout(Size previewSize) {
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int rectWidth = dm.widthPixels / PanoramaModule.MOSAIC_PAGE_NUM;
        int rectHeight = rectWidth * previewSize.width / previewSize.height;
        mPanoramaIndicator.setSize(rectWidth, rectHeight);
        mPanoramaIndicator.setVisibility(View.VISIBLE);
        MosaicNativeInterface.nativeInitThumbnail(dm.widthPixels, rectHeight);
        Log.d("dyb_pano", "init thumbnail");
        if (indicatorImageBitmap == null) {
            indicatorImageBitmap = Bitmap
                    .createBitmap(dm.widthPixels, rectHeight, Config.ARGB_8888);
            mPanoramaIndicator.setThumbnailBitmap(indicatorImageBitmap);
        }
        mPanoramaIndicator.showStart();
    }

    public Bitmap getBitmap() {
        return indicatorImageBitmap;
    }

    public void cleanIndicatorBitmap() {
        mPanoramaIndicator.setThumbnailBitmap(null);
        if (indicatorImageBitmap != null) {
            indicatorImageBitmap.recycle();
            indicatorImageBitmap = null;
        }
    }

    public void setDrawingWidth(int width) {
        mPanoramaIndicator.setDrawingWidth(width);
    }

    public void showMosaicingUI() {
        mShutterButton.setImageResource(R.drawable.btn_shutter_panorama_stop);
        Util.fadeOut(mThumbnailView);
        mCameraControls.hideIndicatorPanel();
        mCameraControls.hideTopBottomArc();
    }

    public void hideMosaicingUI() {
        mShutterButton.setImageResource(R.drawable.btn_shutter_panorama);
        Util.fadeIn(mThumbnailView);
        mCameraControls.showIndicatorPanel();
        mCameraControls.showTopBottomArc();
//        setOffset(0, 0, 0);
        mPanoramaIndicator.showStart();
    }
    
    public void animateToModule(int moduleIndex) {
        mPanoramaIndicator.setVisibility(View.GONE);
        mCameraControls.animateToModule(moduleIndex, mPreferences, mParameters, this);
    }

    @Override
    public void onAnimationEnd() {
        mController.onAnimationEnd();
    }

    public void rotateOutThumbnail() {
        mThumbnailView.rotateOut();
    }
}
