package cc.fotoplace.camera;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import cc.fotoplace.camera.CameraManager.CameraProxy;
import cc.fotoplace.camera.CameraPreference.OnPreferenceChangedListener;
import cc.fotoplace.camera.filters.FilterData;
import cc.fotoplace.camera.network.FilterStoreActivity;
import cc.fotoplace.camera.platform.PlatformHelper;
import cc.fotoplace.camera.ui.CameraSurfaceView;
import cc.fotoplace.camera.ui.EffectPanel.EffectListener;
import cc.fotoplace.camera.ui.ModuleIndicatorPanel.AnimatioinCallback;
import cc.fotoplace.gallery3d.exif.ExifInterface;
import cc.fotoplace.gallery3d.exif.ExifTag;
import cc.fotoplace.gallery3d.exif.Rational;

import com.yunos.camera.ImageProcessNativeInterface;

public class PhotoModule extends CameraModule implements PhotoController,
		FocusOverlayManager.Listener, OnPreferenceChangedListener,
		ShutterButton.OnShutterButtonListener, MediaSaveService.Listener,
		SensorEventListener, PreviewCallback, AnimatioinCallback,
		EffectListener {

	private static final String TAG = "CAM_PhotoModule";
	private static final String PERFORMANCE_TAG = "CameraPerformanceTag";

	// We number the request code from 1000 to avoid collision with Gallery.
	private static final int REQUEST_CROP = 1000;

	// handler message
	private static final int SETUP_PREVIEW = 1;
	private static final int FIRST_TIME_INIT = 2;
	private static final int CLEAR_SCREEN_DELAY = 3;
	private static final int CHECK_DISPLAY_ROTATION = 4;
	private static final int SWITCH_CAMERA = 5;
	private static final int CAMERA_OPEN_SUCCESS = 6;
	private static final int START_PREVIEW_DONE = 8;
	private static final int CHANGE_MODULE = 9;
	private static final int DISMISS_ZOOM_UI = 10;

	// The subset of parameters we need to update in setCameraParameters().
	private static final int UPDATE_PARAM_INITIALIZE = 1;
	private static final int UPDATE_PARAM_ZOOM = 2;
	private static final int UPDATE_PARAM_PREFERENCE = 4;
	private static final int UPDATE_PARAM_ALL = -1;

	// This is the timeout to keep the camera in onPause for the first time
	// after screen on if the activity is started from secure lock screen.
	private static final int KEEP_CAMERA_TIMEOUT = 1000; // ms

	private static final int ZOOM_STARTED = 0;
	private static final int ZOOM_STOPPED = 1;
	private static final int ZOOM_STOPPING = 2;

	private int zoomState = ZOOM_STOPPED;
	// copied from Camera hierarchy
	private CameraProxy mCameraDevice;
	private int mCameraId;
	private Parameters mParameters;
	private boolean mPaused = false;
	private ComboPreferences mPreferences;
	private PhotoUI mUI;
	private boolean mQuickCapture;
	private boolean mIsInReviewMode = false;
	private final PostViewPictureCallback mPostViewPictureCallback = new PostViewPictureCallback();
	private final RawPictureCallback mRawPictureCallback = new RawPictureCallback();

	private final Object mAutoFocusMoveCallback = new AutoFocusMoveCallback();//设置相机自动对焦移回调。
	private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();//调接口用于通知完成相机自动对焦 
	// these are only used by Camera

	// The activity is going to switch to the specified camera id. This is
	// needed because texture copy is done in GL thread. -1 means camera is notß
	// switching.
	protected int mPendingSwitchCameraId = -1;//切换相机id
	private boolean mIsRecordLocation = false;  //是否需要记录位置
	// When setCameraParametersWhenIdle() is called, we accumulate the subsets
	// needed to be updated in mUpdateSet.
	private int mUpdateSet;
	private FocusOverlayManager mFocusManager;
	private static final int SCREEN_DELAY = 2 * 60 * 1000;

	private int mZoomValue; // The current zoom value.

	private Parameters mInitialParams;
	private boolean mFocusAreaSupported;//是否支持聚焦
	private boolean mMeteringAreaSupported;/// 是否支持测光区域
	private boolean mAeLockSupported;   //曝光
	private boolean mAwbLockSupported;
	private boolean mSmoothZoomSupported;
	private boolean mIsForceAutoFocus;   //是否自动对焦
	private long mShutterCallbackTime;
	private long mPostViewPictureCallbackTime;
	private long mRawPictureCallbackTime;
	private long mJpegPictureCallbackTime;
	private long mShutterTime;
	private byte[] mJpegImageData;

	// These latency time are for the CameraLatency test.
	public long mAutoFocusTime;
	public long mShutterLag;
	public long mShutterToPictureDisplayedTime;
	public long mPictureDisplayedToJpegCallbackTime;
	public long mJpegCallbackFinishTime;
	public long mCaptureStartTime;
	private long mCameraOpenedTime;
	// The degrees of the device rotated clockwise from its natural orientation.
	private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
	private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();
	private static final String sTempCropFilename = "crop-temp";

	private ContentProviderClient mMediaProviderClient;

	// mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
	private String mCropValue;
	private Uri mSaveUri;
	private PreferenceGroup mPreferenceGroup;
	private byte[] mPreviewData;
	private int mPendingModuleIndex = CameraActivity.UNKNOWN_MODULE_INDEX;

	// Face Beauty
	private boolean mIsBeautyOn = false;
	private Bitmap mFaceBeautyBitmap = null;
	// HDR
	private boolean mIsHDROn = false;
	// Lines
	private boolean mIsLinesOn = false;
	private String mFlashMode;
	// UT Variables
	private int mUTFocusCounter = 0;
	private boolean mIsFullScreen = false;

	private boolean mToLaunchFilterStore = false;
	/**
	 * An unpublished intent flag requesting to return as soon as capturing is
	 * completed. TODO: consider publishing by moving into MediaStore.
	 */
	private static final String EXTRA_QUICK_CAPTURE = "android.intent.extra.quickCapture";

	// The display rotation in degrees. This is only valid when mCameraState is
	// not PREVIEW_STOPPED.
	private int mDisplayRotation;
	// The value for android.hardware.Camera.setDisplayOrientation.
	private int mCameraDisplayOrientation;
	// The value for UI components like indicators.
	private int mDisplayOrientation;
	// The value for android.hardware.Camera.Parameters.setRotation.
	private int mJpegRotation;
	private boolean mFirstTimeInitialized;
	private boolean mIsImageCaptureIntent;//是否是外部调用相机
	private boolean mSnapshotOnIdle = false;

	private ContentResolver mContentResolver;

	private LocationManager mLocationManager;//位置管理工具类

	private SensorManager mSensorManager;  //管理传感器
	private float[] mGData = new float[3];
	private float[] mMData = new float[3];
	private float[] mR = new float[16];
	private int mHeading = -1;
	// private boolean isSurfaceTextureNull;
	private boolean mIsAutoFocusCallback = false;
	private final MainHandler mHandler = new MainHandler();

	private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener = new MediaSaveService.OnMediaSavedListener() {
		@Override
		public void onMediaSaved(Uri uri) {
			if (uri != null) {
				Log.v("mk", "uri = " + uri.toString());
				mActivity.addSecureAlbumItemIfNeeded(false, uri);
				Util.broadcastNewPicture(mActivity, uri);
				mUI.updateThumbnail();
			}
		}
	};

	// We use a queue to generated names of the images to be used later
	// when the image is ready to be saved.
	private NamedImages mNamedImages;
	private boolean isEffect = false;
	private long onResumeStartTime;

	/**
	 * This Handler is used to post message back onto the main thread of the
	 * application
	 */
	private class MainHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case SETUP_PREVIEW: {
				setupPreview();
				break;
			}

			case CLEAR_SCREEN_DELAY: {
				mActivity.getWindow().clearFlags(
						WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				break;
			}

			case FIRST_TIME_INIT: {
				initializeFirstTime();
				break;
			}

			case CHECK_DISPLAY_ROTATION: {
				// Set the display orientation if display rotation has
				// changed.
				// Sometimes this happens when the device is held upside
				// down and camera app is opened. Rotation animation will
				// take some time and the rotation value we have got may be
				// wrong. Framework does not have a callback for this now.
				if (Util.getDisplayRotation(mActivity) != mDisplayRotation) {
					setDisplayOrientation();
				}
				break;
			}

			case SWITCH_CAMERA: {
				switchCamera();
				break;
			}

			case CAMERA_OPEN_SUCCESS: {
				onCameraOpened();
				break;
			}

			case START_PREVIEW_DONE: {
				onPreviewStarted();
				break;
			}

			case CHANGE_MODULE: {
				mActivity.doChangeCamera(mPendingModuleIndex);
				mPendingModuleIndex = CameraActivity.UNKNOWN_MODULE_INDEX;
				break;
			}

			case DISMISS_ZOOM_UI: {
				mUI.hideZoom();
				break;
			}
			}
		}
	}
  
	protected void setCameraId(int cameraId) {
		ListPreference pref = mPreferenceGroup
				.findPreference(CameraSettings.KEY_CAMERA_ID);
		if (pref != null)
			pref.setValue("" + cameraId);
	}
     //初始化FocusOverlayManager
	private void initializeFocusManager() {
		Log.v(TAG, "initializeFocusManager");
		// Create FocusManager object. startPreview needs it.
		// if mFocusManager not null, reuse it
		// otherwise create a new instance
		if (mFocusManager != null) {
			mFocusManager.removeMessages();
		} else {
			CameraInfo infos[] = CameraHolder.instance().getCameraInfo();
			CameraInfo info;
			boolean mirror;

			// sometimes infos[] is null
			if (infos != null && infos.length > 0) {
				info = infos[mCameraId];
				mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
			} else {
				// in most platform cameraId=0 means backcamera, cameraId=1
				// means frontcamera
				mirror = (mCameraId == 1);
			}

			String[] defaultFocusModes = mActivity
					.getResources()
					.getStringArray(R.array.pref_camera_focusmode_default_array);
			mFocusManager = new FocusOverlayManager(mPreferences,
					defaultFocusModes, mInitialParams, this, mirror,
					mActivity.getMainLooper(), mUI);
		}
	}
	//得到相机getParameters

	private void initializeCapabilities() {
		mInitialParams = mCameraDevice.getParameters();
		mFocusAreaSupported = Util.isFocusAreaSupported(mInitialParams);
		mMeteringAreaSupported = Util.isMeteringAreaSupported(mInitialParams);
		mAeLockSupported = Util.isAutoExposureLockSupported(mInitialParams);
		mAwbLockSupported = Util
				.isAutoWhiteBalanceLockSupported(mInitialParams);
	}

	// Snapshots can only be taken after this is called. It should be called
	// once only. We could have done these things in onCreate() but we want to
	// make preview screen appear as soon as possible.
	private void initializeFirstTime() {
		if (mFirstTimeInitialized)
			return;

		initializeLocationSettings();
		keepMediaProviderInstance();

		mUI.initializeFirstTime();
		MediaSaveService s = mActivity.getMediaSaveService();
		// We set the listener only when both service and shutterbutton
		// are initialized.
		if (s != null) {
			s.setListener(this);
		}

		mNamedImages = new NamedImages();
		mFirstTimeInitialized = true;

		addIdleHandler();
		mActivity.updateStorageSpaceAndHint();
	}
  //比较适合那种需要在将来执行操作，但是又不知道需要指定多少延迟时间的操作。
	private static final MessageQueue.IdleHandler mIdleHandler = new MessageQueue.IdleHandler() {
		@Override
		public boolean queueIdle() {
			Storage.ensureOSXCompatible();
			return false;
		}
	};

	boolean mIdleHandlerScheduled = false;

	private void addIdleHandler() {
		if (!mIdleHandlerScheduled) {
			MessageQueue queue = Looper.myQueue();
			queue.addIdleHandler(mIdleHandler);
			mIdleHandlerScheduled = true;
		}
	}

	private void removeIdleHandler() {
		if (mIdleHandlerScheduled) {
			MessageQueue queue = Looper.myQueue();
			queue.removeIdleHandler(mIdleHandler);
			mIdleHandlerScheduled = false;
		}
	}

	// If the activity is paused and resumed, this method will be called in
	// onResume.
	private void initializeSecondTime() {
		MediaSaveService s = mActivity.getMediaSaveService();
		initializeLocationSettings();
		if (s != null) {
			s.setListener(this);
		}
		mNamedImages = new NamedImages();
		mUI.initializeSecondTime(mParameters);
		keepMediaProviderInstance();
	}
    //初始化LocationManager
	private void initializeLocationSettings() {
		mIsRecordLocation = mPreferences.getBoolean(
				CameraSettings.KEY_RECORD_LOCATION, true);
		mLocationManager.recordLocation(mIsRecordLocation);
	}
   //切换相机摄像头
	private void switchCamera() {
		Log.v(TAG, "switchCamera");
		if (mPaused)
			return;

		mParameters = mCameraDevice.getParameters();
		Size preivewSize = mParameters.getPreviewSize();
		if (PlatformHelper.supportSwitchBlur()) {
			mActivity.getGLRootView().startFadeIn(preivewSize.width,
					preivewSize.height, mPreviewData, mCameraId);
		}
		// mActivity.flipSurfaceCover(previewBitmap, mCameraId, isFullscreen);
		mCameraId = mPendingSwitchCameraId;
		mPendingSwitchCameraId = -1;
		setCameraId(mCameraId);
		// from onPause
		mUI.animateBeforeSwitchingCamera(mCameraId);
		stopPreview();
		if (mFocusManager != null)
			mFocusManager.removeMessages();
		mActivity.closeCamera();
		mCameraDevice = null;
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
		mFocusManager.onCameraReleased();

		// Restart the camera and initialize the UI. From onCreate.
		mPreferences.setLocalId(mActivity, mCameraId);
		CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

		mActivity.openCameraAsync(mCameraId);
		mCameraDevice = mActivity.getCameraDevice();
		if (mCameraDevice == null) {
			Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
			return;
		}

		mParameters = mCameraDevice.getParameters();
		mFocusManager.setParameters(mParameters);

		initializeCapabilities();
		setupPreview();
		mUI.animateAfterSwitchingCamera(mCameraId);
		openCameraCommon();
	}
    //设置setCameraParameters
	private void setCameraParameters(int updateSet) {
		if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
			updateCameraParametersInitialize();
		}
		if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
			updateCameraParametersZoom();
		}
		if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
			updateCameraParametersPreference();
			mIsAutoFocusCallback = false;
		}
		mCameraDevice.setParameters(mParameters);
	}

	private void updateCameraParametersInitialize() {
		// Reset preview frame rate to the maximum because it may be lowered by
		// video camera application.
		List<Integer> frameRates = mParameters.getSupportedPreviewFrameRates();
		if (frameRates != null) {
			Integer max = Collections.max(frameRates);
			mParameters.setPreviewFrameRate(max);
		}

		mParameters.set(Util.RECORDING_HINT, Util.FALSE);

		// Disable video stabilization. Convenience methods not available in API
		// level <= 14
		String vstabSupported = mParameters
				.get("video-stabilization-supported");
		if ("true".equals(vstabSupported)) {
			mParameters.set("video-stabilization", "false");
		}
	}
 
	private void updateCameraParametersZoom() {
		// Set zoom.
		if (mParameters.isZoomSupported()) {
			mParameters.setZoom(mZoomValue);
		}
	}

	private void updateCameraParametersPreference() {
		Log.v(TAG, "updateCameraParametersPreference");
		setAutoExposureLockIfSupported();
		setAutoWhiteBalanceLockIfSupported();
		setFocusAreasIfSupported();
		setMeteringAreasIfSupported();
		PlatformHelper.setZSD(isZsdOn(), mParameters);
		mSmoothZoomSupported = mParameters.isSmoothZoomSupported();
		mParameters.set(CameraSettings.KEY_CAP_MODE,
				CameraSettings.CAP_MODE_NORMAL);

		int index = getPhotoSizePreference();
		boolean isFull = CameraSettings.getFullScreenPreference(mActivity,
				mPreferences);
		mIsFullScreen = isFull;
		Size picSize = CameraSettings.getPicSize(
				mParameters.getSupportedPictureSizes(), isFull, isEffect
						|| mIsBeautyOn, index);
		mParameters.setPictureSize(picSize.width, picSize.height);
		List<Size> size = mParameters.getSupportedPreviewSizes();

		Size optimalSize = Util.getOptimalPreviewSize(mActivity, size,
				(double) picSize.width / picSize.height);

		Size original = mParameters.getPreviewSize();
		if (!original.equals(optimalSize)) {
			mParameters.setPreviewSize(optimalSize.width, optimalSize.height);
			mFocusManager.overrideFocusMode(null);
			mParameters.setFocusMode(mFocusManager.getFocusMode());
			updateAutoFocusMoveCallback();
			mParameters = mCameraDevice.getParameters();
		}
		Log.v("dyb", "Preview size is " + optimalSize.width + "x"
				+ optimalSize.height);

		mParameters.setJpegQuality(100);
		mParameters.setAntibanding(Parameters.ANTIBANDING_AUTO);
		mFlashMode = mPreferences.getString(CameraSettings.KEY_FLASH_MODE,
				mActivity.getString(R.string.pref_camera_flashmode_default));
		List<String> supportedFlash = mParameters.getSupportedFlashModes();
		if (Util.isSupported(mFlashMode, supportedFlash)) {
			mParameters.setFlashMode(mFlashMode);
		} else {
			mFlashMode = mParameters.getFlashMode();
			if (mFlashMode == null) {
				mFlashMode = mActivity
						.getString(R.string.pref_camera_flashmode_no_flash);
			}
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			String key = mActivity
					.getString(R.string.camera_setting_item_shutter_sound_key);
			boolean shutterSound = mPreferences.getBoolean(key, true);
			if (shutterSound) {
				mCameraDevice.enableShutterSound(true);
			} else {
				mCameraDevice.enableShutterSound(false);
			}
		}
		mFocusManager.overrideFocusMode(null);
		mParameters.setFocusMode(mFocusManager.getFocusMode());
		updateAutoFocusMoveCallback();
	}

	private void setAutoExposureLockIfSupported() {
		if (mAeLockSupported) {
			mParameters.setAutoExposureLock(mFocusManager.getAeAwbLock());
		}
	}

	private void setAutoWhiteBalanceLockIfSupported() {
		if (mAwbLockSupported) {
			mParameters.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
		}
	}

	private void setFocusAreasIfSupported() {
		if (mFocusAreaSupported && !mIsAutoFocusCallback) {
			mParameters.setFocusAreas(mFocusManager.getFocusAreas());
		}
	}

	private void setMeteringAreasIfSupported() {
		if (mMeteringAreaSupported && !mIsAutoFocusCallback) {
			// Use the same area for focus and metering.
			mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
		}
	}

	private void onPreviewStarted() {
		mActivity.setPreviewSuspended(false);
		startFaceDetection();
		mParameters = mCameraDevice.getParameters();
		Size previewSize = mParameters.getPreviewSize();
		mIsFullScreen = CameraSettings.getFullScreenPreference(mActivity,
				mPreferences);
		mActivity.getGLRootView().setPreviewSize(previewSize.width,
				previewSize.height, !mIsFullScreen);
		int size[] = new int[3];
		mActivity.getGLRootView().getDrawingAreaSize(size);
		mFocusManager.setPreviewSize(size[0], size[1], size[2]);
		mIsForceAutoFocus = isForcedAutoFocus();
		mFocusManager.setForceAutoFocus(mIsForceAutoFocus);
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
		Log.i(TAG, "onPreviewStarted end");
	}

	private void onCameraOpened() {
		Log.v(TAG, "onCameraOpened");

		mParameters = mCameraDevice.getParameters();
		long start = System.currentTimeMillis();

		if (mIsInReviewMode)
			mActivity.getGLRootView().setReviewMode();
		long beforeStartPreview = System.currentTimeMillis();
		long openToStartTime = beforeStartPreview - mCameraOpenedTime;
		long initializeCapabilitiesTiem = beforeStartPreview - start;
		Log.d(PERFORMANCE_TAG, "initializeCapabilities Time is"
				+ initializeCapabilitiesTiem);
		Log.d(PERFORMANCE_TAG, "open to start time is " + openToStartTime);
		startPreview();
		long startPreviewTime = System.currentTimeMillis() - beforeStartPreview;
		Log.d(PERFORMANCE_TAG, "start preview costs " + startPreviewTime
				+ " ms");
		mToLaunchFilterStore = false;
		openCameraCommon();
	}

	public boolean isCameraIdle() {
		return (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING)
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED)
				|| ((mFocusManager != null) && mFocusManager.isFocusCompleted() && (mActivity
						.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA));
	}

	@Override
	public boolean isSnapshotting() {
		return mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING;
	}

	private void initializeControlByIntent() {
		mUI.initializeControlByIntent();
		if (mIsImageCaptureIntent) {
			Log.v(TAG, "init ImageCaptureIntent param");
			setupCaptureParams();
		}
	}

	private void setupCaptureParams() {
		Bundle myExtras = mActivity.getIntent().getExtras();
		if (myExtras != null) {
			mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
			mCropValue = myExtras.getString("crop");
			Log.v(TAG, "mSaveUri:"
					+ ((mSaveUri != null) ? mSaveUri.getPath() : ""));
			Log.v(TAG, "mCropValue:" + mCropValue);
		}
	}

	// This can be called by UI Thread or CameraStartUpThread. So this should
	// not modify the views.
	private void startPreview() {
		Log.v(TAG, "startPreview");
		mCameraDevice.setErrorCallback(mErrorCallback);
		// ICS camera frameworks has a bug. Face detection state is not cleared
		// after taking a picture. Stop the preview to work around it. The bug
		// was fixed in JB.
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
			stopPreview();
		}

		setDisplayOrientation();
		if (!mSnapshotOnIdle) {
			// If the focus mode is continuous autofocus, call cancelAutoFocus
			// to
			// resume it because it may have been paused by autoFocus call.
			if (mFocusManager != null
					&& Util.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager
							.getFocusMode())) {
				mCameraDevice.cancelAutoFocus();
			}
			mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
		}
		setCameraParameters(UPDATE_PARAM_ALL);
		if (ApiHelper.HAS_SURFACE_TEXTURE) {
			CameraSurfaceView mSurfaceView = (CameraSurfaceView) mActivity
					.getGLRootView();
			if (mUI.getSurfaceTexture() == null) {
				mUI.setSurfaceTexture(mSurfaceView.getSurfaceTexture());
			}
			mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
			Object st = mUI.getSurfaceTexture();
			if (st != null) {
				mCameraDevice.setPreviewTextureAsync((SurfaceTexture) st);
				// isSurfaceTextureNull = false;
			} else {
				return;
			}
		} else {
			mCameraDevice.setDisplayOrientation(mDisplayOrientation);
		}

		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING) {
			Log.v(TAG, "CameraDevice startPreview");
			mCameraDevice.startPreviewAsync();
		}
		mFocusManager.onPreviewStarted();
		mActivity.getGLRootView().startPreview(mCameraId);
		mHandler.sendEmptyMessage(START_PREVIEW_DONE);
	}

	// either open a new camera or switch cameras
	private void openCameraCommon() {
		loadCameraPreferences();
		mUI.onCameraOpened(mPreferenceGroup, mPreferences, mParameters, this);
		mUI.updateLinesButton(mIsLinesOn);
	}

	private void setDisplayOrientation() {
		mDisplayRotation = Util.getDisplayRotation(mActivity);
		mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation,
				mCameraId);
		mCameraDisplayOrientation = Util.getDisplayOrientation(0, mCameraId);
		if (mFocusManager != null) {
			mFocusManager.setDisplayOrientation(mDisplayOrientation);
		}
	}

	// Only called by UI thread.
	private void setupPreview() {
		Log.v(TAG, "setupPreview");
		if (mFocusManager != null)
			mFocusManager.resetTouchFocus();
		startPreview();
	}

	private static class NamedImages {
		private ArrayList<NamedEntity> mQueue;
		private NamedEntity mNamedEntity;

		public NamedImages() {
			mQueue = new ArrayList<NamedEntity>();
		}

		public void nameNewImage(ContentResolver resolver, long date) {
			NamedEntity r = new NamedEntity();
			r.title = Util.createJpegName(date);
			r.date = date;
			mQueue.add(r);
		}

		public String getTitle() {
			if (mQueue.isEmpty()) {
				mNamedEntity = null;
				return null;
			}
			mNamedEntity = mQueue.get(0);
			mQueue.remove(0);

			return mNamedEntity.title;
		}

		// Must be called after getTitle().
		public long getDate() {
			if (mNamedEntity == null)
				return -1;
			return mNamedEntity.date;
		}

		private static class NamedEntity {
			String title;
			long date;
		}
	}

	public void onStateChanged() {
		switch (mActivity.getCameraState()) {
		case CameraActivity.CAMERA_STATE_PREVIEW_STOPPED:
		case CameraActivity.CAMERA_STATE_SNAPSHOTTING:
		case CameraActivity.CAMERA_STATE_SWITCHING_CAMERA:
			mUI.enableGestures(false);
			break;
		case CameraActivity.CAMERA_STATE_PREVIEWING:
		case CameraActivity.CAMERA_STATE_FOCUSING:
			mUI.enableGestures(true);
			break;
		}
	}

	private int getPreferredCameraId(ComboPreferences preferences) {
		int intentCameraId = Util.getCameraFacingIntentExtras(mActivity);
		if (intentCameraId != -1) {
			// Testing purpose. Launch a specific camera through the intent
			// extras.
			return intentCameraId;
		} else {
			if (mActivity.isFirstTimeEntering()) {
				mActivity.setFirstTimeEntering(false);
				CameraSettings.writePreferredCameraId(mPreferences.getGlobal(),
						0);
				return 0;
			}
			return CameraSettings.readPreferredCameraId(preferences);
		}
	}

	@Override
	public void init(CameraActivity activity, View root) {
		Log.v(TAG, "init photo module");
		super.init(activity, root);

		mPendingModuleIndex = -1;
		DisplayMetrics dm = new DisplayMetrics();
		mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
		mActivity.getGLRootView()
				.setScreenSize(dm.widthPixels, dm.heightPixels);

		mPreferences = new ComboPreferences(mActivity);
		mCameraId = getPreferredCameraId(mPreferences);
		mPreferences.setLocalId(mActivity, mCameraId);

		mIsLinesOn = mPreferences.getBoolean(
				CameraSettings.KEY_PHOTO_ALIGNMENT_LINES, false);

		mUI = new PhotoUI(activity, this, root, mPreferences);
		mUI.setListener(this);
		mContentResolver = mActivity.getContentResolver();

		// Surface texture is from camera screen nail and startPreview needs it.
		// This must be done before startPreview.
		mIsImageCaptureIntent = Util
				.isImageCaptureIntent(mActivity.getIntent());

		resetExposureCompensation();

		initializeControlByIntent();
		mQuickCapture = mActivity.getIntent().getBooleanExtra(
				EXTRA_QUICK_CAPTURE, false);
		mLocationManager = new LocationManager(mActivity, mUI);
		mSensorManager = (SensorManager) (mActivity
				.getSystemService(Context.SENSOR_SERVICE));
		isEffect = false;
		mActivity.getGLRootView().setFilter(0, null);

		mCameraDevice = mActivity.getCameraDevice();
		mCameraOpenedTime = System.currentTimeMillis();
		if (mCameraDevice == null) {
			if (activity.isPaused()) {
				Log.v(TAG, "activity is paused, so cancel init");
				return;
			} else {
				Util.showErrorAndFinish(mActivity,
						R.string.cannot_connect_camera);
				return;
			}
		} else {
			stopPreviewAfterInit();
			mHandler.sendEmptyMessage(CAMERA_OPEN_SUCCESS);
			initializeCapabilities();
			if (mFocusManager == null)
				initializeFocusManager();
		}
	}

	private void stopPreviewAfterInit() {
		mParameters = mCameraDevice.getParameters();
		int index = getPhotoSizePreference();
		boolean isFull = CameraSettings.getFullScreenPreference(mActivity,
				mPreferences);

		Size picSize = CameraSettings.getPicSize(
				mParameters.getSupportedPictureSizes(), isFull, isEffect
						|| mIsBeautyOn, index);
		List<Size> size = mParameters.getSupportedPreviewSizes();

		Size optimalSize = Util.getOptimalPreviewSize(mActivity, size,
				(double) picSize.width / picSize.height);

		Size original = mParameters.getPreviewSize();
		if (!original.equals(optimalSize) || isZsdOn() || isHdrOn()) {
			Log.v(TAG, "stop preview after init");
			mCameraDevice.stopPreview();
			mActivity
					.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
		}
	}

	@Override
	public void onFullScreenChanged(boolean full) {
		mUI.onFullScreenChanged(full);
	}

	@Override
	public void onPauseBeforeSuper() {
		super.onPauseBeforeSuper();
		mPaused = true;
		Sensor gsensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (gsensor != null) {
			mSensorManager.unregisterListener(this, gsensor);
		}

		Sensor msensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		if (msensor != null) {
			mSensorManager.unregisterListener(this, msensor);
		}
		mUI.clearFocus();
		mUI.hideSettingPanel();
		if (!mToLaunchFilterStore) {
			mUI.hideEffectsPanel();
		}
	}

	@Override
	public void onPauseAfterSuper() {
		Log.v(TAG, "onPauseAfterSuper");
		// When camera is started from secure lock screen for the first time
		// after screen on, the activity gets
		// onCreate->onResume->onPause->onResume.
		// To reduce the latency, keep the camera for a short time so it does
		// not need to be opened again.
		if (mCameraDevice != null && mActivity.isSecureCamera()
				&& ActivityBase.isFirstStartAfterScreenOn()) {
			ActivityBase.resetFirstStartAfterScreenOn();
			CameraHolder.instance().keep(KEEP_CAMERA_TIMEOUT);
		}

		if (mLocationManager != null)
			mLocationManager.recordLocation(false);

		// Remove the messages in the event queue.
		mHandler.removeMessages(SETUP_PREVIEW);
		mHandler.removeMessages(FIRST_TIME_INIT);
		mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
		mHandler.removeMessages(SWITCH_CAMERA);
		mHandler.removeMessages(CAMERA_OPEN_SUCCESS);
		mHandler.removeMessages(START_PREVIEW_DONE);
		mHandler.removeMessages(DISMISS_ZOOM_UI);

		if (mFocusManager != null)
			mFocusManager.removeMessages();

		if (mCameraDevice != null) {
			if (isZsdOn() || isHdrOn()) {
				Log.v(TAG,
						"if zsd or hdr on, set zsd and hdr to off, and stop-preview needed while onpause");
				PlatformHelper.setZSD(false, mParameters);
				PlatformHelper.setHDR(false, mParameters);
				mCameraDevice.setParameters(mParameters);
				stopPreview();
			}
			mCameraDevice.setZoomChangeListener(null);
			mCameraDevice.setPreviewCallback(null);
			mCameraDevice.setFaceDetectionListener(null);
			mCameraDevice.setErrorCallback(null);
			mCameraDevice.setOneshotPreviewCallback(null);
			mCameraDevice.setAutoFocusMoveCallback(null);
			mCameraDevice = null;
			if (mFocusManager != null) {
				mFocusManager.onCameraReleased();
			}
		}

		removeIdleHandler();
		resetScreenOn();

		mPendingSwitchCameraId = -1;
		MediaSaveService s = mActivity.getMediaSaveService();
		if (s != null) {
			s.setListener(null);
		}
		mIsAutoFocusCallback = false;
	}

	private void resetExposureCompensation() {
		String value = mPreferences.getString(CameraSettings.KEY_EXPOSURE,
				CameraSettings.EXPOSURE_DEFAULT_VALUE);
		if (!CameraSettings.EXPOSURE_DEFAULT_VALUE.equals(value)) {
			Editor editor = mPreferences.edit();
			editor.putString(CameraSettings.KEY_EXPOSURE, "0");
			editor.apply();
		}
	}

	private void resetScreenOn() {
		mHandler.removeMessages(CLEAR_SCREEN_DELAY);
		mActivity.getWindow().clearFlags(
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private void keepScreenOnAwhile() {
		mHandler.removeMessages(CLEAR_SCREEN_DELAY);
		mActivity.getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
	}

	private void keepMediaProviderInstance() {
		// We want to keep a reference to MediaProvider in camera's lifecycle.
		// to do: Utilize mMediaProviderClient instance to replace
		// ContentResolver calls.
		if (mMediaProviderClient == null) {
			mMediaProviderClient = mContentResolver
					.acquireContentProviderClient(MediaStore.AUTHORITY);
		}
	}

	@Override
	public void onResumeBeforeSuper() {
		onResumeStartTime = System.currentTimeMillis();
		mPaused = false;
	}

	@Override
	public void onResumeAfterSuper() {

		if (mCameraDevice == null) {
			mCameraDevice = mActivity.getCameraDevice();
			if (mCameraDevice == null) {
				if (!mActivity.isPaused()) {
					Util.showErrorAndFinish(mActivity,
							R.string.cannot_connect_camera);
				} else {
					Log.v(TAG,
							"activity is paused, so cancel onResumeAfterSuper");
				}
				return;
			} else {
				mHandler.sendEmptyMessage(CAMERA_OPEN_SUCCESS);
				initializeCapabilities();
				if (mFocusManager == null)
					initializeFocusManager();
			}
		}

		mZoomValue = 0;

		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
			resetExposureCompensation();
		}

		// If first time initialization is not finished, put it in the
		// message queue.
		if (!mFirstTimeInitialized) {
			mHandler.sendEmptyMessage(FIRST_TIME_INIT);
		} else {
			initializeSecondTime();
		}
		keepScreenOnAwhile();
		// Initializing it here after the preview is started.
		Sensor gsensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (gsensor != null) {
			mSensorManager.registerListener(this, gsensor,
					SensorManager.SENSOR_DELAY_NORMAL);
		}

		Sensor msensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		if (msensor != null) {
			mSensorManager.registerListener(this, msensor,
					SensorManager.SENSOR_DELAY_NORMAL);
		}
		if (!mActivity.getGLRootView().isSuspended()) {
			mActivity.setPreviewSuspended(false);
		}
		long onResumeCost = System.currentTimeMillis() - onResumeStartTime;
		Log.d(PERFORMANCE_TAG, "on resume cost time " + onResumeCost + " ms");
		super.onResumeAfterSuper();
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
	}

	@Override
	public void onStop() {
		stopPreview();
	}

	@Override
	public void installIntentFilter() {
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	}

	@Override
	public boolean onBackPressed() {
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mPaused) {
			return true;
		}
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return onVolumeKeyDown(event);
		case KeyEvent.KEYCODE_CAMERA:
			if (mFirstTimeInitialized && event.getRepeatCount() == 0
					&& !mIsInReviewMode) {
				onShutterButtonFocus(true);
				mUI.pressShutter(true);
			}
			return true;
		}
		return false;
	}

	private int getFaceBeautyLevel() {
		String key = mActivity.getResources().getString(
				R.string.camera_setting_item_beauty_level_key);
		String defValue = mActivity.getResources().getString(
				R.string.camera_setting_item_beauty_level_default);
		String[] entryValues = mActivity.getResources().getStringArray(
				R.array.entryvalues_camera_settings_beauty_level);
		String resolution = mPreferences.getString(key, defValue);
		if (resolution.equals(entryValues[0])) {
			return 5;
		} else if (resolution.equals(entryValues[1])) {
			return 3;
		} else {
			return 1;
		}
	}

	private int getPhotoSizePreference() {
		String key = mActivity.getResources().getString(
				R.string.camera_setting_item_photo_resolution_key);
		String defValue = mActivity.getResources().getString(
				R.string.camera_setting_item_photo_resolution_default);
		String[] entryValues = mActivity.getResources().getStringArray(
				R.array.entryvalues_camera_settings_photo_resolution);
		String resolution = mPreferences.getString(key, defValue);
		if (resolution.equals(entryValues[0])) {
			return 0;
		} else if (resolution.equals(entryValues[1])) {
			return 1;
		} else {
			return 2;
		}
	}

	private boolean onVolumeKeyDown(KeyEvent event) {
		String[] entryValues = mActivity.getResources().getStringArray(
				R.array.entryvalues_camera_settings_volumne_key_fuction);
		String defValue = mActivity.getResources().getString(
				R.string.camera_setting_item_volume_key_function_default);
		String key = mActivity.getResources().getString(
				R.string.camera_setting_item_volume_key_function_key);
		String function = mPreferences.getString(key, defValue);
		if (function.equals(entryValues[2])) {
			// Volume Adjustment
			return false;
		} else if (function.equals(entryValues[0])) {
			// Shutter
			if (mIsInReviewMode
					|| mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEWING) {
				return true;
			}
			if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
				onShutterButtonFocus(true);
				mUI.pressShutter(true);
			}
			return true;
		} else if (function.equals(entryValues[1])) {
			// Zoom
			if (mIsInReviewMode
					|| mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEWING
					|| mParameters == null) {
				return true;
			}
			if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
				mUI.zoomIn(mParameters);
				mHandler.removeMessages(DISMISS_ZOOM_UI);
				mHandler.sendEmptyMessageDelayed(DISMISS_ZOOM_UI, 1000);
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
				mUI.zoomOut(mParameters);
				mHandler.removeMessages(DISMISS_ZOOM_UI);
				mHandler.sendEmptyMessageDelayed(DISMISS_ZOOM_UI, 1000);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (mPaused) {
			return true;
		}
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return onVolumeKeyUp(event);
		case KeyEvent.KEYCODE_CAMERA:
			if (mFirstTimeInitialized
					&& !mIsInReviewMode
					&& mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
				mUI.pressShutter(false);
				onShutterButtonClick();
			}
			return true;
		case KeyEvent.KEYCODE_MENU:
			break;
		}
		return false;
	}

	private boolean onVolumeKeyUp(KeyEvent event) {
		String[] entryValues = mActivity.getResources().getStringArray(
				R.array.entryvalues_camera_settings_volumne_key_fuction);
		String defValue = mActivity.getResources().getString(
				R.string.camera_setting_item_volume_key_function_default);
		String key = mActivity.getResources().getString(
				R.string.camera_setting_item_volume_key_function_key);
		String function = mPreferences.getString(key, defValue);
		if (function.equals(entryValues[2])) {
			// Volume Adjustment
			return false;
		} else if (function.equals(entryValues[0])) {
			// Shutter
			if (mIsInReviewMode
					|| mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEWING) {
				return true;
			}
			if (mFirstTimeInitialized) {
				mUI.pressShutter(false);
				onShutterButtonClick();
			}
			return true;
		} else if (function.equals(entryValues[1])) {
			// Zoom
			return true;
		}
		return false;
	}

	@Override
	public void onSingleTapUp(View view, int x, int y) {
		Log.d("dyb", "onSingleTapUp");
		if (mPaused
				|| mCameraDevice == null
				|| !mFirstTimeInitialized
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED
				|| mPendingModuleIndex != CameraActivity.UNKNOWN_MODULE_INDEX
				|| mIsInReviewMode) {
			Log.d("mk", "mFirstTimeInitialized=" + mFirstTimeInitialized
					+ "  camera state:" + mActivity.getCameraState());
			return;
		}
		// Check if metering area or focus area is supported.
		if (!mFocusAreaSupported && !mMeteringAreaSupported)
			return;
		// Check if tap area is inside
		mFocusManager.onSingleTapUp(x, y);
		mUI.hideSettingPanel();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent m) {
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA)
			return true;
		return mUI.dispatchTouchEvent(m);
	}

	@Override
	public void onPreviewTextureCopied() {

	}

	@Override
	public void onCaptureTextureCopied() {
	}

	@Override
	public void onUserInteraction() {
	}

	@Override
	public boolean updateStorageHintOnResume() {
		return true;
	}

	@Override
	public void onOrientationChanged(int orientation) {
		// We keep the last known orientation. So if the user first orient
		// the camera then point the camera to floor or sky, we still have
		// the correct orientation.
		if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN)
			return;
		mOrientation = Util.roundOrientation(orientation, mOrientation);

		int newOrientation = Util.roundOrientation(orientation, mOrientation);
		if (mOrientation != newOrientation) {
			mOrientation = newOrientation;
		}

		mUI.onOrientationChanged(mOrientation);
	}

	@Override
	public void onQueueStatus(boolean full) {
		Log.d("mk", "onQueueStatus " + full);
		mUI.enableShutter(!full);
	}

	@Override
	public void onMediaSaveServiceConnected(MediaSaveService s) {
		// We set the listener only when both service and shutter button
		// are initialized.
		Log.d("mk", "onMediaSaveServiceConnected");
		if (mFirstTimeInitialized) {
			s.setListener(this);
		}
	}

	@Override
	public void onShutterButtonFocus(boolean pressed) {
		if (mPaused
				|| mUI.collapseCameraControls()
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING)
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED))
			return;

		// mCameraDevice.setOneshotPreviewCallback(this);
		// Do not do focus if there is not enough storage.
		if (pressed && !canTakePicture())
			return;

		mUI.hideSettingPanel();
		mUI.hideEffectsPanel();

		if (pressed) {
			if (mFocusAreaSupported && mIsForceAutoFocus) {
				mParameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
				mCameraDevice.setParameters(mParameters);
			}
			mFocusManager.onShutterDown();
		} else {
			// for countdown mode, we need to postpone the shutter release
			// i.e. lock the focus during countdown.
			mFocusManager.onShutterUp();
		}
	}

	@Override
	public void onShutterButtonClick() {
		if (mPaused
				|| mUI.collapseCameraControls()
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA)
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED)
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING))
			return;
		Log.v(TAG, "onShutterButtonClick");
		mUI.hideSettingPanel();
		mUI.hideEffectsPanel();
		// Do not take the picture if there is not enough storage.
		if (mActivity.getStorageSpace() <= Storage.LOW_STORAGE_THRESHOLD) {
			Log.d("mk", "Not enough space or storage not ready. remaining="
					+ mActivity.getStorageSpace());
			return;
		}

		Log.d("mk",
				"onShutterButtonClick: mCameraState="
						+ mActivity.getCameraState());

		// If the user wants to do a snapshot while the previous one is still
		// in progress, remember the fact and do it after we finish the previous
		// one and re-start the preview. Snapshot in progress also includes the
		// state that autofocus is focusing and a picture will be taken when
		// focus callback arrives.
		if ((mFocusManager.isFocusingSnapOnFinish() || mActivity
				.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING)
				&& !mIsImageCaptureIntent) {
			mSnapshotOnIdle = true;
			return;
		}

		mSnapshotOnIdle = false;
		mFocusManager.doSnap();
	}

	@Override
	public int onZoomChanged(int index, boolean isSmooth) {
		Log.d("dyb", "photo module on zoom value change " + index);
		// Not useful to change zoom value when the activity is paused.
		if (mPaused) {
			return index;
		}
		mUI.hideSettingPanel();
		if (mCameraDevice == null) {
			return index;
		}
		// Set zoom parameters asynchronously

		if (isSmooth && mSmoothZoomSupported && mZoomValue != index) {
			mZoomValue = index;
			if (zoomState == ZOOM_STARTED) {
				zoomState = ZOOM_STOPPING;
				mCameraDevice.stopSmoothZoom();
			} else if (zoomState == ZOOM_STOPPED) {
				mCameraDevice.startSmoothZoom(mZoomValue);
				zoomState = ZOOM_STARTED;
			}
		} else {
			mZoomValue = index;
			mParameters.setZoom(mZoomValue);
			mCameraDevice.setParameters(mParameters);
		}
		return index;
	}

	@Override
	public void onCaptureDone() {
		Log.v(TAG, "onCaptureDone()");
		if (mPaused) {
			return;
		}

		if (mCropValue == null) {
			// First handle the no crop case -- just return the value. If the
			// caller specifies a "save uri" then write the data to its
			// stream. Otherwise, pass back a scaled down version of the bitmap
			// directly in the extras.
			if (mSaveUri != null) {
				OutputStream outputStream = null;
				try {
					outputStream = mContentResolver.openOutputStream(mSaveUri);
					outputStream.write(mJpegImageData);
					outputStream.close();

					mActivity.setResult(Activity.RESULT_OK);
					mActivity.finish();
				} catch (IOException ex) {
					ex.printStackTrace();
					// BugID:112981: IOException may happen when SD Card is
					// error.
					Toast.makeText(mActivity,
							mActivity.getString(R.string.access_sd_fail),
							Toast.LENGTH_SHORT).show();
					mActivity.setResult(Activity.RESULT_CANCELED);
					mActivity.finish();
				} catch (NullPointerException e) {
					// BugID:113150: NullPointer will happen when target address
					// cannot be accessed
					// Case 1: outputStream =
					// mContentResolver.openOutputStream(mSaveUri);
					// Case 2: outputStream.write(mJpegImageData);
					Toast.makeText(mActivity,
							mActivity.getString(R.string.access_sd_fail),
							Toast.LENGTH_SHORT).show();
					mActivity.setResult(Activity.RESULT_CANCELED);
					mActivity.finish();
				} finally {
					Util.closeSilently(outputStream);
				}
			} else {
				ExifInterface exif = Exif.getExif(mJpegImageData);
				int orientation = Exif.getOrientation(exif);
				Bitmap bitmap = Util.makeBitmap(mJpegImageData, 50 * 1024);
				bitmap = Util.rotate(bitmap, orientation);
				mActivity.setResult(Activity.RESULT_OK, new Intent(
						"inline-data").putExtra("data", bitmap));
				mActivity.finish();
			}
		} else {
			// Save the image to a temp file and invoke the cropper
			Uri tempUri = null;
			FileOutputStream tempStream = null;
			try {
				File path = mActivity.getFileStreamPath(sTempCropFilename);
				path.delete();
				tempStream = mActivity.openFileOutput(sTempCropFilename, 0);
				tempStream.write(mJpegImageData);
				tempStream.close();
				tempUri = Uri.fromFile(path);
			} catch (FileNotFoundException ex) {
				ex.printStackTrace();
				mActivity.setResult(Activity.RESULT_CANCELED);
				mActivity.finish();
				return;
			} catch (IOException ex) {
				ex.printStackTrace();
				mActivity.setResult(Activity.RESULT_CANCELED);
				mActivity.finish();
				return;
			} finally {
				Util.closeSilently(tempStream);
			}

			Bundle newExtras = new Bundle();
			if (mCropValue.equals("circle")) {
				newExtras.putString("circleCrop", "true");
			}
			if (mSaveUri != null) {
				newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
			} else {
				newExtras.putBoolean(CropExtras.KEY_RETURN_DATA, true);
			}
			if (mActivity.isSecureCamera()) {
				newExtras.putBoolean(CropExtras.KEY_SHOW_WHEN_LOCKED, true);
			}

			Intent cropIntent = new Intent("com.android.camera.action.CROP");

			cropIntent.setData(tempUri);
			cropIntent.putExtras(newExtras);

			mActivity.startActivityForResult(cropIntent, REQUEST_CROP);
		}
	}

	@Override
	public void onCaptureCancelled() {
		mIsInReviewMode = false;
		mActivity.setResult(Activity.RESULT_CANCELED, new Intent());
		mActivity.finish();
	}

	@Override
	public void onCaptureRetake() {
		mIsInReviewMode = false;
		Log.v("mk", "onCaptureRetake()");
		if (mPaused)
			return;
		mUI.hidePostCaptureAlert();
		setupPreview();
	}

	@Override
	public void cancelAutoFocus() {
		mCameraDevice.cancelAutoFocus();
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_FOCUSING) {
			mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
		}
		setCameraParameters(UPDATE_PARAM_PREFERENCE);
	}

	@Override
	public void stopPreview() {
		Log.v(TAG, "stopPreview");
		if (mCameraDevice != null
				&& mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
			// if focusing, should cancel auto focus first
			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_FOCUSING) {
				Log.v(TAG, "cancel auto focus before stop preview");
				mCameraDevice.cancelAutoFocus();
			}

			// if using zsd, not need to stop preview after snap
			if (isZsdOn()
					&& mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING) {
				Log.v(TAG, "not need to stop preview after snap using zsd");
			} else {
				Log.v(TAG, "CameraDevice stopPreview");
				mCameraDevice.stopPreview();
				mCameraDevice.setPreviewCallback(null);
				mCameraDevice.setOneshotPreviewCallback(null);
				mActivity
						.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
			}
		}
	}

	@Override
	public int getCameraState() {
		return 0;
	}

	@Override
	public void onSurfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void onCountDownFinished() {
	}

	@Override
	public void onScreenSizeChanged(int width, int height, int previewWidth,
			int previewHeight) {
	}

	private boolean canTakePicture() {
		boolean canTake = isCameraIdle();// && (mActivity.getStorageSpace() >
		// Storage.LOW_STORAGE_THRESHOLD);
		Log.d("dyb", "can take picture " + canTake);
		return canTake;
	}

	@Override
	public void autoFocus() {
		Log.v(TAG, "autoFocus");
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
			mActivity.setCameraState(CameraActivity.CAMERA_STATE_FOCUSING);
			mCameraDevice.autoFocus(mAutoFocusCallback);
		}

		mIsAutoFocusCallback = true;
	}

	@Override
	public boolean capture() {
		// If we are already in the middle of taking a snapshot or the image
		// save request
		// is full then ignore.
		if (mCameraDevice == null
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA
				|| mActivity.getMediaSaveService().isQueueFull()
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_FOCUSING) {
			Log.v(TAG, "capture not allow");
			return false;
		}

		Log.v(TAG, "capture");

		mActivity.setCameraState(CameraActivity.CAMERA_STATE_SNAPSHOTTING);
		mCaptureStartTime = System.currentTimeMillis();
		mPostViewPictureCallbackTime = 0;
		mJpegImageData = null;

		// final boolean animateBefore = (mSceneMode == Util.SCENE_MODE_HDR);

		// Set rotation and gps data.
		mJpegRotation = Util.getJpegRotation(mCameraId, mOrientation);
		mParameters.setRotation(mJpegRotation);
		Location loc = mLocationManager.getCurrentLocation();
		Util.setGpsParameters(mParameters, loc);
		PlatformHelper.setHDR(isHdrOn(), mParameters);
		mCameraDevice.setParameters(mParameters);
		mShutterTime = System.currentTimeMillis();
		mActivity.getGLRootView().takePicture();
		mActivity.setPreviewSuspended(true);
		mCameraDevice.takePicture2(new ShutterCallback(), mRawPictureCallback,
				mPostViewPictureCallback, new JpegPictureCallback(loc),
				mActivity.getCameraState(), mFocusManager.getFocusState());
		mNamedImages.nameNewImage(mContentResolver, mCaptureStartTime);

		return true;
	}

	@Override
	public void startFaceDetection() {
	}

	@Override
	public void stopFaceDetection() {
	}

	@Override
	public void setFocusParameters() {
		setCameraParameters(UPDATE_PARAM_PREFERENCE);
	}

	@Override
	public void onSharedPreferenceChanged() {
	}

	@Override
	public void onRestorePreferencesClicked() {
	}

	@Override
	public void onOverriddenPreferencesClicked() {
	}

	@Override
	public void onCameraPickerClicked(int cameraId) {
		if (mPaused || mPendingSwitchCameraId != -1)
			return;

		mPendingSwitchCameraId = cameraId;
		mUI.hideSettingPanel();
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_SWITCHING_CAMERA);
		mUI.pressShutter(false);
		if (PlatformHelper.supportSwitchBlur()) {
			mCameraDevice.setOneshotPreviewCallback(this);
		} else {
			switchCamera();
		}
	}

	private void updateAutoFocusMoveCallback() {
		if (mParameters.getFocusMode().equals(
				Util.FOCUS_MODE_CONTINUOUS_PICTURE)) {
			mCameraDevice
					.setAutoFocusMoveCallback((AutoFocusMoveCallback) mAutoFocusMoveCallback);
		} else {
			mCameraDevice.setAutoFocusMoveCallback(null);
		}
	}

	private final class ShutterCallback implements
			android.hardware.Camera.ShutterCallback {

		@Override
		public void onShutter() {
			Log.v("mk", "onShutter");
			mShutterCallbackTime = System.currentTimeMillis();
			mShutterLag = mShutterCallbackTime - mCaptureStartTime;
			Log.v(TAG, "mShutterLag = " + mShutterLag + "ms");
			mUI.startCaptureAnimation();
			mUI.rotateOutThumbnail();
		}
	}
	
  //	//对jpeg图像数据的回调,最重要的一个回调
	private final class PostViewPictureCallback implements PictureCallback {
		@Override
		public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
			// on MTK platform, this callback doesn't work
			Log.d(TAG,
					"PostViewPictureCallback, onPictureTaken(), data.length = "
							+ data.length);
			mPostViewPictureCallbackTime = System.currentTimeMillis();
			Log.v(TAG, "mShutterToPostViewCallbackTime = "
					+ (mPostViewPictureCallbackTime - mShutterCallbackTime)
					+ "ms");
		}
	}

	private final class RawPictureCallback implements PictureCallback {
		@Override
		public void onPictureTaken(byte[] rawData,
				android.hardware.Camera camera) {
			// on MTK platform, rawData is null
			Log.d(TAG, "RawPictureCallback, onPictureTaken()");
			mRawPictureCallbackTime = System.currentTimeMillis();
			Log.v(TAG, "mShutterToRawCallbackTime = "
					+ (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
		}
	}

	private final class JpegPictureCallback implements PictureCallback {
		Location mLocation;

		public JpegPictureCallback(Location loc) {
			mLocation = loc;
		}

		@Override
		public void onPictureTaken(final byte[] jpegData,
				final android.hardware.Camera camera) {
			Log.v(TAG,
					"JpegPictureCallback, onPicutreTaken(), jpegData.length = "
							+ jpegData.length);
			long onPicTakenTime = System.currentTimeMillis() - mShutterTime;
			Log.d(PERFORMANCE_TAG, "picture taken time is " + onPicTakenTime
					+ " ms");
			if (mPaused) {
				return;
			}

			mJpegPictureCallbackTime = System.currentTimeMillis();
			// If postview callback has arrived, the captured image is displayed
			// in postview callback. If not, the captured image is displayed in
			// raw picture callback.
			if (mPostViewPictureCallbackTime != 0) {
				mShutterToPictureDisplayedTime = mPostViewPictureCallbackTime
						- mShutterCallbackTime;
				mPictureDisplayedToJpegCallbackTime = mJpegPictureCallbackTime
						- mPostViewPictureCallbackTime;
			} else {
				mShutterToPictureDisplayedTime = mRawPictureCallbackTime
						- mShutterCallbackTime;
				mPictureDisplayedToJpegCallbackTime = mJpegPictureCallbackTime
						- mRawPictureCallbackTime;
			}
			Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
					+ mPictureDisplayedToJpegCallbackTime + "ms");

			// Only animate when in full screen capture mode
			// i.e. If monkey/a user swipes to the gallery during picture
			// taking,
			// don't show animation
			mFocusManager.updateFocusUI(); // Ensure focus indicator is hidden.

			if (!mIsImageCaptureIntent) {
				if (ApiHelper.CAN_START_PREVIEW_IN_JPEG_CALLBACK) {
					setupPreview();
				} else {
					// Camera HAL of some devices have a bug. Starting preview
					// immediately after taking a picture will fail. Wait some
					// time before starting the preview.
					mHandler.sendEmptyMessageDelayed(SETUP_PREVIEW, 500);
				}
			}

			mJpegImageData = jpegData;

			Size s = mParameters.getPictureSize();
			ExifInterface exif = Exif.getExif(jpegData);
			int orientation = Exif.getOrientation(exif);
			int width, height;
			if ((mJpegRotation + orientation) % 180 == 0) {
				width = s.width;
				height = s.height;
			} else {
				width = s.height;
				height = s.width;
			}

			if (isEffect) {
				exif.removeCompressedThumbnail();
				mJpegImageData = mActivity.getGLRootView().processJpegData(
						jpegData, 100);
			}
			if (mIsBeautyOn) {
				int params[] = new int[5];
				params[0] = getFaceBeautyLevel();
				ImageProcessNativeInterface.faceEnhancementInit(width, height);
				mFaceBeautyBitmap = BitmapFactory.decodeByteArray(
						mJpegImageData, 0, mJpegImageData.length);
				ImageProcessNativeInterface.faceEnhancementProcessBitmap(
						mFaceBeautyBitmap, params);
				ImageProcessNativeInterface.faceEnhancementRelease();
				ByteArrayOutputStream outputstream = new ByteArrayOutputStream();
				mFaceBeautyBitmap.compress(CompressFormat.JPEG, 100,
						outputstream);
				mJpegImageData = outputstream.toByteArray();
				Log.d("dyb", "face beauty process");
			}
			if (!mIsImageCaptureIntent) {
				String title = mNamedImages.getTitle();
				long date = mNamedImages.getDate();
				if (title == null) {
					Log.e(TAG, "Unbalanced name/data pair");
				} else {
					if (date == -1)
						date = mCaptureStartTime;
					if (mHeading >= 0) {
						// heading direction has been updated by the sensor.
						ExifTag directionRefTag = exif.buildTag(
								ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
								ExifInterface.GpsTrackRef.MAGNETIC_DIRECTION);
						ExifTag directionTag = exif.buildTag(
								ExifInterface.TAG_GPS_IMG_DIRECTION,
								new Rational(mHeading, 1));
						exif.setTag(directionRefTag);
						exif.setTag(directionTag);
					}
					Log.v("mk", "save it to image");
					if (!mIsRecordLocation)
						mLocation = null;
					mActivity.getMediaSaveService().addImage(mJpegImageData,
							title, date, mLocation, width, height, orientation,
							null, mOnMediaSavedListener, mContentResolver);
				}
			} else {
				mUI.enableGestures(true);
				if (!mQuickCapture) {
					mIsInReviewMode = true;
					mUI.setReviewBitmap(mJpegImageData);
					mUI.showPostCaptureAlert();
				} else {
					onCaptureDone();
				}
			}

			// Check this in advance of each shot so we don't add to shutter
			// latency. It's true that someone else could write to the SD card
			// in
			// the mean time and fill it, but that could have happened between
			// the
			// shutter press and saving the JPEG too.
			mActivity.updateStorageSpaceAndHint();

			long now = System.currentTimeMillis();
			mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
			Log.v(PERFORMANCE_TAG, "mJpegCallbackFinishTime = "
					+ mJpegCallbackFinishTime + "ms");
			Log.v(TAG, "======================================");
			mJpegPictureCallbackTime = 0;
			// Whenever a phot is take, report to user track
		}
	}

	private final class AutoFocusCallback implements
			android.hardware.Camera.AutoFocusCallback {
		@Override
		public void onAutoFocus(boolean focused, android.hardware.Camera camera) {
			if (mPaused)
				return;

			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_FOCUSING) {
				mActivity
						.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
			}
			mFocusManager.onAutoFocus(focused, mUI.isShutterPressed());
			mUTFocusCounter++;
		}
	}
//设置相机自动对焦移回调。
	private final class AutoFocusMoveCallback implements
			android.hardware.Camera.AutoFocusMoveCallback {
		@Override
		public void onAutoFocusMoving(boolean moving,
				android.hardware.Camera camera) {
			if (mIsInReviewMode)
				return;
			if (mFocusManager != null)
				mFocusManager.onAutoFocusMoving(moving);
		}
	}

	@Override
	public void restartPreview() {
		Log.v(TAG, "restartPreview");
		if (mPaused)
			return;
		mCameraDevice = mActivity.getCameraDevice();
		mParameters = mCameraDevice.getParameters();
		startPreview();
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		int type = event.sensor.getType();
		float[] data;
		if (type == Sensor.TYPE_ACCELEROMETER) {
			data = mGData;
		} else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
			data = mMData;
		} else {
			// we should not be here.
			return;
		}
		for (int i = 0; i < 3; i++) {
			data[i] = event.values[i];
		}
		float[] orientation = new float[3];
		SensorManager.getRotationMatrix(mR, null, mGData, mMData);
		SensorManager.getOrientation(mR, orientation);
		mHeading = (int) (orientation[0] * 180f / Math.PI) % 360;
		if (mHeading < 0) {
			mHeading += 360;
		}
	}

	@Override
	public void onCameraFlashModeClicked(int modeId) {
		IconListPreference pref = (IconListPreference) mPreferenceGroup
				.findPreference(CameraSettings.KEY_FLASH_MODE);
		if (pref != null) {
			String value = (String) pref.getEntryValues()[(modeId + 1) % 3];
			pref.setValue(value);
			mParameters.setFlashMode(value);
			if (mCameraDevice != null)
				mCameraDevice.setParameters(mParameters);
			mUI.updateFlashButton(value);
		}
		mUI.hideSettingPanel();
	}

	private void loadCameraPreferences() {
		CameraSettings settings = new CameraSettings(mActivity, mInitialParams,
				mCameraId, CameraHolder.instance().getCameraInfo());
		mPreferenceGroup = settings
				.getPreferenceGroup(R.xml.camera_preferences);
	}

	@Override
	public void onSwipe(int direction) {
		Log.v(CameraActivity.ChangeModuleTag, "onSwipe direction(" + direction
				+ ")");
		mUI.clearFocus();
		if (mPendingModuleIndex != CameraActivity.UNKNOWN_MODULE_INDEX) {
			mActivity.setCachedSwipeEvent(direction);
			return;
		}

		if (Util.isImageCaptureIntent(mActivity.getIntent()))
			return;
		Log.v("mk", "PhotoModule -- onSwipe(), direction = " + direction
				+ ", mPendingModuleIndex = " + mPendingModuleIndex);
		if (direction == PreviewGestures.DIR_LEFT) {
			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
				mPendingModuleIndex = CameraActivity.SCANNER_MODULE_INDEX;
				mCameraDevice.setOneshotPreviewCallback(this);
			}
		} else if (direction == PreviewGestures.DIR_RIGHT) {
			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
				mPendingModuleIndex = CameraActivity.VIDEO_MODULE_INDEX;
				mCameraDevice.setOneshotPreviewCallback(this);
			}
		}
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (mPreviewData == null) {
			Log.v(CameraActivity.ChangeModuleTag, "onPreviewFrame photo");
		}

		mPreviewData = data;
		if (mPendingModuleIndex != CameraActivity.UNKNOWN_MODULE_INDEX) {
			mCameraDevice.setPreviewCallback(null);
			mCameraDevice.setOneshotPreviewCallback(null);
			mParameters = mCameraDevice.getParameters();
			Size previewSize = mParameters.getPreviewSize();
			if (mCameraId == 0)
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, data, mCameraId);
			else
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, data, mCameraId);
			mUI.animateToModule(mPendingModuleIndex);
		} else if (mPendingSwitchCameraId != -1) {
			mHandler.sendEmptyMessage(SWITCH_CAMERA);
		}
	}

	@Override
	public void onFlipAnimationEnd() {
		mActivity.getGLRootView().startFadeOut();
	}

	@Override
	public void onDestroy() {
	}

	@Override
	public void onAnimationEnd() {
		int pendingEvent = mActivity.getAndClearCachedSwipeEvent();
		Log.v("mk", "cached event = " + pendingEvent);
		if (pendingEvent == PreviewGestures.DIR_LEFT) {
			if (mPendingModuleIndex < 3) {
				mPendingModuleIndex++;
				mUI.animateToModule(mPendingModuleIndex);
				return;
			}
		} else if (pendingEvent == PreviewGestures.DIR_RIGHT) {
			if (mPendingModuleIndex > 0) {
				mPendingModuleIndex--;
				mUI.animateToModule(mPendingModuleIndex);
				return;
			}
		}
		mHandler.sendEmptyMessage(CHANGE_MODULE);
	}

	public String getFlashMode(int moduleIndex) {
		Log.v("mk", "moduleIndex = " + moduleIndex);
		if (moduleIndex != CameraActivity.VIDEO_MODULE_INDEX
				&& moduleIndex != CameraActivity.SCANNER_MODULE_INDEX
				&& moduleIndex != CameraActivity.PHOTO_MODULE_INDEX) {
			throw new IllegalArgumentException();
		}
		String flashMode = null;
		if (moduleIndex == CameraActivity.PHOTO_MODULE_INDEX) {
			flashMode = mPreferences.getString(CameraSettings.KEY_FLASH_MODE,
					Parameters.FLASH_MODE_AUTO);
		} else if (moduleIndex == CameraActivity.VIDEO_MODULE_INDEX) {
			flashMode = mPreferences.getString(
					CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
					Parameters.FLASH_MODE_OFF);
		} else {
			flashMode = mPreferences.getString(
					CameraSettings.KEY_SCANCAMERA_FLASH_MODE,
					Parameters.FLASH_MODE_OFF);
		}
		return flashMode;
	}

	@Override
	public void onLinesChanged() {
		mIsLinesOn = !mIsLinesOn;
		Editor editor = mPreferences.edit();
		editor.putBoolean(CameraSettings.KEY_PHOTO_ALIGNMENT_LINES, mIsLinesOn);
		if (editor.commit()) {
			mUI.updateLinesButton(mIsLinesOn);
		}
	}

	@Override
	public void onFaceBeautyChanged() {
		mIsBeautyOn = !mIsBeautyOn;
		mUI.updateBeautyButton(mIsBeautyOn);
		if (mIsBeautyOn) {
			Toast toast = Toast.makeText(mActivity,
					mActivity.getString(R.string.face_beauty_on),
					Toast.LENGTH_SHORT);
			toast.setGravity(Gravity.CENTER, 0, mActivity.getResources()
					.getDimensionPixelSize(R.dimen.face_beauty_toast_offset));
			toast.show();
			startFaceDetection();
		} else {
			stopFaceDetection();
			mUI.clearFaces();
		}
		updatePictureSize();
	}

	@Override
	public void onHDRChanged() {
		mIsHDROn = !mIsHDROn;
		mUI.updateHDRButton(mIsHDROn);
	}

	@Override
	public boolean supportHDR() {
		return PlatformHelper.supportHDR(mCameraId);
	}

	@Override
	public boolean changeFullscreenState(boolean isExpand) {
		return false;
	}

	@Override
	public int getCameraId() {
		return mCameraId;
	}

	@Override
	public void frameAvailable() {
		Log.v(CameraActivity.ChangeModuleTag, "frameAvailable photo");
		mActivity.getGLRootView().startFadeOut();
	}

	@Override
	public void setFilter(int index, FilterData filterData) {
		mActivity.getGLRootView().setFilter(index, filterData);
		if (index == 0) {
			isEffect = false;
		} else {
			isEffect = true;
		}
	}

	@Override
	public void onFilterChanged() {
		if (mPaused) {
			Log.v(TAG, "onFilterChanged canceled because of paused");
			return;
		}

		updatePictureSize();
	}

	private void updatePictureSize() {
		boolean isFull = CameraSettings.getFullScreenPreference(mActivity,
				mPreferences);
		int res = getPhotoSizePreference();
		Size picSize = CameraSettings.getPicSize(
				mParameters.getSupportedPictureSizes(), isFull, isEffect
						|| mIsBeautyOn, res);
		mParameters.setPictureSize(picSize.width, picSize.height);
		mCameraDevice.setParameters(mParameters);
	}

	@Override
	public void launchFilterStore() {
		mToLaunchFilterStore = true;
		Intent intent = new Intent(mActivity, FilterStoreActivity.class);
		// intent.putExtra(HWActivityInterface.HW_ACTION_BAR_TITLE,
		// R.string.store_title);
		mActivity.startActivity(intent);
	}

	private boolean isZsdOn() {
		if (!PlatformHelper.supportZSD(mCameraId)) {
			return false;
		}
		String key = mActivity.getString(R.string.camera_setting_item_zsd);
		return mPreferences.getBoolean(key, false);
	}

	private boolean isHdrOn() {
		if (!PlatformHelper.supportHDR(mCameraId)) {
			return false;
		}
		return mIsHDROn;
	}

	private boolean isForcedAutoFocus() {
		String key = mActivity
				.getString(R.string.camera_setting_item_force_autofocus);
		return mPreferences.getBoolean(key, false);
	}
}
