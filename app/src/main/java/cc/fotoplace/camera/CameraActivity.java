package cc.fotoplace.camera;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.FrameLayout;

import cc.fotoplace.camera.CameraManager.CameraProxy;
import cc.fotoplace.camera.PreviewGestures.SwipeListener;
import cc.fotoplace.camera.ui.CameraSurfaceView;
import cc.fotoplace.camera.ui.PreviewCoverImageView;
import cc.fotoplace.camera.ui.RotateAnimation;
import cc.fotoplace.camera.ui.ThumbnailLayout;
import cc.fotoplace.camera.ui.CameraSurfaceView.SurfaceCallback;


public class CameraActivity extends ActivityBase implements SurfaceCallback,
		SwipeListener, SurfaceHolder.Callback {

	private static final String TAG = "Camera_Activity";
	public static final String ChangeModuleTag = "ChangeModuleTag";
	public static final String HWPerformanceTag = "HWPerformanceTag";
	private static final String PERFORMANCE_TAG = "CameraPerformanceTag";
	public static final int UNKNOWN_MODULE_INDEX = -1;
	public static final int VIDEO_MODULE_INDEX = 0;
	public static final int PHOTO_MODULE_INDEX = 1;
	public static final int SCANNER_MODULE_INDEX = 2;
	public static final int PANORAMA_MODULE_INDEX = 3;

	public static final int MODULE_NUM = 4;

	public static final int ANIMATION_DURATION = 250;
	public static final int VIEW_ID_VIDEO_RECORDING_PANEL = 10001;

	private CameraModule mCurrentModule;
	private CameraSurfaceView mGlRootView;
	private PreviewCoverImageView mSurfaceCoverImageView; //聚焦显示图片
	private FrameLayout mFrame;                           //不同模式显示的fragment
	private Thumbnail mPhotoThumbnail;                    //底部图片显示调用类
	private Thumbnail mVideoThumbnail;
	// private boolean mAutoRotateScreen;

	private int mCameraId = 0;
	private CameraProxy mCameraDevice = null;
	private CameraStartUpThread mCameraStartUpThread = null;
	private int mCameraState = CAMERA_STATE_PREVIEW_STOPPED;

	public static final int CAMERA_STATE_PREVIEWING = 1;//相机预览
	public static final int CAMERA_STATE_PREVIEW_STOPPED = 2;//相机预览停止
	public static final int CAMERA_STATE_SNAPSHOTTING = 3;   //快照
	public static final int CAMERA_STATE_FOCUSING = 4;
	public static final int CAMERA_STATE_SWITCHING_CAMERA = 5;

	private MyOrientationEventListener mOrientationListener;
	// The degrees of the device rotated clockwise from its natural orientation.
	private int mLastRawOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
	private int mOrientation = 0;

	private long mOnCreateStartTime = 0;
	private long onPauseEndTime = 0;
	private long beforeOpenCameraTime = 0;
	private boolean mPaused = false;
	// public CameraScreenNail mCameraScreenNail;

	private MediaSaveService mMediaSaveService;
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder b) {
			mMediaSaveService = ((MediaSaveService.LocalBinder) b).getService();
			mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
			Log.d("dyb_camera_activity", "onServiceConnected");
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mMediaSaveService = null;
		}
	};

	// .不阻塞主线程 打开相机The purpose is not to block the main thread in onCreate and onResume.不阻塞主线程 打开相机
	private class CameraStartUpThread extends Thread {
		private CameraActivity mOwner = null;
		private volatile boolean mCancelled;

		public CameraStartUpThread(CameraActivity owner) {
			mOwner = owner;
		}

		public void cancel() {
			mCancelled = true;
			interrupt();
		}

		@Override
		public void run() {
			boolean success = true;

			try {
				if (mCancelled)
					return;
				long beforeOpenCamera = System.currentTimeMillis();
				mCameraDevice = Util.openCamera(mOwner, mCameraId);
				long openTime = System.currentTimeMillis() - beforeOpenCamera;
				Log.d(PERFORMANCE_TAG, "open camera(" + mCameraId + ") costs "
						+ openTime + " ms");
			} catch (CameraHardwareException e) {
				Log.d(TAG, "open camera fail");
				success = false;
				e.printStackTrace();
			} catch (CameraDisabledException e) {
				Log.d(TAG, "open camera fail");
				success = false;
				e.printStackTrace();
			}

			if (!success) {
				mCameraDevice = null;
			}

			mCameraStartUpThread = null;
		}
	}
   //打开相机
	public void openCameraAsync(int cameraId) {
		Log.v(TAG, "openCamera");
		if (mCameraDevice != null || mCameraStartUpThread != null) {
			return;
		}

		mCameraId = cameraId;
		mCameraStartUpThread = new CameraStartUpThread(this);
		mCameraStartUpThread.start();
	}
    //获取CameraProxy
	public CameraProxy getCameraDevice() {
		try {
			if (mCameraStartUpThread != null) {
				mCameraStartUpThread.join();
			}
		} catch (InterruptedException e) {
			mCameraDevice = null;
			e.printStackTrace();
		}
		long getCameraDeviceTime = System.currentTimeMillis()
				- beforeOpenCameraTime;
		Log.d(PERFORMANCE_TAG, "open camera to get camera time is "
				+ getCameraDeviceTime + " ms");
		return mCameraDevice;
	}

	public void closeCamera() {
		Log.v(TAG, "closeCamera");

		// if open camera thread is running, cancel the thread first
		try {
			if (mCameraStartUpThread != null) {
				mCameraStartUpThread.cancel();
				mCameraStartUpThread.join();
				mCameraStartUpThread = null;
			}
		} catch (InterruptedException e) {
			// ignore
			e.printStackTrace();
		}

		if (mCameraDevice != null) {
			mCameraDevice.setZoomChangeListener(null);
			mCameraDevice.setFaceDetectionListener(null);
			mCameraDevice.setErrorCallback(null);

			if (mCameraState != CAMERA_STATE_FOCUSING) {
				mCameraDevice.cancelAutoFocus();
				setCameraState(CAMERA_STATE_PREVIEWING);
			}

			if (mCameraState != CAMERA_STATE_PREVIEWING) {
				Log.v(TAG, "stop preview");
				mCameraDevice.stopPreview();
			}

			CameraHolder.instance().release();
			mCameraDevice = null;
		}

		mCameraState = CAMERA_STATE_PREVIEW_STOPPED;
	}

	public void setCameraState(int state) {
		Log.v(TAG, "setCameraState to " + state);
		mCameraState = state;
		mCurrentModule.onStateChanged();
	}

	public int getCameraState() {
		return mCameraState;
	}

	public boolean isPaused() {
		return mPaused;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.v(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		// record the onCreate() time
		mOnCreateStartTime = System.currentTimeMillis();
		Log.d(PERFORMANCE_TAG, "on create time is " + mOnCreateStartTime);
		setContentView(R.layout.camera_main);
		mFrame = (FrameLayout) findViewById(R.id.camera_app_root);
		mGlRootView = (CameraSurfaceView) findViewById(R.id.gl_root_view);
		mGlRootView.getHolder().addCallback(this);
		mSurfaceCoverImageView = (PreviewCoverImageView) findViewById(R.id.gl_root_cover);
		mGlRootView.onCreate();
		beforeOpenCameraTime = System.currentTimeMillis();
		long create2open = beforeOpenCameraTime - mOnCreateStartTime;
		Log.d(PERFORMANCE_TAG, "oncreate to open time " + create2open + " ms");
		openCameraAsync(mCameraId);

		if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent()
				.getAction())
				|| MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent()
						.getAction())) {
			mCurrentModule = new VideoModule();
//		} else if ("com.f.camera.SCAN_QRCODE".equals(getIntent()
//				.getAction())) {
//			mCurrentModule = new PhotoModule();
		} else {
			mCurrentModule = new PhotoModule();
		}
		mCurrentModule.init(this, mFrame);
		mOrientationListener = new MyOrientationEventListener(this);
		bindMediaSaveService();
		Util.initialize(this);
	}

	@Override
	public void onDestroy() {
		Log.v(TAG, "onDestroy");
		unbindMediaSaveService();
		mCurrentModule.onDestroy();
		if (mGlRootView != null)
			mGlRootView.releaseSurfaceTexture();
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		mCurrentModule.onConfigurationChanged(config);
	}

	@Override
	public void onResume() {
		Log.v(TAG, "onResume time = "
				+ (System.currentTimeMillis() - mOnCreateStartTime));

		mPaused = false;
		openCameraAsync(mCameraId);
		mOrientationListener.enable();
		mCurrentModule.onResumeBeforeSuper();
		super.onResume();
		mCurrentModule.onResumeAfterSuper();
		if (mSecureCamera) {
			// we don't load thumbnail in secure camera
		} else {
			forceUpdateThumbnail();
		}
	}

	@Override
	protected void onStop() {
		Log.v(TAG, "onStop");
		super.onStop();
		mCurrentModule.onStop();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
	}

	@Override
	protected void installIntentFilter() {
		super.installIntentFilter();
		mCurrentModule.installIntentFilter();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		mCurrentModule.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onBackPressed() {
		if (!mCurrentModule.onBackPressed()) {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return mCurrentModule.onKeyDown(keyCode, event)
				|| super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return mCurrentModule.onKeyUp(keyCode, event)
				|| super.onKeyUp(keyCode, event);
	}

	@Override
	public void startActivityForResult(Intent intent, int requestCode) {
		// Intent proxyIntent = new Intent(this, ProxyLauncher.class);
		// proxyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		// proxyIntent.putExtra(Intent.EXTRA_INTENT, intent);
		super.startActivityForResult(intent, requestCode);
	}

	@Override
	public void onPause() {
		Log.v(TAG, "onPause");
		mPaused = true;
		mOrientationListener.disable();
		mCurrentModule.onPauseBeforeSuper();
		mSurfaceCoverImageView.setVisibility(View.GONE);
		mSurfaceCoverImageView.setImageBitmap(null);
		super.onPause();
		mCurrentModule.onPauseAfterSuper();
		// invalidate Thumbnail after Activity is onResumed.
		mPhotoThumbnail = null;
		mVideoThumbnail = null;
		closeCamera();
		onPauseEndTime = System.currentTimeMillis();
	}

	private void bindMediaSaveService() {
		Log.d("dyb_camera_activity", "bindMediaSaveService");
		Intent intent = new Intent(this, MediaSaveService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	private void unbindMediaSaveService() {
		if (mMediaSaveService != null) {
			mMediaSaveService.setListener(null);
		}
		if (mConnection != null) {
			unbindService(mConnection);
		}
	}

	private void openModule(CameraModule module) {
		Log.v(TAG, "openModule");
		module.init(this, mFrame);
		module.onResumeBeforeSuper();
		module.onResumeAfterSuper();
	}

	private void closeModule(CameraModule module) {
		Log.v(TAG, "closeModule");
		module.onPauseBeforeSuper();
		module.onPauseAfterSuper();
		mFrame.removeAllViews();
	}

	private class MyOrientationEventListener extends OrientationEventListener {
		public MyOrientationEventListener(Context context) {
			super(context);
		}

		@Override
		public void onOrientationChanged(int orientation) {
			// We keep the last known orientation. So if the user first orient
			// the camera then point the camera to floor or sky, we still have
			// the correct orientation.
			if (orientation == ORIENTATION_UNKNOWN)
				return;
			mLastRawOrientation = orientation;
			mOrientation = Util.roundOrientation(orientation, mOrientation);
			mCurrentModule.onOrientationChanged(orientation);
		}
	}

	public SurfaceTexture getSurfaceTexture() {
		return mGlRootView.getSurfaceTexture();
	}

	public CameraSurfaceView getGLRootView() {
		return mGlRootView;
	}

	public MediaSaveService getMediaSaveService() {
		return mMediaSaveService;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent m) {
		if (m.getActionMasked() == MotionEvent.ACTION_DOWN) {
		}
		// if ((mSwitcher != null) && mSwitcher.showsPopup() &&
		// !mSwitcher.isInsidePopup(m)) {
		// return mSwitcher.onTouch(null, m);
		// } else if ((mSwitcher != null) && mSwitcher.isInsidePopup(m)) {
		// return superDispatchTouchEvent(m);
		// } else {
		return mCurrentModule.dispatchTouchEvent(m);
		// }
	}

	public boolean superDispatchTouchEvent(MotionEvent m) {
		return super.dispatchTouchEvent(m);
	}
    
	@Override  
	public void surfaceCreated() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Log.d(PERFORMANCE_TAG, "surface created");
				if (mCameraDevice != null) {
					// ensure camera is opened
					mCurrentModule.restartPreview();
				}
			}
		});
	}

	public void doChangeCamera(int i) {
		Log.v(ChangeModuleTag, "closeModule begin");
		CameraHolder.instance().keep();
		closeModule(mCurrentModule);

		// tmp for change to scan and pano while camera is front
		if ( i == PANORAMA_MODULE_INDEX) {
			if (mCameraId != 0) {
				closeCamera();
				openCameraAsync(0);
				getCameraDevice();
			}
		}

		Log.v(ChangeModuleTag, "closeModule end");
		Log.v(ChangeModuleTag, "openModule begin");

		switch (i) {
		case VIDEO_MODULE_INDEX:
			mCurrentModule = new VideoModule();
			Log.d("dyb", "change to video module");
			break;
		case PHOTO_MODULE_INDEX:
			mCurrentModule = new PhotoModule();
			break;
		case SCANNER_MODULE_INDEX:
			mCurrentModule = new PhotoModule();
			break;
		case PANORAMA_MODULE_INDEX:
			mCurrentModule = new PanoramaModule();
			break;
		}
		openModule(mCurrentModule);
		mCurrentModule.onOrientationChanged(mLastRawOrientation);
		if (mMediaSaveService != null) {
			mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
		}
		Log.v(ChangeModuleTag, "openModule end");
	}

	@Override
	public void onSwipe(int direction) {
		mCurrentModule.onSwipe(direction);
	}

	private boolean sFirsTimeEntering = true;

	public void setFirstTimeEntering(boolean entering) {
		sFirsTimeEntering = entering;
	}

	public boolean isFirstTimeEntering() {
		return sFirsTimeEntering;
	}
     //聚焦图片显示
	public void flipSurfaceCover(Bitmap bitmap, int isIncrease,
			boolean isFullscreen) {
		float screenRatio = mGlRootView.getSurfaceRatio();
		RotateAnimation mAnimation = new RotateAnimation(540, 960,
				isIncrease == 0 ? RotateAnimation.ROTATE_DECREASE
						: RotateAnimation.ROTATE_INCREASE);
		mAnimation.setDuration(500);
		mSurfaceCoverImageView.setImageBitmap(bitmap);
		mSurfaceCoverImageView.setFullscreen(isFullscreen, bitmap.getHeight()
				/ (float) bitmap.getWidth(), screenRatio);
		mAnimation.setAnimationListener(new AnimationListener() {

			@Override
			public void onAnimationStart(Animation arg0) {
				mGlRootView.setVisibility(View.GONE);
				mSurfaceCoverImageView.setVisibility(View.VISIBLE);
			}

			@Override
			public void onAnimationRepeat(Animation arg0) {
			}

			@Override
			public void onAnimationEnd(Animation arg0) {
				mSurfaceCoverImageView.setVisibility(View.GONE);
				mGlRootView.setVisibility(View.VISIBLE);
				mCurrentModule.onFlipAnimationEnd();
			}
		});
		mSurfaceCoverImageView.startAnimation(mAnimation);
	}

	public void setPreviewSuspended(boolean suspended) {
		mGlRootView.setSuspended(suspended);
	}
     
	public Thumbnail getPhotoThumbnail() {
		mPhotoThumbnail = Thumbnail
				.getLastThumbnail(getContentResolver(), true);
		if (mPhotoThumbnail == null) {
			// Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
			// R.drawable.ic_camera_album_photo);
			mPhotoThumbnail = new Thumbnail(null, null, 0);
		}
		return mPhotoThumbnail;
	}

	public Thumbnail getVideoThumbnail() {
		mVideoThumbnail = Thumbnail.getLastThumbnail(getContentResolver(),
				false);
		if (mVideoThumbnail == null) {
			// Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
			// R.drawable.ic_camera_album_video);
			mVideoThumbnail = new Thumbnail(null, null, 0);
		}
		return mVideoThumbnail;
	}

	public int getOrientation() {
		return mOrientation;
	}
     //更新底部video和photo显示
	class UpdateThumbnailTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			mPhotoThumbnail = null;
			mVideoThumbnail = null;
			mPhotoThumbnail = getPhotoThumbnail();
			mVideoThumbnail = getVideoThumbnail();
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			ThumbnailLayout pthumbView = (ThumbnailLayout) findViewById(R.id.photo_thumbnail_layout);
			if (mPhotoThumbnail != null && mPhotoThumbnail.getBitmap() != null)
				pthumbView.rotateIn(mPhotoThumbnail.getBitmap(),
						R.drawable.ic_camera_album_photo);
			ThumbnailLayout vthumbView = (ThumbnailLayout) findViewById(R.id.video_thumbnail_layout);
			if (mVideoThumbnail != null && mVideoThumbnail.getBitmap() != null)
				vthumbView.rotateIn(mVideoThumbnail.getBitmap(),
						R.drawable.ic_camera_album_video);
		}
	}

	private void forceUpdateThumbnail() {
		UpdateThumbnailTask mTask = new UpdateThumbnailTask();
		mTask.execute();
	}

	private int mCachedSwipeEvent = -1;

	public void setCachedSwipeEvent(int event) {
		mCachedSwipeEvent = event;
	}

	public int getAndClearCachedSwipeEvent() {
		int ret = mCachedSwipeEvent;
		mCachedSwipeEvent = -1;
		return ret;
	}

	@Override
	public void frameAvailable() {
		mCurrentModule.frameAvailable();
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		long cost = System.currentTimeMillis() - onPauseEndTime;
		Log.d(PERFORMANCE_TAG, "close camera to surface destory " + cost
				+ " ms");
	}
}
