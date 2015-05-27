package cc.fotoplace.camera.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.ScaleGestureDetector;
import cc.fotoplace.camera.R;

public class ZoomRenderer extends OverlayRenderer
implements ScaleGestureDetector.OnScaleGestureListener {

    //private static final String TAG = "CAM_Zoom";

    private int mMaxZoom;
    private int mMinZoom;
    private OnZoomChangedListener mListener;

    private ScaleGestureDetector mDetector;
    private Paint mPaint;
    private Paint mTextPaint;
    private int mCircleSize;
    private int mCenterX;
    private int mCenterY;
    private float mMaxCircle;
    private float mMinCircle;
    //private int mInnerStroke;
    //private int mOuterStroke;
    private int digitTextSize;
    //private int charTextSize;
    private int mZoomSig;
    private int mZoomFraction;
    private Rect mTextBounds;
    //private boolean isFullscreen;

    private final int GAP_DEGREE = 10;
    private final int CIRCLE_DEGREE = 360;
    private final int ART_START_DEGREE = -90;
    private final int ARC_NUM = 3;
    private int mArcStart[] = new int[ARC_NUM]; 
    //private int mSweepAngle;

    //private static final int BLOCK_SIZE = 10;
    private int mCurrentDegree;
    
    private RectF mOval = new RectF();

    public interface OnZoomChangedListener {
        void onZoomStart();
        void onZoomEnd();
        void onZoomValueChanged(int index);  // only for immediate zoom
    }

    public ZoomRenderer(Context ctx) {
        Resources res = ctx.getResources();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        //mPaint.setStyle(Paint.Style.STROKE);
        Typeface typeface = Typeface.createFromAsset(ctx.getAssets(), "fonts/camera_font.ttf");
        mTextPaint = new Paint(mPaint);
        mTextPaint.setStyle(Paint.Style.FILL);
        //mTextPaint.setTextSize(res.getDimensionPixelSize(R.dimen.zoom_font_size));
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setAlpha(255);
        mTextPaint.setTypeface(typeface);
        digitTextSize = res.getDimensionPixelOffset(R.dimen.zoom_font_digit_size);
        //charTextSize = res.getDimensionPixelOffset(R.dimen.zoom_font_char_size);
        //mInnerStroke = res.getDimensionPixelSize(R.dimen.focus_inner_stroke);
        //        mOuterStroke = res.getDimensionPixelSize(R.dimen.focus_outer_stroke);
        mDetector = new ScaleGestureDetector(ctx, this);
        mMinCircle = res.getDimensionPixelSize(R.dimen.zoom_ring_min);
        mMaxCircle = res.getDimensionPixelSize(R.dimen.zoom_ring_max);
        mTextBounds = new Rect();
        setVisible(false);

        //mSweepAngle = CIRCLE_DEGREE/ARC_NUM - GAP_DEGREE;
        for (int i = 0; i < ARC_NUM; i++) {
            mArcStart[i] = (CIRCLE_DEGREE/ARC_NUM) * i + (GAP_DEGREE / 2) + ART_START_DEGREE;
        }
        //isFullscreen = true;
    }

    // set from module
    public void setZoomMax(int zoomMaxIndex) {
        mMaxZoom = zoomMaxIndex;
        mMinZoom = 0;
    }

    public void setZoom(int index, boolean isShow) {
        mCircleSize = (int) (mMinCircle + index * (mMaxCircle - mMinCircle) / (mMaxZoom - mMinZoom));
        if (isShow) {
            setVisible(true);
            update();
        }
    }

    public void setZoomValue(int value) {
        value = value / 10;
        mZoomSig = value / 10;
        mZoomFraction = value % 10;
        if (mZoomFraction < 3) {
            mZoomFraction = 0;
        } else if (mZoomFraction < 7) {
            mZoomFraction = 5;
        } else {
            mZoomFraction = 0;
            mZoomSig += 1;
        }
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    // only called when first time entering module
    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;
    }

    public boolean isScaling() {
        return mDetector.isInProgress();
    }

    @Override
    public void onDraw(Canvas canvas) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.BLACK);
        mPaint.setAlpha((int)(255 * 0.4f));
        mOval.left = mCenterX - mCircleSize;
        mOval.right = mCenterX + mCircleSize;
        mOval.top = mCenterY - mCircleSize;
        mOval.bottom = mCenterY + mCircleSize;
        canvas.drawOval(mOval, mPaint);

        String zoomSigTxt = mZoomSig+"."+mZoomFraction+"x";
        mTextPaint.setTextSize(digitTextSize);
        mTextPaint.getTextBounds(zoomSigTxt, 0, zoomSigTxt.length(), mTextBounds);
        int width = getWidth();
        int height = getHeight();
        canvas.translate(width/2, height/2);
        canvas.rotate(-mCurrentDegree);
        canvas.translate(-width/2, -height/2);
        canvas.drawText(zoomSigTxt, mCenterX - mTextBounds.centerX(), mCenterY,
                mTextPaint);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mListener == null)
            return false;
        final float sf = detector.getScaleFactor();
        float circle = 0;
        circle = (int) (mCircleSize * Math.sqrt(sf));
        circle = Math.min(mMaxCircle, circle);
        mCircleSize = (int) circle;
        mCircleSize = Math.max((int)mMinCircle, mCircleSize);
        int zoom = mMinZoom + (int) ((Math.max((int)mMinCircle, mCircleSize) - mMinCircle) * (mMaxZoom - mMinZoom) / (mMaxCircle - mMinCircle));
        mListener.onZoomValueChanged(zoom);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        setVisible(true);
        if (mListener != null) {
            mListener.onZoomStart();
        }
        update();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        setVisible(false);
        if (mListener != null) {
            mListener.onZoomEnd();
        }
    }
    
    public void setOrientation(int degree) {
        mCurrentDegree = degree;
    }
    
    public void hideZoom() {
        setVisible(false);
    }
}
