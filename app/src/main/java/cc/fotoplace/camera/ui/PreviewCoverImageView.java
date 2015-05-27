package cc.fotoplace.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

public class PreviewCoverImageView extends ImageView{

    private float scaleX;
    private float scaleY;
    private float offsetY;

    public PreviewCoverImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    public void setFullscreen(boolean isFullscreen, float imageRatio, float screenRatio) {
        if (Math.abs(screenRatio-imageRatio) < 0.01) {
            scaleX = 1;
            scaleY = 1;
            offsetY = 0;
        } else {
            if (isFullscreen) {
                if (screenRatio > imageRatio) {
                    scaleY = 1;
                    scaleX = 1;//screenRatio/imageRatio;
                    offsetY = 0;
                } else {

                }
            } else {
                if (screenRatio > imageRatio) {
                    float ratio = imageRatio/screenRatio;
                    scaleX = 1;
                    scaleY = ratio;
                    offsetY = 180/ratio;
                } else {

                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        //super.onDraw(canvas);
        Drawable mDrawable = getDrawable();
        canvas.scale(scaleX, scaleY);
        canvas.translate(0, offsetY);
        mDrawable.draw(canvas);
    }


}
