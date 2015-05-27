package cc.fotoplace.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.Util;

public class FocusIndicatorView extends ImageView implements FocusIndicator{

    private int targetSize;
    private int currentSize;
    private int rawSize;
    private Paint paint = new Paint();
    private boolean isCleared = true;
    private boolean isSuccess = false;
    private AlphaAnimation mStartAlphaAnimation = new AlphaAnimation(0.6f, 1.0f);
    private AlphaAnimation mFadeoutAnimation = new AlphaAnimation(1.0f, 0.0f);
    private int FOCUS_SUCCESS_COLOR = 0;

    public FocusIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStartAlphaAnimation.setDuration(100);
        mFadeoutAnimation.setDuration(100);
        mFadeoutAnimation.setStartOffset(1000);
        mStartAlphaAnimation.setRepeatCount(2);
        mFadeoutAnimation.setFillAfter(true);
        rawSize = getResources().getDimensionPixelOffset(R.dimen.focus_indicator_inner_size);
        targetSize = rawSize;
        currentSize = targetSize;
        FOCUS_SUCCESS_COLOR = context.getResources().getColor(R.color.focus_color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setAntiAlias(true);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(5);
        if (isCleared)
            return;
        if (isSuccess) {
            paint.setColor(FOCUS_SUCCESS_COLOR);
        } else {
            paint.setColor(Color.WHITE);
        }
        canvas.drawCircle(getWidth()/2, getHeight()/2, currentSize/2, paint);
        if (currentSize > targetSize) {
            currentSize = (currentSize+targetSize*2)/3;
            if (currentSize <= targetSize+10) {
                currentSize = targetSize;
                isSuccess = true;
                startAnimation(mFadeoutAnimation);
            }
            invalidate();
        }
    }

    @Override
    public void showSuccess(boolean timeout) { 
        targetSize = (int) (rawSize * 0.8);
        invalidate();
    }

    @Override
    public void showFail(boolean timeout) {
        targetSize = (int) (rawSize * 0.8);
        invalidate();
    }

    @Override
    public void clear() {
        isCleared = true;
        targetSize = rawSize;
        currentSize = rawSize;
        invalidate();
        setFocus(Util.displayWidth/2, Util.displayHeight/2);
        setVisibility(View.GONE);
        clearAnimation();
    }

    @Override
    public void showStart(boolean manual) {
        isCleared = false;
        isSuccess = false;
        setVisibility(View.VISIBLE);
        startAnimation(mStartAlphaAnimation);
    }

    public void setFocus(int x, int y) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.leftMargin = x - Util.displayWidth / 2;
        lp.topMargin = y - Util.displayHeight / 2;
        requestLayout();
    }

}
