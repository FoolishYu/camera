package cc.fotoplace.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import cc.fotoplace.camera.R;


//图片预览控件 两个图片重叠效果
public class ThumbnailLayout extends RelativeLayout{

    private ImageView currentImageView;
    private ImageView lastImageView;
    private Context mContext;

    private boolean hasRotateout = false;

    private static final int ANIMATION_SPEED = 270; // 270 deg/sec
    private static final int angle = 15;
    private int mCurrentDegree = 0; // [0, 359]
    private int mStartDegree = 0;
    private int mTargetDegree = 0;
    private int mAnimationDegree = 0;

    private boolean mClockwise = false, mEnableAnimation = true;

    private long mAnimationStartTime = 0;
    private long mAnimationEndTime = 0;

    private int mRes;
    private Bitmap lastBitmap;
    private Bitmap currentBitmap;

    private ScaleAnimation mFadeinAnimation = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, 
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    private RotateOutAnimation mRotateOutAnimation = new RotateOutAnimation();

    public ThumbnailLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setWillNotDraw(false);
        mFadeinAnimation.setDuration(200);
        mRotateOutAnimation.setDuration(200);

        mRotateOutAnimation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation arg0) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationRepeat(Animation arg0) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationEnd(Animation arg0) {
                // TODO Auto-generated method stub
                if (lastBitmap != null)
                    lastImageView.setImageBitmap(lastBitmap);
                else
                    lastImageView.setImageResource(mRes);
                //currentImageView.setImageBitmap(null);
                currentImageView.setRotation(-mCurrentDegree);
                currentImageView.setImageBitmap(currentBitmap);
                if (hasRotateout) {
                    currentImageView.startAnimation(mFadeinAnimation);
                    hasRotateout = false;
                }
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        // TODO Auto-generated method stub
        super.onFinishInflate();
        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        View root = inflater.inflate(R.layout.thumbnail_layout, this);
        currentImageView = (ImageView) root.findViewById(R.id.thumbnail_current);
        lastImageView = (ImageView) root.findViewById(R.id.thumbnail_last);
        currentImageView.setRotation(mCurrentDegree);
        lastImageView.setRotation(mCurrentDegree+angle);
        lastImageView.setAlpha(0.5f);
    }

    public void setOrientation(int degree, boolean animation) {

        mEnableAnimation = animation;
        // make sure in the range of [0, 359]
        degree = degree >= 0 ? degree % 360 : degree % 360 + 360;
        if (degree == mTargetDegree) return;

        mTargetDegree = degree;
        if (mEnableAnimation) {
            mStartDegree = mCurrentDegree;
            mAnimationStartTime = AnimationUtils.currentAnimationTimeMillis();

            int diff = mTargetDegree - mCurrentDegree;
            diff = diff >= 0 ? diff : 360 + diff; // make it in range [0, 359]

            // Make it in range [-179, 180]. That's the shorted distance between the
            // two angles
            diff = diff > 180 ? diff - 360 : diff;

            mClockwise = diff >= 0;
            mAnimationEndTime = mAnimationStartTime
                    + Math.abs(diff) * 1000 / ANIMATION_SPEED;
        } else {
            mCurrentDegree = mTargetDegree;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);
        if (mCurrentDegree != mTargetDegree) {
            long time = AnimationUtils.currentAnimationTimeMillis();
            if (time < mAnimationEndTime) {
                int deltaTime = (int)(time - mAnimationStartTime);
                int degree = mStartDegree + ANIMATION_SPEED
                        * (mClockwise ? deltaTime : -deltaTime) / 1000;
                degree = degree >= 0 ? degree % 360 : degree % 360 + 360;
                mCurrentDegree = degree;
                invalidate();
            } else {
                mCurrentDegree = mTargetDegree;
            }
        }
        currentImageView.setRotation(-mCurrentDegree);
        lastImageView.setRotation(-mCurrentDegree+angle);
    }

    public void setImageResource(int id) {
        mRes = id;
        currentImageView.setImageResource(id);
        lastImageView.setImageResource(0);
    }

    @Override
    protected void drawableStateChanged() {
        if (isPressed()) {
            currentImageView.setAlpha(0.5f);
        } else {
            currentImageView.setAlpha(1f);
        }
        super.drawableStateChanged();
    }

    class RotateOutAnimation extends Animation {

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            // TODO Auto-generated method stub
            super.applyTransformation(interpolatedTime, t);
            mAnimationDegree = (int) (interpolatedTime * angle);
            currentImageView.setRotation(-mCurrentDegree + mAnimationDegree);
        }

    }

    public void rotateOut() {
        hasRotateout = true;
    }

    public void rotateIn(Bitmap bitmap, int res) {
        if (bitmap == null) {
            currentBitmap = null;
            lastBitmap = null;
            currentImageView.setImageResource(res);
            lastImageView.setImageBitmap(null);
        } else {
            mRes = res;
            //currentImageView.setImageBitmap(bitmap);
            if (hasRotateout) {
                lastImageView.startAnimation(mRotateOutAnimation);
            } else {
                currentImageView.setImageBitmap(bitmap);
                if (lastBitmap == null)
                    lastImageView.setImageResource(mRes);
            }
            lastBitmap = currentBitmap;
            currentBitmap = bitmap;
        }
    }
}
