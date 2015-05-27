
package cc.fotoplace.camera;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import cc.fotoplace.camera.ui.RenderOverlay;
import cc.fotoplace.camera.ui.ZoomRenderer;

public class PreviewGestures implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "CAM_gestures";

    private static final int MODE_NONE = 0;
    private static final int MODE_PIE = 1;
    private static final int MODE_ZOOM = 2;
    private static final int MODE_MODULE = 3;
    private static final int MODE_ALL = 4;
    private static final int MODE_SWIPE = 5;
    // private static final int MODE_PINCH = 6;

    public static final int DIR_UP = 0;
    public static final int DIR_DOWN = 1;
    public static final int DIR_LEFT = 2;
    public static final int DIR_RIGHT = 3;

    public static final int PINCH_NONE = 0;
    public static final int PINCH_EXPAND = 1;
    public static final int PINCH_SHRINK = 2;

    public static final int SWIPE_TIMEOUT = 360;

    private CameraActivity mActivity;
    private SingleTapListener mTapListener;
    private RenderOverlay mOverlay;
    //private FocusRenderer mFocus;
    private ZoomRenderer mZoom;
    private MotionEvent mDown;
    private MotionEvent mCurrent;
    private ScaleGestureDetector mScale;
    private List<View> mReceivers;
    private List<View> mUnclickableAreas;
    private int mMode;
    private int mSlop;
    private int mTapTimeout;
    private boolean mEnabled;
    private boolean mZoomOnly;
    private int mOrientation;
    private int[] mLocation;
    private boolean isZoomEnabled = true;
    private SwipeListener mSwipeListener;

    public interface SingleTapListener {
        public void onSingleTapUp(View v, int x, int y);
    }

    public interface SwipeListener {
        public void onSwipe(int direction);
    }

    public PreviewGestures(CameraActivity ctx, SingleTapListener tapListener,
            ZoomRenderer zoom, SwipeListener swipe) {
        mActivity = ctx;
        mTapListener = tapListener;
        //mFocus = focus;
        mZoom = zoom;
        mMode = MODE_ALL;
        mScale = new ScaleGestureDetector(ctx, this);
        mSlop = (int) ctx.getResources().getDimension(R.dimen.pie_touch_slop);
        mTapTimeout = ViewConfiguration.getTapTimeout();
        mEnabled = true;
        mLocation = new int[2];
        mSwipeListener = swipe;
    }

    public void setRenderOverlay(RenderOverlay overlay) {
        mOverlay = overlay;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void setZoomOnly(boolean zoom) {
        mZoomOnly = zoom;
    }

    public void addTouchReceiver(View v) {
        if (mReceivers == null) {
            mReceivers = new ArrayList<View>();
        }
        mReceivers.add(v);
    }

    public void removeTouchReceiver(View v) {
        if (mReceivers == null || v == null)
            return;
        mReceivers.remove(v);
    }

    public void addUnclickableArea(View v) {
        if (mUnclickableAreas == null) {
            mUnclickableAreas = new ArrayList<View>();
        }
        mUnclickableAreas.add(v);
    }

    public void clearTouchReceivers() {
        if (mReceivers != null) {
            mReceivers.clear();
        }
    }

    public void clearUnclickableAreas() {
        if (mUnclickableAreas != null) {
            mUnclickableAreas.clear();
        }
    }

    private boolean checkClickable(MotionEvent m) {
        if (mUnclickableAreas != null) {
            for (View v : mUnclickableAreas) {
                if (isInside(m, v)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void reset() {
        clearTouchReceivers();
        clearUnclickableAreas();
    }

    public boolean dispatchTouch(MotionEvent m) {
        if (!mEnabled) {
            return true;//mActivity.superDispatchTouchEvent(m);
        }
        mCurrent = m;
        if (MotionEvent.ACTION_DOWN == m.getActionMasked()) {
            if (checkReceivers(m)) {
                mMode = MODE_MODULE;
                return mActivity.superDispatchTouchEvent(m);
            } else {
                mMode = MODE_ALL;
                mDown = MotionEvent.obtain(m);
                if (mZoom != null) {
                    mScale.onTouchEvent(m);
                }
                // make sure this is ok
                return mActivity.superDispatchTouchEvent(m);
            }
        } else if (mMode == MODE_NONE) {
            return false;
        } else if (mMode == MODE_SWIPE) {
            return true;
        } else if (mMode == MODE_PIE) {
            if (MotionEvent.ACTION_POINTER_DOWN == m.getActionMasked()) {
                if (mZoom != null) {
                    onScaleBegin(mScale);
                }
            } else {
                return true;
            }
            return true;
        } else if (mMode == MODE_ZOOM) {
            if (!mScale.isInProgress() && MotionEvent.ACTION_POINTER_UP == m.getActionMasked()) {
                mMode = MODE_NONE;
                onScaleEnd(mScale);
            } else {
                mScale.onTouchEvent(m);
            }
            return true;
        } else if (mMode == MODE_MODULE) {
            return mActivity.superDispatchTouchEvent(m);
        } else {
            // didn't receive down event previously;
            // assume module wasn't initialized and ignore this event.
            if (mDown == null) {
                return true;
            }
            if (MotionEvent.ACTION_POINTER_DOWN == m.getActionMasked()) {
                if (mZoom != null) {
                    mScale.onTouchEvent(m);
                    onScaleBegin(mScale);
                }
            } else if ((mMode == MODE_ZOOM) && !mScale.isInProgress()
                    && MotionEvent.ACTION_POINTER_UP == m.getActionMasked()) {
                // user initiated and stopped zoom gesture without zooming
                mScale.onTouchEvent(m);
                //onScaleEnd(mScale);
            }
            // not zoom or pie mode and no timeout yet
            if (mZoom != null) {
                boolean res = mScale.onTouchEvent(m);
                if (mScale.isInProgress()) {
                    cancelActivityTouchHandling(m);
                    return res;
                }
            }
            if (MotionEvent.ACTION_UP == m.getActionMasked()) {
                // must have been tap
                if (m.getEventTime() - mDown.getEventTime() < mTapTimeout
                        && checkClickable(m)) {
                    cancelActivityTouchHandling(m);
                    mTapListener.onSingleTapUp(null,
                            (int) mDown.getX() - mOverlay.getWindowPositionX(),
                            (int) mDown.getY() - mOverlay.getWindowPositionY());
                    return true;
                } else {
                    return mActivity.superDispatchTouchEvent(m);
                }
            } else if (MotionEvent.ACTION_MOVE == m.getActionMasked()) {
                float xMove = Math.abs(m.getX() - mDown.getX());
                float yMove = Math.abs(m.getY() - mDown.getY()); 
                if ( xMove > mSlop && xMove > 1.5f * yMove
                        && m.getEventTime() - mDown.getEventTime() < SWIPE_TIMEOUT) {
                    mSwipeListener.onSwipe(getSwipeDirection(m));
                    mMode = MODE_NONE;
                    return true;
                }
            }
            return false;
        }
    }

    private boolean checkReceivers(MotionEvent m) {
        if (mReceivers != null) {
            for (View receiver : mReceivers) {
                if (isInside(m, receiver)) {
                    return true;
                }
            }
        }
        return false;
    }

    // left tests for finger moving right to left
    private int getSwipeDirection(MotionEvent m) {
        float dx = 0;
        float dy = 0;
        switch (mOrientation) {
            case 0:
                dx = m.getX() - mDown.getX();
                dy = m.getY() - mDown.getY();
                break;
            case 90:
                dx = -(m.getY() - mDown.getY());
                dy = m.getX() - mDown.getX();
                break;
            case 180:
                dx = -(m.getX() - mDown.getX());
                dy = m.getY() - mDown.getY();
                break;
            case 270:
                dx = m.getY() - mDown.getY();
                dy = m.getX() - mDown.getX();
                break;
        }
        if (dx < 0 && (Math.abs(dy) / -dx < 2))
            return DIR_LEFT;
        if (dx > 0 && (Math.abs(dy) / dx < 2))
            return DIR_RIGHT;
        if (dy > 0)
            return DIR_DOWN;
        return DIR_UP;
    }

    private boolean isInside(MotionEvent evt, View v) {
        v.getLocationInWindow(mLocation);
        // when view is flipped horizontally
        if ((int) v.getRotationY() == 180) {
            mLocation[0] -= v.getWidth();
        }
        // when view is flipped vertically
        if ((int) v.getRotationX() == 180) {
            mLocation[1] -= v.getHeight();
        }
        return (v.getVisibility() == View.VISIBLE
                && evt.getX() >= mLocation[0] && evt.getX() < mLocation[0] + v.getWidth()
                && evt.getY() >= mLocation[1] && evt.getY() < mLocation[1] + v.getHeight());
    }

    public void cancelActivityTouchHandling(MotionEvent m) {
        mActivity.superDispatchTouchEvent(makeCancelEvent(m));
    }

    private MotionEvent makeCancelEvent(MotionEvent m) {
        MotionEvent c = MotionEvent.obtain(m);
        c.setAction(MotionEvent.ACTION_CANCEL);
        return c;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        Log.d("dyb", "onScale");
        return mZoom.onScale(detector);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (!checkClickable(mCurrent) || !checkClickable(mDown)) {
            return false;
        }
        if (!isZoomEnabled)
            return false;
        if (mMode != MODE_ZOOM) {
            mMode = MODE_ZOOM;
            cancelActivityTouchHandling(mCurrent);
        }
        if (mCurrent.getActionMasked() != MotionEvent.ACTION_MOVE) {
            Log.d("dyb", "onScaleBegin");
            return mZoom.onScaleBegin(detector);
        } else {
            return true;
        }
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (mCurrent.getActionMasked() != MotionEvent.ACTION_MOVE) {
            mZoom.onScaleEnd(detector);
        }
    }

    public void enableZoom(boolean enabled) {
        isZoomEnabled = enabled;
    }

}
