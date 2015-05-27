package cc.fotoplace.camera;

import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

public abstract class CameraModule {

	protected CameraActivity mActivity;
	protected View mRoot;

	public void init(CameraActivity activity, View root) {
		mActivity = activity;
		mRoot = root;
	}

	public abstract void onFullScreenChanged(boolean full);

	public void onPauseBeforeSuper() {
	}

	public abstract void onResumeBeforeSuper();

	public abstract void onPauseAfterSuper();

	public void onResumeAfterSuper() {
	}

	public abstract void onConfigurationChanged(Configuration config);

	public abstract void onStop();

	public abstract void onDestroy();
  //mCameraState
	public abstract void onStateChanged();
    //sd卡检测
	public abstract void installIntentFilter();

	public abstract void onActivityResult(int requestCode, int resultCode,
			Intent data);

	public abstract boolean onBackPressed();

	public abstract boolean onKeyDown(int keyCode, KeyEvent event);

	public abstract boolean onKeyUp(int keyCode, KeyEvent event);

	public abstract void onSingleTapUp(View view, int x, int y);

	public abstract boolean dispatchTouchEvent(MotionEvent m);

	public abstract void onPreviewTextureCopied();

	public abstract void onCaptureTextureCopied();

	public abstract void onUserInteraction();

	public abstract boolean updateStorageHintOnResume();
    //屏幕旋转
	public abstract void onOrientationChanged(int orientation);
   //传值MediaSaveService
	public abstract void onMediaSaveServiceConnected(MediaSaveService s);
    //重置CameraSurfaceView
	public abstract void restartPreview();
   //切换CameraModule
	public abstract void onSwipe(int direction);

	public abstract void onFlipAnimationEnd();
  //CameraSurfaceView CameraSurfaceView
	public abstract void frameAvailable();

	private String getModuleName() {
		return this.getClass().getSimpleName();
	}

}
