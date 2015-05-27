package cc.fotoplace.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ImageView;

public class QRDetectResult extends ImageView{

    private int mPoints[];
    private Rect r = new Rect();
    private static Paint paint = new Paint();
    private float ratio = 1f;
    public QRDetectResult(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);
        if (mPoints == null)
            return;
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.BLUE);
        for (int i = 0; i < mPoints.length/4; i++) {
            if (mPoints[4*i] == 0 && mPoints[4*i+2] == 0)
                continue;
            r.top = (int) (mPoints[4*i] * ratio) + 528;
            int left = (int) (mPoints[4*i+1] * ratio)+108;
            //int right = (int) (r.left+mPoints[4*i+3] * ratio);
            //r.left = 1080 - right;
            r.right = 1080 - left;
            r.left = (int) (r.right - mPoints[4*i+3] * ratio);
            r.bottom = (int) (r.top+mPoints[4*i+2] * ratio);
            canvas.drawRect(r, paint);
        }
    }
    
    public void setPoints(int points[]) {
        mPoints = points;
        invalidate();
    }

}
