
package cc.fotoplace.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import cc.fotoplace.camera.R;

public class PanoramaIndicator extends RelativeLayout {

    private ImageView mBackgroundImageView = null;
    private ImageView mRectImageView = null;
    private ImageView mArrowImageView = null;
    private MosaicThumnailImageView mThumbnailImageView = null;
    private TextView mTextInfo = null;
    private int mBarHeight;
    private int mArrowYOffset;
    private int mBorder;
    private int mRectRawOffset;
    private int mMosaicedOffset;
    private int mRectWidth;
    
    private static final float VERTICAL_OFFSET_COEF = 0.1F;
    private static final int FAST_SPEED_THRESHOLD  = 200;
    private static final int X_NORMAR_THRESHOLD = 50;

    public PanoramaIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBorder = context.getResources().getDimensionPixelOffset(R.dimen.panorama_indicator_border);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mRectImageView = (ImageView) findViewById(R.id.panorama_rect);
        mBackgroundImageView = (ImageView) findViewById(R.id.panorama_bg);
        mArrowImageView = (ImageView) findViewById(R.id.panorama_start_arrow);
        mTextInfo = (TextView) findViewById(R.id.panorama_text_info);
        mThumbnailImageView = (MosaicThumnailImageView) findViewById(R.id.panorama_thumbnail);
    }

    public void setOffset(int x, int y, int speed) {
        // The Moving Mosaiced Rect
        RelativeLayout.LayoutParams lpRect = (LayoutParams) mRectImageView.getLayoutParams();
        lpRect.leftMargin = mRectRawOffset + x;
        lpRect.rightMargin = mBorder;
        mRectImageView.requestLayout();

        // Arrow Sign
        RelativeLayout.LayoutParams lpStart = (LayoutParams) mArrowImageView.getLayoutParams();
        lpStart.topMargin = mArrowYOffset-y;
        lpStart.leftMargin = (int) (lpRect.leftMargin + 1.2 * mRectWidth);
        if (lpStart.leftMargin > 2 * mRectWidth)
            lpStart.leftMargin = Math.min(lpStart.leftMargin, getWidth() - mRectWidth);
        mArrowImageView.requestLayout();

        if (x < X_NORMAR_THRESHOLD) {
            showNormal();
        } else {
            if (y < - mBarHeight * VERTICAL_OFFSET_COEF) { // barHeight = 240 in xxhdpi
                showTooLow();
            } else if (y < mBarHeight * VERTICAL_OFFSET_COEF) {
                if (speed > FAST_SPEED_THRESHOLD) {
                    showTooFast();
                } else {
                    showNormal();
                }
            } else {
                showTooHigh();
            }
        }
    }

    /**
     * Set the size of indicator, specify the width and height of the real-time image rectangle.   
     * @param rectWidth  : Screen Width divides MOSAIC_PAGE_NUM,  
     * @param rectHeight : Preview ratio multiply rectWidth  
     */
    public void setSize(int rectWidth, int rectHeight) {
        mBarHeight = rectHeight;
        mRectWidth = rectWidth;
        RelativeLayout.LayoutParams lpRect = (LayoutParams) mRectImageView.getLayoutParams();
        mMosaicedOffset = getResources().getDimensionPixelSize(R.dimen.mosaiced_rect_offset);
        mRectRawOffset = rectWidth - mMosaicedOffset;
        lpRect.height = rectHeight + mBorder;
        mRectImageView.requestLayout();

        RelativeLayout.LayoutParams lpBg = (LayoutParams) mBackgroundImageView.getLayoutParams();
        lpBg.height = rectHeight + mBorder;
        mBackgroundImageView.requestLayout();

        RelativeLayout.LayoutParams lpThumbnail = (LayoutParams) mThumbnailImageView.getLayoutParams();
        lpThumbnail.height = rectHeight;
        mThumbnailImageView.requestLayout();

        mArrowYOffset = getResources().getDimensionPixelOffset(R.dimen.panorama_indicator_arrow_offset);

        mRectImageView.setVisibility(View.VISIBLE);
        mBackgroundImageView.setVisibility(View.VISIBLE);
    }

    public void showStart() {
        setOffset(0, 0, 0);
        mArrowImageView.setVisibility(View.VISIBLE);
        mTextInfo.setText(R.string.panorama_info_start);
    }

    private void showTooFast() {
        mTextInfo.setText(R.string.panorama_info_fast);
    }

    private void showTooLow() {
        mTextInfo.setText(R.string.panorama_info_low);
    }

    private void showTooHigh() {
        mTextInfo.setText(R.string.panorama_info_high);
    }

    private void showNormal() {
        mTextInfo.setText(R.string.panorama_info_normal);
    }

    public void showFinish() {
        mTextInfo.setText(null);
        mArrowImageView.setVisibility(View.GONE);
    }

    public void setThumbnailBitmap(Bitmap bitmap) {
        mThumbnailImageView.setDrawBitmap(bitmap);
    }

    public void setDrawingWidth(int _width) {
        mThumbnailImageView.setDrawingWidth(_width);
    }
}
