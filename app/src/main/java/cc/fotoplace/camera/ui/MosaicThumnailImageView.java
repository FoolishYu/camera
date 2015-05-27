package cc.fotoplace.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class MosaicThumnailImageView extends ImageView{

    private Bitmap mDrawBitmap;
    private final Paint paint = new Paint();
    
    private final Rect drawingRect = new Rect();
    public MosaicThumnailImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public void setDrawBitmap(Bitmap bitmap) {
        mDrawBitmap = bitmap;
    }
    
    public void setDrawingWidth(int _width) {
        drawingRect.left = 0;
        drawingRect.right = _width;
        drawingRect.top = 0;
        drawingRect.bottom = mDrawBitmap.getHeight();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);
        if (mDrawBitmap != null) {
            Log.d("dyb", drawingRect.toShortString());
            canvas.drawBitmap(mDrawBitmap, drawingRect, drawingRect, paint);
        }
            
    }

}
