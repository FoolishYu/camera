package cc.fotoplace.camera.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import cc.fotoplace.camera.CameraActivity;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.Util;

//识别face的view
public class FaceView extends View
    implements FocusIndicator, Rotatable {
    private static final String TAG = "CAM FaceView";
    private final boolean LOGV = false;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mDisplayOrientation;
    // The orientation compensation for the face indicator to make it look
    // correctly in all device orientations. Ex: if the value is 90, the
    // indicator should be rotated 90 degrees counter-clockwise.
    private int mOrientation;
    private boolean mMirror;
    private boolean mPause;
    private Matrix mMatrix = new Matrix();
    private RectF mRect = new RectF();
    private Rect drawingRect = new Rect();
    // As face detection can be flaky, we add a layer of filtering on top of it
    // to avoid rapid changes in state (eg, flickering between has faces and
    // not having faces)
    private Face[] mFaces;
    private Face[] mPendingFaces;
    private int mColor;
    private final int mFocusingColor;
    private final int mFocusedColor;
    private final int mFailColor;
    private Paint mPaint;
    private volatile boolean mBlocked;

    private int mUncroppedWidth;
    private int mUncroppedHeight;
    private static final int MSG_SWITCH_FACES = 1;
    private static final int SWITCH_DELAY = 70;
    private boolean mStateSwitchPending = false;
    private Bitmap faceBitmap;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_SWITCH_FACES:
                mStateSwitchPending = false;
                mFaces = mPendingFaces;
                invalidate();
                break;
            }
        }
    };

    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        mFocusingColor = res.getColor(R.color.face_detect_start);
        mFocusedColor = res.getColor(R.color.face_detect_success);
        mFailColor = res.getColor(R.color.face_detect_fail);
        mColor = mFocusingColor;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        //mPaint.setStyle(Style.STROKE);
        //mPaint.setStrokeWidth(res.getDimension(R.dimen.face_circle_stroke));
        faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.camera_focus_face);
        drawingRect.left = 0;
        drawingRect.right = faceBitmap.getWidth();
        drawingRect.top = 0;
        drawingRect.bottom = faceBitmap.getHeight();
    }

    public void setFaces(Face[] faces) {
        if (LOGV) Log.v(TAG, "Num of faces=" + faces.length);
        if (mPause) return;
        if (mFaces != null) {
            if ((faces.length > 0 && mFaces.length == 0)
                    || (faces.length == 0 && mFaces.length > 0)) {
                mPendingFaces = faces;
                if (!mStateSwitchPending) {
                    mStateSwitchPending = true;
                    mHandler.sendEmptyMessageDelayed(MSG_SWITCH_FACES, SWITCH_DELAY);
                }
                return;
            }
        }
        if (mStateSwitchPending) {
            mStateSwitchPending = false;
            mHandler.removeMessages(MSG_SWITCH_FACES);
        }
        mFaces = faces;
        invalidate();
    }

    public void setDisplayOrientation(int orientation) {
        mDisplayOrientation = orientation;
        if (LOGV) Log.v(TAG, "mDisplayOrientation=" + orientation);
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        invalidate();
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
        if (LOGV) Log.v(TAG, "mMirror=" + mirror);
    }

    public boolean faceExists() {
        return (mFaces != null && mFaces.length > 0);
    }

    @Override
    public void showStart(boolean manual) {
        mColor = mFocusingColor;
        invalidate();
    }

    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showSuccess(boolean timeout) {
        mColor = mFocusedColor;
        invalidate();
    }

    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showFail(boolean timeout) {
        mColor = mFailColor;
        invalidate();
    }

    @Override
    public void clear() {
        // Face indicator is displayed during preview. Do not clear the
        // drawable.
        mColor = mFocusingColor;
        mFaces = null;
        invalidate();
    }

    public void pause() {
        mPause = true;
    }

    public void resume() {
        mPause = false;
    }

    public void setBlockDraw(boolean block) {
        mBlocked = block;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mBlocked && (mFaces != null) && (mFaces.length > 0)) {
            //int rw, rh;
        	int size[] = new int[2];
            if (mUncroppedWidth == 0) {
                // TODO: This check is temporary. It needs to be removed after the
                // refactoring is fully functioning.
                final CameraSurfaceView csv = ((CameraActivity) getContext()).getGLRootView();
                csv.getDrawingAreaSize(size);
            } else {
            	size[0] = mUncroppedWidth;
            	size[1] = mUncroppedHeight;
            }
            size[0] *= 1.3;
            size[1] *= 1.3;
            // Prepare the matrix.
            if (((size[1] > size[0]) && ((mDisplayOrientation == 0) || (mDisplayOrientation == 180)))
                    || ((size[0] > size[1]) && ((mDisplayOrientation == 90) || (mDisplayOrientation == 270)))) {
                int temp = size[0];
                size[0] = size[1];
                size[1] = temp;
            }
            Util.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, size[0], size[1]);
            int dx = (getWidth() - size[0]) / 2;
            int dy = (getHeight()- size[1]) / 2;

            // Focus indicator is directional. Rotate the matrix and the canvas
            // so it looks correctly in all orientations.
            canvas.save();
            mMatrix.postRotate(mOrientation); // postRotate is clockwise
            canvas.rotate(-mOrientation); // rotate is counter-clockwise (for canvas)
            for (int i = 0; i < mFaces.length; i++) {
                // Filter out false positives.
                if (mFaces[i].score < 50) continue;

                // Transform the coordinates.
                mRect.set(mFaces[i].rect);
                if (LOGV) Util.dumpRect(mRect, "Original rect");
                mMatrix.mapRect(mRect);
                if (LOGV) Util.dumpRect(mRect, "Transformed rect");
                mPaint.setColor(mColor);
                mRect.offset(dx, dy);
                canvas.drawBitmap(faceBitmap, drawingRect, mRect, mPaint);
                //canvas.drawOval(mRect, mPaint);
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }
}
