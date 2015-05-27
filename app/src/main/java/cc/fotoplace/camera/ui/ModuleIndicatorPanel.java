package cc.fotoplace.camera.ui;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.LinearLayout;
import android.widget.TextView;
import cc.fotoplace.camera.CameraActivity;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.Util;

public class ModuleIndicatorPanel extends LinearLayout implements AnimatorListener {

    public interface AnimatioinCallback {
        void onAnimationEnd();
    }

    private TextView mVideoText;
    private TextView mPhotoText;
    private TextView mScanText;
    private TextView mPanoText;

    private AnimatorSet mAnimatorSet;
    private int mCurrentModuleIndex = CameraActivity.UNKNOWN_MODULE_INDEX;
    private static int TXT_SIZE_CENTER = 12;
    private static int TXT_SIZE_OTHERS = 10;

    private int X_STEP;
    private int X_OFFSET;
    private int SCREEN_WIDTH;

    private static int TXT_COLOR_CENTER = 0;;
    private static final int TXT_COLOR_OTHERS = Color.WHITE;
    private AnimatioinCallback mCallback;

    private static int TXT_COLOR_CENTER_R = Color.red(TXT_COLOR_CENTER);
    private static int TXT_COLOR_CENTER_G = Color.green(TXT_COLOR_CENTER);
    private static int TXT_COLOR_CENTER_B = Color.blue(TXT_COLOR_CENTER);

    private static final int TXT_COLOR_OTHERS_R = Color.red(TXT_COLOR_OTHERS);
    private static final int TXT_COLOR_OTHERS_G = Color.green(TXT_COLOR_OTHERS);
    private static final int TXT_COLOR_OTHERS_B = Color.blue(TXT_COLOR_OTHERS);

    private Interpolator mInterpolator = new AccelerateDecelerateInterpolator(); 

    private Context mContext;

    public ModuleIndicatorPanel(Context context) {
        super(context);
        init(context);
    }

    public ModuleIndicatorPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ModuleIndicatorPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        Resources resource = mContext.getResources();
        X_STEP = (int) resource.getDimensionPixelSize(R.dimen.module_indicator_width);
        X_OFFSET = X_STEP/2;
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(dm);
        SCREEN_WIDTH = dm.widthPixels;
        TXT_COLOR_CENTER = context.getResources().getColor(R.color.module_indicator_panel_color);
        TXT_COLOR_CENTER_R = Color.red(TXT_COLOR_CENTER);
        TXT_COLOR_CENTER_G = Color.green(TXT_COLOR_CENTER);
        TXT_COLOR_CENTER_B = Color.blue(TXT_COLOR_CENTER);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mVideoText = (TextView) findViewById(R.id.video_guide);
        mPhotoText = (TextView) findViewById(R.id.photo_guide);
        mScanText = (TextView) findViewById(R.id.scan_guide);
        mPanoText = (TextView) findViewById(R.id.pano_guide);
        if (Util.isImageCaptureIntent(((Activity)mContext).getIntent())) {
            mPhotoText.setVisibility(View.VISIBLE);
            mVideoText.setVisibility(View.GONE);
            mScanText.setVisibility(View.GONE);
            mPanoText.setVisibility(View.GONE);
            mPhotoText.setTextSize(TXT_SIZE_CENTER);
            mPhotoText.setTextColor(TXT_COLOR_CENTER);
            return;
        }
        if (Util.isVideoCaptureIntent(((Activity)mContext).getIntent())) {
            mPhotoText.setVisibility(View.GONE);
            mVideoText.setVisibility(View.VISIBLE);
            mScanText.setVisibility(View.GONE);
            mPanoText.setVisibility(View.GONE);
            mVideoText.setTextSize(TXT_SIZE_CENTER);
            mVideoText.setTextColor(TXT_COLOR_CENTER);
            return;
        }
    
        if (Util.isVideoCameraIntent(((Activity)mContext).getIntent())) {
            mCurrentModuleIndex = CameraActivity.VIDEO_MODULE_INDEX;
            mVideoText.setTextSize(TXT_SIZE_CENTER);
            mVideoText.setTextColor(TXT_COLOR_CENTER);
            mPhotoText.setTextSize(TXT_SIZE_OTHERS);
            mPhotoText.setTextColor(TXT_COLOR_OTHERS);
            mScanText.setTextSize(TXT_SIZE_OTHERS);
            mScanText.setTextColor(TXT_COLOR_OTHERS);
            mPanoText.setTextSize(TXT_SIZE_OTHERS);
            mPanoText.setTextColor(TXT_COLOR_OTHERS);
            setX(X_OFFSET + X_STEP);
        } else {
            mCurrentModuleIndex = CameraActivity.PHOTO_MODULE_INDEX;
            mVideoText.setTextSize(TXT_SIZE_OTHERS);
            mVideoText.setTextColor(TXT_COLOR_OTHERS);
            mPhotoText.setTextSize(TXT_SIZE_CENTER);
            mPhotoText.setTextColor(TXT_COLOR_CENTER);
            mScanText.setTextSize(TXT_SIZE_OTHERS);
            mScanText.setTextColor(TXT_COLOR_OTHERS);
            mPanoText.setTextSize(TXT_SIZE_OTHERS);
            mPanoText.setTextColor(TXT_COLOR_OTHERS);
            setX(X_OFFSET);
        }
    }

    public void setAnimationCallback(AnimatioinCallback callback) {
        mCallback = callback;
    }

    public void setSelected(int index) {
        Log.v("mk", "setSelected() --------------------- index = " + index);
        if (mAnimatorSet == null) {
            mAnimatorSet = new AnimatorSet();
            mAnimatorSet.setDuration(CameraActivity.ANIMATION_DURATION);
            mAnimatorSet.addListener(this);
        }
        updateTextSizeAnimator(index);
        // updateTextAlphaAnimator(index);
        updateLayoutPositionAnimator(index);
        updateTextColorAnimation(index);
        mAnimatorSet.playTogether(new Animator[] {
                mVideoTextSizeAnimator,
                mPhotoTextSizeAnimator,
                mScanTextSizeAnimator,
                mPanoTextSizeAnimator,
                mLayoutPositionAnimator
        });
        mAnimatorSet.setInterpolator(mInterpolator);
        mAnimatorSet.start();
        if (mW2GAnimation != null) mW2GAnimation.start();
        if (mG2WAnimation != null) mG2WAnimation.start();
        mCurrentModuleIndex = index;
    }

    ObjectAnimator mVideoTextSizeAnimator;
    ObjectAnimator mPhotoTextSizeAnimator;
    ObjectAnimator mScanTextSizeAnimator;
    ObjectAnimator mPanoTextSizeAnimator;
    ObjectAnimator mLayoutPositionAnimator;
    WhiteToGreenAnimation mW2GAnimation;
    GreenToWhiteAnimation mG2WAnimation;

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        mCallback.onAnimationEnd();
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    @Override
    public void onAnimationStart(Animator animation) {
        setAlpha(1F);
        mVideoText.setVisibility(View.VISIBLE);
        mPhotoText.setVisibility(View.VISIBLE);
        mScanText.setVisibility(View.VISIBLE);
        mPanoText.setVisibility(View.VISIBLE);
        invalidate();
    }

    private void updateLayoutPositionAnimator(int index) {
        mLayoutPositionAnimator = ObjectAnimator.ofFloat(this, "X", getX(), SCREEN_WIDTH / 2
                - index * X_STEP - X_OFFSET);
    }

    private void updateTextColorAnimation(int index) {
        if (mW2GAnimation == null) {
            mW2GAnimation = new WhiteToGreenAnimation();
        }
        if (mG2WAnimation == null) {
            mG2WAnimation = new GreenToWhiteAnimation();
        }
        switch (mCurrentModuleIndex) {
            case CameraActivity.VIDEO_MODULE_INDEX:
                mG2WAnimation.setTextView(mVideoText);
                if (mCurrentModuleIndex > index) { // Move to left Module, impossible
                } else {    // Video to Photo Module
                    mW2GAnimation.setTextView(mPhotoText);
                }
                break;
            case CameraActivity.PHOTO_MODULE_INDEX:
                mG2WAnimation.setTextView(mPhotoText);
                if (mCurrentModuleIndex > index) { // Photo to Video Module 
                    mW2GAnimation.setTextView(mVideoText);
                } else {    // Photo to Scan Module
                    mW2GAnimation.setTextView(mScanText);
                }
                break;
            case CameraActivity.SCANNER_MODULE_INDEX:
                mG2WAnimation.setTextView(mScanText);
                if (mCurrentModuleIndex > index) { // Scan to Photo Module
                    mW2GAnimation.setTextView(mPhotoText);
                } else {    // Scan to Pano Module
                    mW2GAnimation.setTextView(mPanoText);
                }
                break;
            case CameraActivity.PANORAMA_MODULE_INDEX:
                mG2WAnimation.setTextView(mPanoText);
                if (mCurrentModuleIndex > index) { // Pano to Scan Module
                    mW2GAnimation.setTextView(mScanText);
                } else {    // Move to right Module, impossible
                }
                break;
            default:
                break;
        }
    }

    private void updateTextSizeAnimator(int index) {
        switch (mCurrentModuleIndex) {
            case CameraActivity.VIDEO_MODULE_INDEX:
                mVideoTextSizeAnimator = ObjectAnimator.ofFloat(mVideoText, "TextSize", TXT_SIZE_CENTER, TXT_SIZE_OTHERS);
                if (mCurrentModuleIndex > index) { // Move to left Module, impossible
                } else {    // Video to Photo Module
                    mPhotoTextSizeAnimator = ObjectAnimator.ofFloat(mPhotoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_CENTER);
                    mScanTextSizeAnimator = ObjectAnimator.ofFloat(mScanText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mPanoTextSizeAnimator = ObjectAnimator.ofFloat(mPanoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                }
                break;
            case CameraActivity.PHOTO_MODULE_INDEX:
                mPhotoTextSizeAnimator = ObjectAnimator.ofFloat(mPhotoText, "TextSize", TXT_SIZE_CENTER, TXT_SIZE_OTHERS);
                if (mCurrentModuleIndex > index) { // 
                    mVideoTextSizeAnimator = ObjectAnimator.ofFloat(mVideoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_CENTER);
                    mScanTextSizeAnimator = ObjectAnimator.ofFloat(mScanText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mPanoTextSizeAnimator = ObjectAnimator.ofFloat(mPanoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                } else {    // Photo to Scan Module
                    mVideoTextSizeAnimator = ObjectAnimator.ofFloat(mVideoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mScanTextSizeAnimator = ObjectAnimator.ofFloat(mScanText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_CENTER);
                    mPanoTextSizeAnimator = ObjectAnimator.ofFloat(mPanoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                }
                break;
            case CameraActivity.SCANNER_MODULE_INDEX:
                mScanTextSizeAnimator = ObjectAnimator.ofFloat(mScanText, "TextSize", TXT_SIZE_CENTER, TXT_SIZE_OTHERS);
                if (mCurrentModuleIndex > index) { // Scan to Photo Module
                    mVideoTextSizeAnimator = ObjectAnimator.ofFloat(mVideoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mPhotoTextSizeAnimator = ObjectAnimator.ofFloat(mPhotoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_CENTER);
                    mPanoTextSizeAnimator = ObjectAnimator.ofFloat(mPanoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                } else {    // Scan to Pano Module
                    mVideoTextSizeAnimator = ObjectAnimator.ofFloat(mVideoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mPhotoTextSizeAnimator = ObjectAnimator.ofFloat(mPhotoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mPanoTextSizeAnimator = ObjectAnimator.ofFloat(mPanoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_CENTER);
                }
                break;
            case CameraActivity.PANORAMA_MODULE_INDEX:
                mPanoTextSizeAnimator = ObjectAnimator.ofFloat(mPanoText, "TextSize", TXT_SIZE_CENTER, TXT_SIZE_OTHERS);
                if (mCurrentModuleIndex > index) { // Pano to Scan Module
                    mVideoTextSizeAnimator = ObjectAnimator.ofFloat(mPanoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mPhotoTextSizeAnimator = ObjectAnimator.ofFloat(mPhotoText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_OTHERS);
                    mScanTextSizeAnimator = ObjectAnimator.ofFloat(mScanText, "TextSize", TXT_SIZE_OTHERS, TXT_SIZE_CENTER);
                } else {    // Move to right Module, impossible
                }
                break;
            default:
                break;
        }
    }

    private class WhiteToGreenAnimation extends Animation {

        private TextView mView;

        public void setTextView(TextView textview) {
            mView = textview;
            mView.setAnimation(this);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            float a = interpolatedTime;
            int r = (int)(a * TXT_COLOR_CENTER_R) + (int)((1-a) * TXT_COLOR_OTHERS_R);
            int g = (int)(a * TXT_COLOR_CENTER_G) + (int)((1-a) * TXT_COLOR_OTHERS_G);
            int b = (int)(a * TXT_COLOR_CENTER_B) + (int)((1-a) * TXT_COLOR_OTHERS_B);
            int color = Color.rgb(r, g, b);
            mView.setTextColor(color);
        }
    }

    private class GreenToWhiteAnimation extends Animation {

        private TextView mView;

        public void setTextView(TextView textview) {
            mView = textview;
            mView.setAnimation(this);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            float a = interpolatedTime;
            int r = (int)(a * TXT_COLOR_OTHERS_R) + (int)((1-a) * TXT_COLOR_CENTER_R);
            int g = (int)(a * TXT_COLOR_OTHERS_G) + (int)((1-a) * TXT_COLOR_CENTER_G);
            int b = (int)(a * TXT_COLOR_OTHERS_B) + (int)((1-a) * TXT_COLOR_CENTER_B);
            int color = Color.rgb(r, g, b);
            mView.setTextColor(color);
        }
    }

}
