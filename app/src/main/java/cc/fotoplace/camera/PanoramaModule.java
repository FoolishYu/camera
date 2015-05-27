package cc.fotoplace.camera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.CameraProfile;
import android.net.Uri;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import cc.fotoplace.camera.CameraManager.CameraProxy;
import cc.fotoplace.camera.CameraPreference.OnPreferenceChangedListener;
import cc.fotoplace.camera.ui.CameraSurfaceView;
import cc.fotoplace.gallery3d.exif.ExifInterface;
import cc.fotoplace.gallery3d.exif.ExifTag;
import cc.fotoplace.gallery3d.exif.Rational;

public class PanoramaModule extends CameraModule implements PanoramaController,
		ShutterButton.OnShutterButtonListener, MediaSaveService.Listener,
		SensorEventListener, PreviewCallback, OnPreferenceChangedListener {

	private static final String TAG = "CAM_PanoramaModule";

	private static final int SETUP_PREVIEW = 1;
	private static final int FIRST_TIME_INIT = 2;
	private static final int CLEAR_SCREEN_DELAY = 3;
	private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 4;
	private static final int CHECK_DISPLAY_ROTATION = 5;
	private static final int SHOW_TAP_TO_FOCUS_TOAST = 6;
	private static final int SWITCH_CAMERA_START_ANIMATION = 8;
	private static final int CAMERA_OPEN_DONE = 9;
	private static final int START_PREVIEW_DONE = 10;
	private static final int OPEN_CAMERA_FAIL = 11;
	private static final int CAMERA_DISABLED = 12;
	private static final int CAPTURE_ANIMATION_DONE = 13;
	private static final int START_PREVIEW_ANIMATION_DONE = 14;
	private static final int CHANGE_MODULE = 20;

	// The subset of parameters we need to update in setCameraParameters().
	private static final int UPDATE_PARAM_INITIALIZE = 1;
	private static final int UPDATE_PARAM_PREFERENCE = 4;
	private static final int UPDATE_PARAM_ALL = -1;

	private static final int START_MOSAICING = 15;
	private static final int PREVIEW_FRAME_AVAILABLE = 16;
	private static final int FINISH_MOSAICING = 17;

	private static final String STOP_MODE_MANUAL = "manual";
	private static final String STOP_MODE_AUTO_FINISHED = "auto-finished";
	private static final String STOP_MODE_ERROR = "interrupted";
	private static final String STOP_MODE_NONE = "none";

	public static final int MOSAIC_PAGE_NUM = 8;

	// copied from Camera hierarchy
	private CameraProxy mCameraDevice;
	private int mCameraId;
	private Parameters mParameters;
	private boolean mPaused = false;
	private ComboPreferences mPreferences;
	private PanoramaUI mUI;
	// these are only used by Camera

	// The activity is going to switch to the specified camera id. This is
	// needed because texture copy is done in GL thread. -1 means camera is not
	// switching.
	protected int mPendingSwitchCameraId = -1;
	// private Bitmap previewBitmap;
	// When setCameraParametersWhenIdle() is called, we accumulate the subsets
	// needed to be updated in mUpdateSet.
	private int mUpdateSet;
	// private FocusOverlayManager mFocusManager;
	private static final int SCREEN_DELAY = 2 * 60 * 1000;

	private Parameters mInitialParams;
	private boolean mAeLockSupported;
	private boolean mAwbLockSupported;

	// These latency time are for the CameraLatency test.
	public long mAutoFocusTime;
	public long mShutterLag;
	public long mShutterToPictureDisplayedTime;
	public long mPictureDisplayedToJpegCallbackTime;
	public long mJpegCallbackFinishTime;
	public long mCaptureStartTime;

	private boolean mMosaicStarted = false;
	private boolean mFirstFrameArrived = false;
	private boolean mReadyToTrack = false;
	private int[] offset = new int[5];

	// The degrees of the device rotated clockwise from its natural orientation.
	private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
	private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();
	private ContentProviderClient mMediaProviderClient;

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
	private boolean isMosaicInitialized = false;

	private int mPreviewWidth;
	private int mPreviewHeight;
	private int mScreenWidth;
	private MosaicThread mMosaicThread;
	private Handler mMosaicHandler;
	private Bitmap mMosaicBitmap = null;
	private int mResult = 0;
	private int[] mSize = new int[2];
	private byte[] mStorage = new byte[4096];

	private ContentResolver mContentResolver;
	private LocationManager mLocationManager;
	private SensorManager mSensorManager;
	private float[] mGData = new float[3];
	private float[] mMData = new float[3];
	private float[] mR = new float[16];
	private int mHeading = -1;
	// private boolean isSurfaceTextureNull;
	private int initialOrientation = 0;
	private int mPendingModuleIndex = CameraActivity.UNKNOWN_MODULE_INDEX;
	private boolean mIsRecordLocation = false;
	private boolean isProcessingMosaic = false;
	private boolean isPrepareingMosaic = false;
	private boolean isStoppingMosaic = false;
	// UT Variable
	private String mUTStopMode = STOP_MODE_NONE;

	private final MainHandler mHandler = new MainHandler();
	private ConditionVariable mStartPreviewPrerequisiteReady = new ConditionVariable();

	private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener = new MediaSaveService.OnMediaSavedListener() {
		@Override
		public void onMediaSaved(Uri uri) {
			if (uri != null) {
				Log.d("dyb", "panorama media saved");
				mActivity.addSecureAlbumItemIfNeeded(false, uri);
				Util.broadcastNewPicture(mActivity, uri);
				mUI.updateThumbnail();
			}
		}
	};

	// We use a queue to generated names of the images to be used later
	// when the image is ready to be saved.
	private NamedImages mNamedImages;

	/**
	 * This Handler is used to post message back onto the main thread of the
	 * application
	 */
	private class MainHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case SETUP_PREVIEW: {
				startPreview();
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

			case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
				setCameraParametersWhenIdle(0);
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
				/*
				 * if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
				 * mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION,
				 * 100); }
				 */
				break;
			}

			case SHOW_TAP_TO_FOCUS_TOAST: {
				// showTapToFocusToast();
				break;
			}

			case SWITCH_CAMERA_START_ANIMATION: {
				// ((CameraScreenNail)
				// mActivity.mCameraScreenNail).animateSwitchCamera();
				break;
			}

			case CAMERA_OPEN_DONE: {
				onCameraOpened();
				break;
			}

			case START_PREVIEW_DONE: {
				Log.d("dyb", "START_PREVIEW_DONE");
				onPreviewStarted();
				break;
			}

			case START_PREVIEW_ANIMATION_DONE: {
				// mActivity.getGLRootView().startFadeOut();
				mCameraDevice.setPreviewCallback(PanoramaModule.this);
				break;
			}

			case OPEN_CAMERA_FAIL: {
				Util.showErrorAndFinish(mActivity,
						R.string.cannot_connect_camera);
				break;
			}

			case CAMERA_DISABLED: {
				break;
			}
			case CAPTURE_ANIMATION_DONE: {
				// mUI.enablePreviewThumb(false);
				break;
			}
			case START_MOSAICING: {
				break;
			}
			case PREVIEW_FRAME_AVAILABLE: {
				if (mFirstFrameArrived) {
					mUI.enableShutter(true);
					isPrepareingMosaic = false;
					MosaicNativeInterface.nativeSetBitmapData(mUI.getBitmap());
					if (!isProcessingMosaic) {
						mUI.setOffset(offset[0], offset[1], offset[2]);
						mUI.setDrawingWidth(offset[4]);
					}
				}
				if (mResult != 0) {
					if (mResult == 2) {
						mUTStopMode = STOP_MODE_AUTO_FINISHED;
					} else {
						mUTStopMode = STOP_MODE_ERROR;
					}
					stopMosaicing();
				}
				break;
			}
			case FINISH_MOSAICING: {
				setAutoExposureLockIfSupported(false);
				setAutoWhiteBalanceLockIfSupported(false);
				mParameters
						.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
				if (!mPaused) {
					if (mCameraDevice != null)
						mCameraDevice.setParameters(mParameters);
					if (mUI != null) {
						mUI.setDrawingWidth(0);
						mUI.hideMosaicingUI();
						mUI.enableShutter(true);
					}
				}
				break;
			}

			case CHANGE_MODULE: {
				mActivity.doChangeCamera(mPendingModuleIndex);
				mPendingModuleIndex = CameraActivity.UNKNOWN_MODULE_INDEX;
				break;
			}
			}
		}
	}

	// private void initializeFocusManager() {
	// // Create FocusManager object. startPreview needs it.
	// // if mFocusManager not null, reuse it
	// // otherwise create a new instance
	// if (mFocusManager != null) {
	// mFocusManager.removeMessages();
	// } else {
	// CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
	// boolean mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
	// String[] defaultFocusModes = mActivity.getResources().getStringArray(
	// R.array.pref_camera_focusmode_default_array);
	// mFocusManager = new FocusOverlayManager(mPreferences, defaultFocusModes,
	// mInitialParams, this, mirror,
	// mActivity.getMainLooper(), mUI);
	// }
	// }
	private void initializeCapabilities() {
		mInitialParams = mCameraDevice.getParameters();
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

		keepMediaProviderInstance();
		initializeLocationSettings();
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

	private static final MessageQueue.IdleHandler mIdleHandler = new MessageQueue.IdleHandler() {
		@Override
		public boolean queueIdle() {
			Storage.ensureOSXCompatible();
			return false;
		}
	};

	boolean mIdleHandlerSchduled = false;

	private void addIdleHandler() {
		if (!mIdleHandlerSchduled) {
			MessageQueue queue = Looper.myQueue();
			queue.addIdleHandler(mIdleHandler);
			mIdleHandlerSchduled = true;
		}
	}

	private void removeIdleHandler() {
		if (mIdleHandlerSchduled) {
			MessageQueue queue = Looper.myQueue();
			queue.removeIdleHandler(mIdleHandler);
			mIdleHandlerSchduled = false;
		}
	}

	// If the activity is paused and resumed, this method will be called in
	// onResume.
	private void initializeSecondTime() {
		keepMediaProviderInstance();
		MediaSaveService s = mActivity.getMediaSaveService();
		initializeLocationSettings();
		if (s != null) {
			s.setListener(this);
		}
		mNamedImages = new NamedImages();
		keepMediaProviderInstance();
	}

	private void initializeLocationSettings() {
		mIsRecordLocation = mPreferences.getBoolean(
				CameraSettings.KEY_RECORD_LOCATION, true);
		mLocationManager.recordLocation(mIsRecordLocation);
	}

	private void setCameraParameters(int updateSet) {
		if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
			updateCameraParametersInitialize();
		}
		if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
			updateCameraParametersPreference();
		}

		mCameraDevice.setParameters(mParameters);
	}

	// If the Camera is idle, update the parameters immediately, otherwise
	// accumulate them in mUpdateSet and update later.
	private void setCameraParametersWhenIdle(int additionalUpdateSet) {
		Log.v(TAG, "setCameraParametersWhenIdle");
		mUpdateSet |= additionalUpdateSet;
		if (mCameraDevice == null) {
			// We will update all the parameters when we open the device, so
			// we don't need to do anything now.
			mUpdateSet = 0;
			return;
		} else if (isCameraIdle()) {
			setCameraParameters(mUpdateSet);
			// updateSceneMode();
			mUpdateSet = 0;
		} else {
			if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
				mHandler.sendEmptyMessageDelayed(
						SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
			}
		}
	}

	@SuppressWarnings("deprecation")
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

	private void updateCameraParametersPreference() {
		Log.v(TAG, "updateCameraParametersPreference");
		// Set a preview size that is closest to the viewfinder height and has
		// the right aspect ratio.
		List<Size> sizes = mParameters.getSupportedPreviewSizes();
		Size optimalSize = sizes.get(0);
		int maxPix = 0;
		for (Size s : sizes) {
			if (s.width * s.height > maxPix) {
				optimalSize = s;
				maxPix = s.width * s.height;
			}
		}

		Size original = mParameters.getPreviewSize();
		if (!original.equals(optimalSize)) {
			mParameters.setPreviewSize(optimalSize.width, optimalSize.height);
			Log.d(TAG, "preview size (" + optimalSize.width + ","
					+ optimalSize.height + ")");
			// Zoom related settings will be changed for different preview
			// sizes, so set and read the parameters to get latest values
			if (mHandler.getLooper() == Looper.myLooper()) {
				// On UI thread only, not when camera starts up
				// startPreview();
			} else {
				mCameraDevice.setParameters(mParameters);
			}
			mParameters = mCameraDevice.getParameters();
		}

		// Set JPEG quality.
		int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(
				mCameraId, CameraProfile.QUALITY_HIGH);
		mParameters.setJpegQuality(jpegQuality);
		mParameters.setAntibanding(Parameters.ANTIBANDING_AUTO);

		String flashMode = mPreferences.getString(
				CameraSettings.KEY_FLASH_MODE,
				mActivity.getString(R.string.pref_camera_flashmode_default));
		List<String> supportedFlash = mParameters.getSupportedFlashModes();
		if (Util.isSupported(flashMode, supportedFlash)) {
			mParameters.setFlashMode(flashMode);
		} else {
			flashMode = mParameters.getFlashMode();
			if (flashMode == null) {
				flashMode = mActivity
						.getString(R.string.pref_camera_flashmode_no_flash);
			}
		}
		// Shutter sound.
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
		mParameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
	}

	private void setAutoExposureLockIfSupported(boolean isLock) {
		if (mAeLockSupported) {
			if (isLock)
				mParameters.setAutoExposureLock(true);
			else
				mParameters.setAutoExposureLock(false);
		}
	}

	private void setAutoWhiteBalanceLockIfSupported(boolean isLock) {
		if (mAwbLockSupported) {
			if (isLock)
				mParameters.setAutoWhiteBalanceLock(true);
			else
				mParameters.setAutoWhiteBalanceLock(false);
		}
	}

	private void onPreviewStarted() {
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
		Size previewSize = mParameters.getPreviewSize();
		mActivity.getGLRootView().setPreviewSize(previewSize.width,
				previewSize.height, true);
		mActivity.getGLRootView().setSmoothChange(false);
		mUI.setMosaicBarLayout(previewSize);
		mPreviewWidth = previewSize.width;
		mPreviewHeight = previewSize.height;
		mMosaicHandler.sendEmptyMessage(MSG_MOSAIC_INIT);
		mHandler.sendEmptyMessageDelayed(START_PREVIEW_ANIMATION_DONE, 100);
	}

	private void onCameraOpened() {
		Log.v(TAG, "onCameraOpened");
		mParameters = mCameraDevice.getParameters();
		mStartPreviewPrerequisiteReady.block();

		initializeCapabilities();

		// setCameraParameters(UPDATE_PARAM_ALL);
		startPreview();

		openCameraCommon();
	}

	public boolean isCameraIdle() {
		return (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING || mActivity
				.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
	}

	// This can be called by UI Thread or CameraStartUpThread. So this should
	// not modify the views.
	private void startPreview() {
		Log.v(TAG, "startPreview");
		mCameraDevice.setErrorCallback(mErrorCallback);
		// ICS camera frameworks has a bug. Face detection state is not cleared
		// after taking a picture. Stop the preview to work around it. The bug
		// was fixed in JB.
		if (mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEW_STOPPED)
			stopPreview();

		setDisplayOrientation();
		setCameraParameters(UPDATE_PARAM_ALL);
		setAutoExposureLockIfSupported(false);
		setAutoWhiteBalanceLockIfSupported(false);
		// isSurfaceTextureNull = true;
		if (ApiHelper.HAS_SURFACE_TEXTURE) {
			CameraSurfaceView mSurfaceView = (CameraSurfaceView) mActivity
					.getGLRootView();
			if (mUI.getSurfaceTexture() == null) {
				mUI.setSurfaceTexture(mSurfaceView.getSurfaceTexture());
			} else {
				// mSurfaceView.setPreviewSize(size.width, size.height);
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
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
			Log.v(TAG, "CameraDevice startPreview");
			mCameraDevice.startPreviewAsync();
		}
		mActivity.getGLRootView().startPreview(mCameraId);
		mHandler.sendEmptyMessage(START_PREVIEW_DONE);
		mHandler.sendEmptyMessage(CHECK_DISPLAY_ROTATION);
	}

	// either open a new camera or switch cameras
	private void openCameraCommon() {
		mUI.onCameraOpened(mPreferences, mParameters, this);
	}

	private void setDisplayOrientation() {
		mDisplayRotation = Util.getDisplayRotation(mActivity);
		mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation,
				mCameraId);
		mCameraDisplayOrientation = Util.getDisplayOrientation(0, mCameraId);
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

	@Override
	public void onStateChanged() {
		switch (mActivity.getCameraState()) {
		case CameraActivity.CAMERA_STATE_PREVIEW_STOPPED:
		case CameraActivity.CAMERA_STATE_SNAPSHOTTING:
		case CameraActivity.CAMERA_STATE_SWITCHING_CAMERA:
		case CameraActivity.CAMERA_STATE_FOCUSING:
			mUI.enableGestures(false);
			break;
		case CameraActivity.CAMERA_STATE_PREVIEWING:
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
			// In Panorama Module, User can only use back camera.
			CameraSettings.writePreferredCameraId(mPreferences.getGlobal(), 0);
			return 0;
		}
	}

	@Override
	public void init(CameraActivity activity, View root) {
		Log.v(TAG, "init module");
		super.init(activity, root);
		DisplayMetrics dm = new DisplayMetrics();
		mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
		mScreenWidth = dm.widthPixels;
		mActivity.getGLRootView()
				.setScreenSize(dm.widthPixels, dm.heightPixels);
		mPreferences = new ComboPreferences(mActivity);
		// CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
		mPreferences.setLocalId(mActivity, mCameraId);
		// CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
		mCameraId = getPreferredCameraId(mPreferences);

		mUI = new PanoramaUI(activity, this, root, mPreferences);
		mContentResolver = mActivity.getContentResolver();

		// Surface texture is from camera screen nail and startPreview needs it.
		// This must be done before startPreview.
		resetExposureCompensation();
		mStartPreviewPrerequisiteReady.open();

		mLocationManager = new LocationManager(mActivity, mUI);
		mSensorManager = (SensorManager) (mActivity
				.getSystemService(Context.SENSOR_SERVICE));
		mActivity.getGLRootView().setFilter(0, null);

		mCameraDevice = mActivity.getCameraDevice();
		if (mCameraDevice == null) {
			if (activity.isPaused()) {
				Log.v(TAG, "activity is paused, so cancel init");
				return;
			} else {
				mHandler.sendEmptyMessage(OPEN_CAMERA_FAIL);
			}
		} else {
			stopPreviewAfterInit();
			mHandler.sendEmptyMessage(CAMERA_OPEN_DONE);
		}
	}

	private void stopPreviewAfterInit() {
		mParameters = mCameraDevice.getParameters();
		List<Size> sizes = mParameters.getSupportedPreviewSizes();
		Size optimalSize = sizes.get(0);
		int maxPix = 0;
		for (Size s : sizes) {
			if (s.width * s.height > maxPix) {
				optimalSize = s;
				maxPix = s.width * s.height;
			}
		}

		Size original = mParameters.getPreviewSize();

		if (!original.equals(optimalSize)) {
			Log.v(TAG, "stop preview after init");
			mCameraDevice.stopPreview();
			mActivity
					.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
		}
	}

	@Override
	public void onFullScreenChanged(boolean full) {
	}

	@Override
	public void onPauseBeforeSuper() {
		super.onPauseBeforeSuper();
		mPaused = true;
		if (mMosaicStarted) {
			mMosaicStarted = false;
			mCameraDevice.setPreviewCallback(null);
			mUI.setDrawingWidth(0);
			mUI.hideMosaicingUI();
			mUI.enableShutter(true);
			isProcessingMosaic = false;
			mFirstFrameArrived = false;
			mReadyToTrack = false;
		}

		mMosaicHandler.sendEmptyMessage(MSG_RELEASE_MEMORY);
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
	}

	@Override
	public void onPauseAfterSuper() {
		mUI.cleanIndicatorBitmap();
		// previewBitmap.recycle();
		// previewBitmap = null;
		// When camera is started from secure lock screen for the first time
		// after screen on, the activity gets
		// onCreate->onResume->onPause->onResume.
		// To reduce the latency, keep the camera for a short time so it does
		// not need to be opened again.
		if (mCameraDevice != null && mActivity.isSecureCamera()
				&& ActivityBase.isFirstStartAfterScreenOn()) {
			ActivityBase.resetFirstStartAfterScreenOn();
		}
		// Reset the focus first. Camera CTS does not guarantee that
		// cancelAutoFocus is allowed after preview stops.
		if (mCameraDevice != null
				&& mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
			mCameraDevice.cancelAutoFocus();
		}

		mHandler.removeMessages(MSG_MOSAIC_INIT);
		mHandler.removeMessages(MSG_MOSAIC_TRACKING);
		mHandler.removeMessages(MSG_RELEASE_MEMORY);
		mHandler.removeMessages(MSG_MOSAIC_FINISH);
		mHandler.removeMessages(MSG_MOSAIC_START);
		// mHandler.sendEmptyMessage(MSG_RELEASE_MEMORY);
		releaseMosaicThread();
		mReadyToTrack = false;
		// Release surface texture.
		// ((CameraScreenNail)
		// mActivity.mCameraScreenNail).releaseSurfaceTexture();

		// mNamedImages = null;

		if (mLocationManager != null)
			mLocationManager.recordLocation(false);

		// If we are in an image capture intent and has taken
		// a picture, we just clear it in onPause.
		// mJpegImageData = null;

		// Remove the messages in the event queue.
		mHandler.removeMessages(SETUP_PREVIEW);
		mHandler.removeMessages(FIRST_TIME_INIT);
		mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
		mHandler.removeMessages(SWITCH_CAMERA_START_ANIMATION);
		mHandler.removeMessages(CAMERA_OPEN_DONE);
		mHandler.removeMessages(START_PREVIEW_DONE);
		mHandler.removeMessages(OPEN_CAMERA_FAIL);
		mHandler.removeMessages(CAMERA_DISABLED);
		mHandler.removeMessages(START_PREVIEW_ANIMATION_DONE);

		mHandler.removeMessages(PREVIEW_FRAME_AVAILABLE);
		mHandler.removeMessages(START_MOSAICING);
		mHandler.removeMessages(FINISH_MOSAICING);

		if (mCameraDevice != null) {
			mCameraDevice.setPreviewCallback(null);
			mCameraDevice.setErrorCallback(null);
			mCameraDevice.setOneshotPreviewCallback(null);
			mCameraDevice = null;
			mActivity
					.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
		}

		removeIdleHandler();
		resetScreenOn();
		// mUI.onPause();

		mPendingSwitchCameraId = -1;
		// if (mFocusManager != null) mFocusManager.removeMessages();
		MediaSaveService s = mActivity.getMediaSaveService();
		if (s != null) {
			s.setListener(null);
		}

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
		// Utilize mMediaProviderClient instance to replace
		// ContentResolver calls.
		if (mMediaProviderClient == null) {
			mMediaProviderClient = mContentResolver
					.acquireContentProviderClient(MediaStore.AUTHORITY);
		}
	}

	@Override
	public void onResumeBeforeSuper() {
		mPaused = false;
	}

	@Override
	public void onResumeAfterSuper() {
		Log.d("dyb", "onResumeAfterSuper");

		if (mCameraDevice == null) {
			mCameraDevice = mActivity.getCameraDevice();
			if (mCameraDevice == null) {
				if (mActivity.isPaused()) {
					Log.v(TAG, "activity is paused, so cancel resumeAfterSuper");
					return;
				} else {
					mHandler.sendEmptyMessage(OPEN_CAMERA_FAIL);
					return;
				}
			} else {
				mHandler.sendEmptyMessage(CAMERA_OPEN_DONE);
			}
		}

		// mJpegPictureCallbackTime = 0;
		isMosaicInitialized = false;
		mMosaicThread = new MosaicThread("MosaicThread");
		mMosaicThread.start();
		mMosaicHandler = new Handler(mMosaicThread.getLooper(), mMosaicThread);
		// Start the preview if it is not started.
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
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return onVolumeKeyDown(event);
		case KeyEvent.KEYCODE_CAMERA:
			// if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
			// mUI.pressShutter(true);
			// }
			return true;
		default:
			break;
		}
		return false;
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
			// Shutter: don't support in Panorama Mode
			return true;
		} else if (function.equals(entryValues[1])) {
			// Zoom: don't support in Panorama Mode
			return true;
		}
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		Log.d("dyb", "keycode = " + keyCode);
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return onVolumeKeyUp(event);
		case KeyEvent.KEYCODE_CAMERA:
			if (mFirstTimeInitialized) {
				// onShutterButtonClick();
				// mUI.pressShutter(false);
			}
			return true;
		case KeyEvent.KEYCODE_BACK:
			if (mMosaicStarted) {
				mUTStopMode = STOP_MODE_MANUAL;
				stopMosaicing();
				return true;
			}
			return false;
		default:
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
			// Shutter: don't support in Panorama Mode
			return true;
		} else if (function.equals(entryValues[1])) {
			// Zoom: don't support in Panorama Mode
			return true;
		}
		return false;
	}

	@Override
	public void onSingleTapUp(View view, int x, int y) {
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
		if (mMosaicStarted && Math.abs(mOrientation - initialOrientation) > 45) {
			mUTStopMode = STOP_MODE_ERROR;
			stopMosaicing();
		}
		mUI.onOrientationChanged(mOrientation);
	}

	@Override
	public void onQueueStatus(boolean full) {
		Log.d("dyb_photo_module", "onQueueStatus " + full);
		mUI.enableShutter(!full);
	}

	@Override
	public void onMediaSaveServiceConnected(MediaSaveService s) {
		// We set the listener only when both service and shutter button
		// are initialized.
		Log.d("dyb_photo_module", "onMediaSaveServiceConnected");
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
		if (pressed && !mMosaicStarted) {
			setAutoExposureLockIfSupported(true);
			setAutoWhiteBalanceLockIfSupported(true);
			mParameters.setFocusMode(Parameters.FLASH_MODE_AUTO);
			mCameraDevice.setParameters(mParameters);
		}
	}

	@Override
	public void onShutterButtonClick() {
		if (mPaused
				|| mUI.collapseCameraControls()
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA)
				|| (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED)
				|| !isMosaicInitialized)
			return;
		// Do not take the picture if there is not enough storage.
		if (mActivity.getStorageSpace() <= Storage.LOW_STORAGE_THRESHOLD) {
			return;
		}

		if (!mMosaicStarted) {
			startMosaicing();
		} else {
			mUTStopMode = STOP_MODE_MANUAL;
			stopMosaicing();
		}

	}

	@Override
	public int onZoomChanged(int index) {

		// Not useful to change zoom value when the activity is paused.
		if (mPaused) {
			return index;
		}
		if (mParameters == null || mCameraDevice == null) {
			return index;
		}
		// Set zoom parameters asynchronously
		mCameraDevice.setParameters(mParameters);
		Parameters p = mCameraDevice.getParameters();
		if (p != null)
			return p.getZoom();
		return index;
	}

	@Override
	public void cancelAutoFocus() {
		Log.v(TAG, "cancelAutoFocus");
		mCameraDevice.cancelAutoFocus();
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
		setCameraParameters(UPDATE_PARAM_PREFERENCE);
	}

	@Override
	public void stopPreview() {
		Log.v(TAG, "stopPreview");
		if (mCameraDevice != null
				&& mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
			Log.v(TAG, "CameraDevice stopPreview");
			mCameraDevice.setPreviewCallback(null);
			mCameraDevice.stopPreview();
		}

		// mActivity.getGLRootView().previewStopped(previewBitmap, previewData);
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
		Log.d("dyb_photo_module", "stop preview");

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
	public void onSwipe(int direction) {
		Log.v(CameraActivity.ChangeModuleTag, "onSwipe direction(" + direction
				+ ")");
		if (mPendingModuleIndex != CameraActivity.UNKNOWN_MODULE_INDEX) {
			mActivity.setCachedSwipeEvent(direction);
			return;
		}

		if (mMosaicStarted) {
			return;
		}
		Log.d("mk", "on swipe(), direction = " + direction);

		if (direction == PreviewGestures.DIR_LEFT) {
			// do nothing currently
		} else if (direction == PreviewGestures.DIR_RIGHT) {
			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
				mPendingModuleIndex = CameraActivity.SCANNER_MODULE_INDEX;
				mCameraDevice.setOneshotPreviewCallback(this);
			}
		}
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Log.v(CameraActivity.ChangeModuleTag, "onPreviewFrame pano");
		if (mPendingModuleIndex != CameraActivity.UNKNOWN_MODULE_INDEX) {
			mParameters = mCameraDevice.getParameters();
			Size previewSize = mParameters.getPreviewSize();
			if (mCameraId == 0)
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, data, mCameraId);
			else
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, data, mCameraId);
			mUI.animateToModule(mPendingModuleIndex);
			return;
		}

		if (isProcessingMosaic)
			return;
		// First Frame arrived
		MosaicNativeInterface.nativeSetPreviewData(data);
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
			if (mMosaicStarted) {
				if (!mFirstFrameArrived) {
					trackFirstMosaicFrame();
				} else {
					if (mReadyToTrack) {
						trackMosaicFrame();
					}
				}
			} else {
				trackIdleFrame();
			}
		}
	}

	private void trackMosaicFrame() {
		if (mReadyToTrack) {
			Log.d("dyb_pano", "track mosaic frame");
			mMosaicHandler.sendEmptyMessage(MSG_MOSAIC_TRACKING);
		}
	}

	private void trackFirstMosaicFrame() {
		Log.d("dyb_pano", "track first frame");
		mFirstFrameArrived = true;
		mMosaicHandler.sendEmptyMessageDelayed(MSG_MOSAIC_START, 200);
	}

	private void trackIdleFrame() {
		if (mActivity.findViewById(R.id.photo_shutter).isEnabled()) {
			Log.v("dyb_pano", "trackIdleFrame() ======== ");
			MosaicNativeInterface.nativeSetBitmapData(mUI.getBitmap());
			mUI.setDrawingWidth(mScreenWidth / MOSAIC_PAGE_NUM);
		}
	}

	private void startMosaicing() {
		mMosaicStarted = true;
		isPrepareingMosaic = true;
		mUI.enableShutter(false);
		initialOrientation = mOrientation;
		mUI.showMosaicingUI();
		mCameraDevice.setParameters(mParameters);
	}

	private void stopMosaicing() {
		if (isPrepareingMosaic || isStoppingMosaic)
			return;
		Log.v("mk", "stopMosaicing(), set isStoppingMosaic true");
		isStoppingMosaic = true;
		mHandler.removeMessages(PREVIEW_FRAME_AVAILABLE);
		mMosaicHandler.removeMessages(MSG_MOSAIC_TRACKING);
		mUI.setOffset(0, 0, 0);
		mUI.setDrawingWidth(0);
		mUI.enableShutter(false);
		mMosaicHandler.sendEmptyMessage(MSG_MOSAIC_FINISH);
		mUI.rotateOutThumbnail();
	}

	@Override
	public void onFlipAnimationEnd() {
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
	}

	@Override
	public void onCameraFlashModeClicked(int modeId) {
	}

	private void saveImage(byte jpegData[], int size[]) {
		mCaptureStartTime = System.currentTimeMillis();
		mNamedImages.nameNewImage(mContentResolver, mCaptureStartTime);
		Location mLocation = mLocationManager.getCurrentLocation();
		// Size s = new Size(size[0], size[1]);
		ExifInterface exif = Exif.getExif(jpegData);
		int orientation = Exif.getOrientation(exif);
		int width, height;
		if ((mJpegRotation + orientation) % 180 == 0) {
			width = size[0];
			height = size[1];
		} else {
			width = size[0];
			height = size[1];
		}
		String title = mNamedImages.getTitle();
		Log.d("dyb", "panorama image title " + title);
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
						ExifInterface.TAG_GPS_IMG_DIRECTION, new Rational(
								mHeading, 1));
				exif.setTag(directionRefTag);
				exif.setTag(directionTag);
			}
			editExif(exif, mLocation);

			mActivity.getMediaSaveService().addImage(jpegData, title, date,
					mLocation, width, height, orientation, exif,
					mOnMediaSavedListener, mContentResolver);
		}
	}

	private void editExif(ExifInterface exif, Location loc) {
		if (loc != null) {
			exif.addGpsTags(loc.getLatitude(), loc.getLongitude());
		}
		String yunos = mActivity.getResources().getString(R.string.exif_device);
		ExifTag makeTag = exif.buildTag(ExifInterface.TAG_MAKE, yunos);
		exif.setTag(makeTag);
		ExifTag modelTag = exif.buildTag(ExifInterface.TAG_MODEL, yunos);
		exif.setTag(modelTag);
	}

	private void releaseMosaicThread() {
		if (mMosaicThread != null) {
			mMosaicThread.getLooper().quit();
			mMosaicThread = null;
		}
	}

	private void recycleBitmaps() {
		if (mMosaicBitmap != null && !mMosaicBitmap.isRecycled()) {
			mMosaicBitmap.recycle();
			mMosaicBitmap = null;
		}
		// if (mMosaicBitmapBackup != null && !mMosaicBitmapBackup.isRecycled())
		// {
		// mMosaicBitmapBackup.recycle();
		// mMosaicBitmapBackup = null;
		// }
	}

	private static final int MSG_MOSAIC_INIT = 101;
	private static final int MSG_MOSAIC_TRACKING = 102;
	private static final int MSG_RELEASE_MEMORY = 103;
	private static final int MSG_MOSAIC_FINISH = 104;
	private static final int MSG_MOSAIC_START = 105;

	private class MosaicThread extends HandlerThread implements
			Handler.Callback {

		public MosaicThread(String name) {
			super(name);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.Handler.Callback#handleMessage(android.os.Message)
		 */
		@Override
		public boolean handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_MOSAIC_INIT:
				Log.d("dyb", "MosaicThread INIT");
				MosaicNativeInterface.nativeInitializeMosaic(mPreviewWidth,
						mPreviewHeight, MOSAIC_PAGE_NUM);
				mMosaicBitmap = mUI.getBitmap();
				isMosaicInitialized = true;
				break;
			case MSG_MOSAIC_START:
				Log.d("dyb", "MosaicThread START");
				mResult = 0;
				MosaicNativeInterface.nativeSetDirection(0);
				MosaicNativeInterface.nativeStartMosaicing();
				mHandler.sendEmptyMessage(START_MOSAICING);
				mReadyToTrack = true;
				Log.d("dyb", "MosaicThread START finish");
				break;
			case MSG_MOSAIC_TRACKING:
				Log.d("dyb", "MosaicThread TRACKING");
				mResult = MosaicNativeInterface.nativeTracking(offset);
				Log.d("dyb", "mResult " + mResult);
				mHandler.sendEmptyMessage(PREVIEW_FRAME_AVAILABLE);
				break;
			case MSG_MOSAIC_FINISH:
				Log.d("dyb", "MosaicThread FINISH");
				if (mMosaicStarted) {
					isProcessingMosaic = true;
					ByteArrayOutputStream mMosaicData = new ByteArrayOutputStream();
					MosaicNativeInterface.nativeFinishMosaicing();
					MosaicNativeInterface.nativeCompressResult(mSize, 90,
							mMosaicData, mStorage);
					saveImage(mMosaicData.toByteArray(), mSize);
					try {
						mMosaicData.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					isProcessingMosaic = false;
					mMosaicStarted = false;
					mFirstFrameArrived = false;
					mReadyToTrack = false;
					Log.v("mk", "set isStoppingMosaic fasle");
					isStoppingMosaic = false;
				}
				mHandler.sendEmptyMessage(FINISH_MOSAICING);
				break;
			case MSG_RELEASE_MEMORY:
				Log.d("dyb_pano", "MosaicThread FINISH MEMORY");
				MosaicNativeInterface.nativeReleaseMemory();
				isMosaicInitialized = false;
				break;
			default:
				break;
			}
			return false;
		}

	}

	@Override
	public void onDestroy() {
		recycleBitmaps();
	}

	@Override
	public void onAnimationEnd() {
		int pendingEvent = mActivity.getAndClearCachedSwipeEvent();
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
		// change module
		mActivity.doChangeCamera(mPendingModuleIndex);
		mPendingModuleIndex = CameraActivity.UNKNOWN_MODULE_INDEX;
	}

	@Override
	public void frameAvailable() {
		Log.v(CameraActivity.ChangeModuleTag, "frameAvailable pano");
		mActivity.getGLRootView().startFadeOut();
	}

}
