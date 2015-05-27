package cc.fotoplace.camera.ui;

import static cc.fotoplace.camera.CameraActivity.PANORAMA_MODULE_INDEX;
import static cc.fotoplace.camera.CameraActivity.PHOTO_MODULE_INDEX;
import static cc.fotoplace.camera.CameraActivity.SCANNER_MODULE_INDEX;
import static cc.fotoplace.camera.CameraActivity.UNKNOWN_MODULE_INDEX;
import static cc.fotoplace.camera.CameraActivity.VIDEO_MODULE_INDEX;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera.Parameters;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import cc.fotoplace.camera.CameraActivity;
import cc.fotoplace.camera.ComboPreferences;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.ShutterButton;
import cc.fotoplace.camera.Util;
import cc.fotoplace.camera.animation.CaptureAnimation;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;

public class CameraControls extends RelativeLayout {

    private static final String TAG = "CAM_Controls";

    private int mCurrentModuleIndex = UNKNOWN_MODULE_INDEX;

    private Context mContext;

    private View mIndicator;
    private View mSwitcher;
    private ThumbnailLayout mPhotoThumbnailView;
    private ThumbnailLayout mVideoThumbnailView;
    private ImageView mLowerControls;
    private ModuleIndicatorPanel mIndicatorPanel;
    private ShutterButton mPhotoShutter;
    private ShutterButton mVideoShutter;
    private RotateImageView mPhotoFlash;
    private RotateImageButton mVideoFlash;
    private RotateImageButton mEffectsButton;
    private RotateImageButton mPhotoSetting;
    private RotateImageButton mVideoSetting;
    private ImageView mPhotoLines;

    private boolean mIsImageCaptureIntent = false;
    private boolean mIsVideoCaptureIntent = false;
    private boolean mIsVideoCameraIntent = false;
   // private boolean mIsScanCameraIntent = false;

    private SparseArray<ModuleActor> mActorArray = new SparseArray<ModuleActor>();

    private CaptureAnimation mCaptureAnimation;

    private ModuleActor createModuleActor(int moduleIndex) {
        ModuleActor actor = null;
        switch (moduleIndex) {
            case VIDEO_MODULE_INDEX:
                actor = new VideoModuleActor(mContext, this);
                break;
            case PHOTO_MODULE_INDEX:
                actor = new PhotoModuleActor(mContext, this);
                break;
            case SCANNER_MODULE_INDEX:
                actor = new PhotoModuleActor(mContext, this);
                break;
            case PANORAMA_MODULE_INDEX:
                actor = new PanoramaModuleActor(mContext, this); 
                break;
            default:
                throw new IllegalArgumentException("Out of module index");
        }
        return actor;
    }

    public CameraControls(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CameraControls(Context context) {
        super(context);
        init(context);
    }

    public CameraControls(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mIndicator = findViewById(R.id.indicator);
        mSwitcher = findViewById(R.id.switcher);
        mIndicatorPanel = (ModuleIndicatorPanel) findViewById(R.id.moduleguide);
        mPhotoThumbnailView = (ThumbnailLayout) findViewById(R.id.photo_thumbnail_layout);
        mVideoThumbnailView = (ThumbnailLayout) findViewById(R.id.video_thumbnail_layout);
        mPhotoShutter = (ShutterButton) findViewById(R.id.photo_shutter);
        mVideoShutter = (ShutterButton) findViewById(R.id.video_shutter);
        mLowerControls = (ImageView) findViewById(R.id.lower_controls);
        mPhotoFlash = (RotateImageButton) findViewById(R.id.photo_flash);
        mVideoFlash = (RotateImageButton) findViewById(R.id.video_flash);
        mEffectsButton = (RotateImageButton) findViewById(R.id.effect);
        mPhotoSetting = (RotateImageButton) findViewById(R.id.photo_setting);
        mVideoSetting = (RotateImageButton) findViewById(R.id.video_setting);
        mPhotoLines = (ImageView) findViewById(R.id.lines);
        // Parse Intent
        mIsImageCaptureIntent = Util.isImageCaptureIntent(((Activity) mContext).getIntent());
        mIsVideoCaptureIntent = Util.isVideoCaptureIntent(((Activity) mContext).getIntent());
        mIsVideoCameraIntent = Util.isVideoCameraIntent(((Activity) mContext).getIntent());
        if (mIsImageCaptureIntent || mIsVideoCaptureIntent ) {
            mIndicator.setVisibility(GONE);
        }
        // set default module index
        if (mIsVideoCameraIntent) {
            mCurrentModuleIndex = CameraActivity.VIDEO_MODULE_INDEX;
        } else {
            mCurrentModuleIndex = CameraActivity.PHOTO_MODULE_INDEX;
        }
//        if (mIsScanCameraIntent) {
//            mPhotoSetting.setVisibility(View.GONE);
//            mPhotoFlash.setVisibility(View.GONE);
//            mVideoFlash.setVisibility(View.VISIBLE);
//            mLowerControls.setVisibility(View.GONE);
//        }
        ModuleActor currentAcor = mActorArray.get(mCurrentModuleIndex); 
        if (currentAcor == null) {
            currentAcor = createModuleActor(Integer.valueOf(mCurrentModuleIndex));
            mActorArray.put(mCurrentModuleIndex, currentAcor);
        }
    }

    public void onOrientationChanged(int orientation) {
        ((RotateImageView)mSwitcher).setOrientation(orientation, true);
        mPhotoThumbnailView.setOrientation(orientation, true);
        mVideoThumbnailView.setOrientation(orientation, true);
        mPhotoShutter.setOrientation(orientation, true);
        mVideoShutter.setOrientation(orientation, true);
        mPhotoFlash.setOrientation(orientation, true);
        mVideoFlash.setOrientation(orientation, true);
        //mEffectsButton.setOrientation(orientation, true);
        //mPhotoSetting.setOrientation(orientation, true);
        mVideoSetting.setOrientation(orientation, true);
        RotateImageView cancel = (RotateImageView) findViewById(R.id.btn_cancel);
        if (cancel != null) {
            cancel.setOrientation(orientation, true);
        }
        RotateImageView done = (RotateImageView) findViewById(R.id.btn_done);
        if (done != null) {
            done.setOrientation(orientation, true);
        }
        RotateImageView retake = (RotateImageView) findViewById(R.id.btn_retake);
        if (retake != null) {
            retake.setOrientation(orientation, true);
        }
    }

    public void setIndicator(int index) {
        mIndicatorPanel.setSelected(index);
    }

    public void startCaptureAnimation() {
        if (mCaptureAnimation == null) {
            mCaptureAnimation = new CaptureAnimation(this);
            setAnimation(mCaptureAnimation);
        }
        startAnimation(mCaptureAnimation);
    }

    public void showIndicatorPanel() {
        Util.fadeIn(mIndicatorPanel);
        if (!mIsImageCaptureIntent && !mIsVideoCaptureIntent) {
            Util.fadeIn(mIndicator);
        }
    }

    public void hideIndicatorPanel() {
        Util.fadeOut(mIndicatorPanel);
        if (!mIsImageCaptureIntent && !mIsVideoCaptureIntent) {
            Util.fadeOut(mIndicator);
        }
    }

    public void initilizeControlByIntent(int moduleIndex, Intent intent, ComboPreferences prefs) {
        ModuleActor currentActor = mActorArray.get(moduleIndex);
        if (currentActor == null) {
            currentActor = createModuleActor(moduleIndex);
            mActorArray.put(moduleIndex, currentActor);
        }
        currentActor.initializeControlsByIntent(intent, prefs);
    }

    public void initViews(int moduleIndex, ComboPreferences pref) {
        ModuleActor currentActor = mActorArray.get(moduleIndex);
        if (currentActor == null) {
            currentActor = createModuleActor(mCurrentModuleIndex);
            mActorArray.put(mCurrentModuleIndex, currentActor);
        }
        currentActor.initializeViews(pref);
    }

    public void animateToModule(int moduleIndex, ComboPreferences pref, Parameters params, AnimatioinCallback callback) {
        if (Math.abs(mCurrentModuleIndex - moduleIndex) != 1) return;
        ModuleActor currentActor = mActorArray.get(mCurrentModuleIndex);
        if (currentActor == null) {
            currentActor = createModuleActor(mCurrentModuleIndex);
            mActorArray.put(mCurrentModuleIndex, currentActor);
        }
        if (moduleIndex < mCurrentModuleIndex) {
            mCurrentModuleIndex = moduleIndex;
            currentActor.animateToPrevModule(moduleIndex, pref, params, callback);
        } else {
            mCurrentModuleIndex = moduleIndex;
            currentActor.animateToNextModule(moduleIndex, pref, params, callback);
        }
    };

    public void updatePhotoSettingButton(boolean popped) {
        if (popped) {
            mPhotoSetting.setImageResource(R.drawable.ic_camera_setting_back);
        } else {
            mPhotoSetting.setImageResource(R.drawable.ic_camera_setting);
        }
    }

    public void updatePhotoFlashButton(String flashMode) {
        if (Parameters.FLASH_MODE_AUTO.equals(flashMode)) {
            mPhotoFlash.setImageResource(R.drawable.ic_camera_flash_auto);
        } else if (Parameters.FLASH_MODE_ON.equals(flashMode)) {
            mPhotoFlash.setImageResource(R.drawable.ic_camera_flash_on);
        } else if (Parameters.FLASH_MODE_OFF.equals(flashMode)) {
            mPhotoFlash.setImageResource(R.drawable.ic_camera_flash_off);
        }
    }

    public void updateVideoFlashButton(String flashmode) {
        if (Parameters.FLASH_MODE_OFF.equals(flashmode)) {
            mVideoFlash.setImageResource(R.drawable.ic_camera_light_off);
        } else {
            mVideoFlash.setImageResource(R.drawable.ic_camera_light_on);
        }
    }

    public void updateEffectsButton(boolean on, boolean selected) {
        if (on) {
            mEffectsButton.setImageResource(R.drawable.ic_camera_beautify_back);
        } else {
            if (selected) {
                mEffectsButton.setImageResource(R.drawable.ic_camera_beautify_on);
            } else {
                mEffectsButton.setImageResource(R.drawable.ic_camera_beautify_off);
            }
        }
    }

    public void hideTopBottomArc() {
        Util.alphaOut(mLowerControls);
    }

    public void showTopBottomArc() {
        Util.alphaIn(mLowerControls);
    }

    @Override
    protected void onDetachedFromWindow() {
        for (int i = 0; i < CameraActivity.MODULE_NUM; i++) {
            ModuleActor actor = mActorArray.valueAt(i);
            if (actor != null) actor = null;
        }
        mActorArray.clear();
        super.onDetachedFromWindow();
    }

    // Getters
    public View getModuleIndicator() {
        return mIndicator;
    }

    public View getSwitcher() {
        return mSwitcher;
    }

    public View getVideoThumbnailView() {
        return mVideoThumbnailView;
    }

    public View getPhotoThumbnailView() {
        return mPhotoThumbnailView;
    }

    public View getLowerControls() {
        return mLowerControls;
    }

    public View getModuleIndicatorPanel() {
        return mIndicatorPanel;
    }

    public View getPhotoShutter() {
        return mPhotoShutter;
    }

    public View getVideoShutter() {
        return mVideoShutter;
    }

    public View getPhotoFlash() {
        return mPhotoFlash;
    }

    public View getVideoFlash() {
        return mVideoFlash;
    }

    public View getPhotoSetting() {
        return mPhotoSetting;
    }

    public View getVideoSetting() {
        return mVideoSetting;
    }

    public View getEffectsButton() {
        return mEffectsButton;
    }

    public ImageView getPhotoLines() {
        return mPhotoLines;
    }

}
