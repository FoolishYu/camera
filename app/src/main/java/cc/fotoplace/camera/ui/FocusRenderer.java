package cc.fotoplace.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;


public class FocusRenderer extends OverlayRenderer
        implements FocusIndicator {

    private static final String TAG = "CAM Pie";

    // Sometimes continuous autofocus starts and stops several times quickly.
    // These states are used to make sure the animation is run for at least some
    // time.
    private volatile int mState;
    private ScaleAnimation mAnimation = new ScaleAnimation();
    private static final int STATE_IDLE = 0;
    private static final int STATE_FOCUSING = 1;
    private static final int STATE_FINISHING = 2;
    //private static final int STATE_PIE = 8;

    private static final float MATH_PI_2 = (float)(Math.PI / 2);

    private Runnable mDisappear = new Disappear();
    private Animation.AnimationListener mEndAction = new EndAction();
    private static final int SCALING_UP_TIME = 600;
    private static final int SCALING_DOWN_TIME = 100;
    private static final int DISAPPEAR_TIMEOUT = 200;
    private static final int DIAL_HORIZONTAL = 157;
    // fade out timings
    private static final int PIE_FADE_OUT_DURATION = 600;

    private static final long PIE_FADE_IN_DURATION = 200;
    private static final long PIE_XFADE_DURATION = 200;
    private static final long PIE_SELECT_FADE_DURATION = 300;
    private static final long PIE_OPEN_SUB_DELAY = 400;
    private static final long PIE_SLICE_DURATION = 80;

    private static final int MSG_OPEN = 0;
    private static final int MSG_CLOSE = 1;
    private static final int MSG_OPENSUBMENU = 2;

    protected static float CENTER = (float) Math.PI / 2;
    protected static float RAD24 = (float)(24 * Math.PI / 180);
    protected static final float SWEEP_SLICE = 0.14f;
    protected static final float SWEEP_ARC = 0.23f;

    // geometry
    private int mRadius;
    private int mRadiusInc;

    // the detection if touch is inside a slice is offset
    // inbounds by this amount to allow the selection to show before the
    // finger covers it
    private int mTouchOffset;

    private Paint mSelectedPaint;
    private Paint mSubPaint;
    private Paint mMenuArcPaint;

    // touch handling
    private Paint mFocusPaint;
    private int mSuccessColor;
    private int mFailColor;
    private int mCircleSize;
    private int mFocusX;
    private int mFocusY;
    private int mCenterX;
    private int mCenterY;
    private int mArcCenterY;
    private int mSliceCenterY;
    private int mPieCenterX;
    private int mPieCenterY;
    private int mSliceRadius;
    private int mArcRadius;
    private int mArcOffset;

    private int mDialAngle;
    private RectF mCircle;
    private RectF mDial;
    private Point mPoint1;
    private Point mPoint2;
    private int mStartAnimationAngle;
    private boolean mFocused;
    private int mInnerOffset;
    private int mOuterStroke;
    private int mInnerStroke;
    private boolean mTapMode;
    private boolean mBlockFocus;
    private int mTouchSlopSquared;
    private Point mDown;
    private boolean mOpening;
    private LinearAnimation mXFade;
    private LinearAnimation mFadeIn;
    private FadeOutAnimation mFadeOut;
    private LinearAnimation mSlice;
    private volatile boolean mFocusCancelled;
    private PointF mPolar = new PointF();
    private int mDeadZone;
    private int mAngleZone;
    private float mCenterAngle;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case MSG_OPEN:
                if (mListener != null) {
                    mListener.onPieOpened(mPieCenterX, mPieCenterY);
                }
                break;
            case MSG_CLOSE:
                if (mListener != null) {
                    mListener.onPieClosed();
                }
                break;
            case MSG_OPENSUBMENU:
                break;
            }

        }
    };

    private PieListener mListener;

    static public interface PieListener {
        public void onPieOpened(int centerX, int centerY);
        public void onPieClosed();
    }

    public void setPieListener(PieListener pl) {
        mListener = pl;
    }

    public FocusRenderer(Context context) {
        init(context);
    }

    private void init(Context ctx) {
        setVisible(false);
        mCircle = new RectF();
        mDial = new RectF();
        mCircleSize = 50;
        mState = STATE_IDLE;
        mFocusPaint = new Paint();
        mFocusPaint.setAntiAlias(true);
        mFocusPaint.setColor(Color.WHITE);
        mFocusPaint.setStyle(Paint.Style.STROKE);
        mPoint1 = new Point();
        mPoint2 = new Point();
        mSuccessColor = Color.GREEN;
        mFailColor = Color.RED;
    }


    @Override
    public boolean handlesTouch() {
        return true;
    }

    // focus specific code

    public void setBlockFocus(boolean blocked) {
        mBlockFocus = blocked;
        if (blocked) {
            clear();
        }
    }

    public void setFocus(int x, int y) {
        mFocusX = x;
        mFocusY = y;
        setCircle(mFocusX, mFocusY);
    }

    public void alignFocus(int x, int y) {
        mOverlay.removeCallbacks(mDisappear);
        mAnimation.cancel();
        mAnimation.reset();
        mFocusX = x;
        mFocusY = y;
        mDialAngle = DIAL_HORIZONTAL;
        setCircle(x, y);
        mFocused = false;
    }

    public int getSize() {
        return 2 * mCircleSize;
    }

    private int getRandomRange() {
        return (int)(-60 + 120 * Math.random());
    }

    private void setCircle(int cx, int cy) {
        mCircle.set(cx - mCircleSize, cy - mCircleSize,
                cx + mCircleSize, cy + mCircleSize);
        mDial.set(cx - mCircleSize + mInnerOffset, cy - mCircleSize + mInnerOffset,
                cx + mCircleSize - mInnerOffset, cy + mCircleSize - mInnerOffset);
    }

    public void drawFocus(Canvas canvas) {
        if (mBlockFocus) return;
        mFocusPaint.setStrokeWidth(mOuterStroke);
        canvas.drawCircle((float) mFocusX, (float) mFocusY, (float) mCircleSize, mFocusPaint);
        //if (mState == STATE_PIE) return;
        int color = mFocusPaint.getColor();
        if (mState == STATE_FINISHING) {
            mFocusPaint.setColor(mFocused ? mSuccessColor : mFailColor);
        }
        mFocusPaint.setStrokeWidth(mInnerStroke);
        drawLine(canvas, mDialAngle, mFocusPaint);
        drawLine(canvas, mDialAngle + 45, mFocusPaint);
        drawLine(canvas, mDialAngle + 180, mFocusPaint);
        drawLine(canvas, mDialAngle + 225, mFocusPaint);
        canvas.save();
        // rotate the arc instead of its offset to better use framework's shape caching
        canvas.rotate(mDialAngle, mFocusX, mFocusY);
        canvas.drawArc(mDial, 0, 45, false, mFocusPaint);
        canvas.drawArc(mDial, 180, 45, false, mFocusPaint);
        canvas.restore();
        mFocusPaint.setColor(color);
    }

    private void drawLine(Canvas canvas, int angle, Paint p) {
        convertCart(angle, mCircleSize - mInnerOffset, mPoint1);
        convertCart(angle, mCircleSize - mInnerOffset + mInnerOffset / 3, mPoint2);
        canvas.drawLine(mPoint1.x + mFocusX, mPoint1.y + mFocusY,
                mPoint2.x + mFocusX, mPoint2.y + mFocusY, p);
    }

    private static void convertCart(int angle, int radius, Point out) {
        double a = 2 * Math.PI * (angle % 360) / 360;
        out.x = (int) (radius * Math.cos(a) + 0.5);
        out.y = (int) (radius * Math.sin(a) + 0.5);
    }

    @Override
    public void showStart(boolean manual) {
    	//Log.d("dyb", "show start");
        //if (mState == STATE_PIE) return;
        //Log.d("dyb", "show start returned");
        cancelFocus();
        mStartAnimationAngle = 67;
        int range = getRandomRange();
        startAnimation(SCALING_UP_TIME,
                false, mStartAnimationAngle, mStartAnimationAngle + range);
        mState = STATE_FOCUSING;
    }

    @Override
    public void showSuccess(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = true;
        }
    }

    @Override
    public void showFail(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = false;
        }
    }

    private void cancelFocus() {
        mFocusCancelled = true;
        mOverlay.removeCallbacks(mDisappear);
        if (mAnimation != null && !mAnimation.hasEnded()) {
            mAnimation.cancel();
        }
        mFocusCancelled = false;
        mFocused = false;
        mState = STATE_IDLE;
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;

        mFocusX = mCenterX;
        mFocusY = mCenterY;
        setCircle(mFocusX, mFocusY);
    }
    
    @Override
    public void clear() {
        //if (mState == STATE_PIE) return;
        cancelFocus();
        mOverlay.post(mDisappear);
    }

    private void startAnimation(long duration, boolean timeout,
            float toScale) {
        startAnimation(duration, timeout, mDialAngle,
                toScale);
    }

    private void startAnimation(long duration, boolean timeout,
            float fromScale, float toScale) {
        setVisible(true);
        mAnimation.reset();
        mAnimation.setDuration(duration);
        mAnimation.setScale(fromScale, toScale);
        mAnimation.setAnimationListener(timeout ? mEndAction : null);
        mOverlay.startAnimation(mAnimation);
        update();
    }

    private class EndAction implements Animation.AnimationListener {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Keep the focus indicator for some time.
            if (!mFocusCancelled) {
                mOverlay.postDelayed(mDisappear, DISAPPEAR_TIMEOUT);
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    private class Disappear implements Runnable {
        @Override
        public void run() {
            //if (mState == STATE_PIE) return;
            setVisible(false);
            mFocusX = mCenterX;
            mFocusY = mCenterY;
            mState = STATE_IDLE;
            setCircle(mFocusX, mFocusY);
            mFocused = false;
        }
    }

    private class FadeOutAnimation extends Animation {

        private float mAlpha;

        public float getValue() {
            return mAlpha;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (interpolatedTime < 0.2) {
                mAlpha = 1;
            } else if (interpolatedTime < 0.3) {
                mAlpha = 0;
            } else {
                mAlpha = 1 - (interpolatedTime - 0.3f) / 0.7f;
            }
        }
    }

    private class ScaleAnimation extends Animation {
        private float mFrom = 1f;
        private float mTo = 1f;

        public ScaleAnimation() {
            setFillAfter(true);
        }

        public void setScale(float from, float to) {
            mFrom = from;
            mTo = to;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mDialAngle = (int)(mFrom + (mTo - mFrom) * interpolatedTime);
        }
    }

    private class LinearAnimation extends Animation {
        private float mFrom;
        private float mTo;
        private float mValue;

        public LinearAnimation(float from, float to) {
            setFillAfter(true);
            setInterpolator(new LinearInterpolator());
            mFrom = from;
            mTo = to;
        }

        public float getValue() {
            return mValue;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mValue = (mFrom + (mTo - mFrom) * interpolatedTime);
        }
    }

	@Override
	public void onDraw(Canvas canvas) {
		drawFocus(canvas);
	}

}
