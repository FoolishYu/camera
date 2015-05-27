package cc.fotoplace.camera;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import cc.fotoplace.camera.platform.PlatformHelper;
import cc.fotoplace.camera.ui.CameraSurfaceView;
import cc.fotoplace.gallery3d.exif.ExifInterface;

public class VideoModule extends CameraModule implements VideoController,
		FocusOverlayManager.Listener,
		CameraPreference.OnPreferenceChangedListener,
		ShutterButton.OnShutterButtonListener, MediaRecorder.OnErrorListener,
		MediaRecorder.OnInfoListener, PreviewCallback {

	private static final String TAG = "CAM_VideoModule";

	private static final int CHECK_DISPLAY_ROTATION = 3;
	private static final int CLEAR_SCREEN_DELAY = 4;
	private static final int UPDATE_RECORD_TIME = 5;
	private static final int ENABLE_SHUTTER_BUTTON = 6;
	private static final int SHOW_TAP_TO_SNAPSHOT_TOAST = 7;
	private static final int SWITCH_CAMERA = 8;
	private static final int SWITCH_CAMERA_START_ANIMATION = 9;
	private static final int CHANGE_MODULE = 17;
	private static final int DISMISS_ZOOM_UI = 18;
	private static final int START_PREVIEW_DONE = 14;
	private static final int STOP_VIDEO_ASYNC_DONE = 20;
	private static final int START_VIDEO_ASYNC_DONE = 21;

	private static final int SCREEN_DELAY = 2 * 60 * 1000;

	private static final long SHUTTER_BUTTON_TIMEOUT = 500L; // 500ms

	private int[] CAMCORDER_PROFILES;

	private static final int ZOOM_STARTED = 0;
	private static final int ZOOM_STOPPED = 1;
	private static final int ZOOM_STOPPING = 2;

	/**
	 * An unpublished intent flag requesting to start recording straight away
	 * and return as soon as recording is stopped. consider publishing by moving
	 * into MediaStore.
	 */
	private static final String EXTRA_QUICK_CAPTURE = "android.intent.extra.quickCapture";

	private CameraActivity mActivity;
	private boolean mPaused;
	private int mCameraId;
	private Parameters mParameters;
	private boolean mOpenCameraFail;
	private CameraManager.CameraProxy mCameraDevice;
	private Boolean mCameraOpened = false;
	private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

	private ComboPreferences mPreferences;
	private PreferenceGroup mPreferenceGroup;

	private CameraSurfaceView mGlRootView;

	// Parse Intent
	private boolean mIsVideoCaptureIntent = false;
	private boolean mQuickCapture;
	private boolean mIsInReviewMode = false;

	private MediaRecorder mMediaRecorder;

	private FocusOverlayManager mFocusManager;
	private boolean mFocusAreaSupported;
	private boolean mMeteringAreaSupported;
	private boolean mAeLockSupported;
	private boolean mAwbLockSupported;
	private boolean mSmoothZoomSupported;
	private Object mLockCameraForAsyncAndFocus;

	private boolean mSwitchingCamera;
	private boolean mMediaRecorderRecording = false;
	private long mRecordingStartTime;
	private boolean mRecordingTimeCountsDown = false;
	private long mOnResumeTime;
	// The video file that the hardware camera is about to record into
	// (or is recording into.)
	private String mVideoFilename;
	private ParcelFileDescriptor mVideoFileDescriptor;

	// The video file that has already been recorded, and that is being
	// examined by the user.
	private String mCurrentVideoFilename;
	private Uri mCurrentVideoUri;
	private ContentValues mCurrentVideoValues;

	StopVideoRecordingThread mStopVideoRecordingThread = null;
	StartVideoRecordingThread mStartVideoRecordingThread = null;

	private CamcorderProfile mProfile;

	// The video duration limit. 0 menas no limit.
	private int mMaxVideoDurationInMs;

	// Time Lapse parameters.
	private boolean mCaptureTimeLapse = true;
	// Default 0. If it is larger than 0, the camcorder is in time lapse mode.
	private int mTimeBetweenTimeLapseFrameCaptureMs = 0;

	// The display rotation in degrees. This is only valid when mPreviewing is
	// true.
	private int mDisplayRotation;
	private int mCameraDisplayOrientation;
	private int mJpegRotation;

	private int mDesiredPreviewWidth;
	private int mDesiredPreviewHeight;
	private ContentResolver mContentResolver;

	private LocationManager mLocationManager;

	private int mPendingSwitchCameraId;

	private final Handler mHandler = new MainHandler();
	private VideoUI mUI;
	// The degrees of the device rotated clockwise from its natural orientation.
	private int mOrientation = 0/* OrientationEventListener.ORIENTATION_UNKNOWN */;

	private int mZoomValue; // The current zoom value.
	private int zoomState = ZOOM_STOPPED;
	private boolean mIsRecordLocation = false;

	private byte previewData[];
	private int mPendingModuleIndex = CameraActivity.UNKNOWN_MODULE_INDEX;
	private String mFlashMode;
	private boolean mIsAutoFocusCallback = false;
	private boolean isFlashTorchHold = false;
	// UT Variables
	private int mUTFocusCounter = 0;
	private int mUTSnapShotCounter = 0;
	private long mUTVideoDuration = 0;
	private boolean needShutterSound;
	private final MediaSaveService.OnMediaSavedListener mOnVideoSavedListener = new MediaSaveService.OnMediaSavedListener() {
		@Override
		public void onMediaSaved(Uri uri) {
			if (uri != null) {
				Log.v(TAG, "uri = " + uri.toString());
				mActivity.addSecureAlbumItemIfNeeded(true, uri);
				mActivity.sendBroadcast(new Intent(Util.ACTION_NEW_VIDEO, uri));
				Util.broadcastNewPicture(mActivity, uri);
				mUI.updateVideoThumbnail();
				// Save uri in case third-party App doesn't provide save uri
				if (mIsVideoCaptureIntent) {
					mCurrentVideoUri = uri;
				}
			}
		}
	};

	private final MediaSaveService.OnMediaSavedListener mOnPhotoSavedListener = new MediaSaveService.OnMediaSavedListener() {
		@Override
		public void onMediaSaved(Uri uri) {
			if (uri != null) {
				mActivity.addSecureAlbumItemIfNeeded(false, uri);
				Util.broadcastNewPicture(mActivity, uri);
				mUI.updatePhotoThumbnail();
			}
			mUTSnapShotCounter++;
		}
	};

	// This Handler is used to post message back onto the main thread of the
	// application
	private class MainHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case ENABLE_SHUTTER_BUTTON:
				mUI.enableShutter(true);
				break;

			case CLEAR_SCREEN_DELAY: {
				mActivity.getWindow().clearFlags(
						WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				break;
			}

			case UPDATE_RECORD_TIME: {
				updateRecordingTime();
				break;
			}

			case CHECK_DISPLAY_ROTATION: {
				// Restart the preview if display rotation has changed.
				// Sometimes this happens when the device is held upside
				// down and camera app is opened. Rotation animation will
				// take some time and the rotation value we have got may be
				// wrong. Framework does not have a callback for this now.
				if ((Util.getDisplayRotation(mActivity) != mDisplayRotation)
						&& !mMediaRecorderRecording && !mSwitchingCamera) {
					startPreview();
				}
				if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
					mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION,
							100);
				}
				break;
			}

			case SHOW_TAP_TO_SNAPSHOT_TOAST: {
				showTapToSnapshotToast();
				break;
			}

			case SWITCH_CAMERA: {
				switchCamera();
				break;
			}

			case SWITCH_CAMERA_START_ANIMATION: {
				// Enable all camera controls.
				mSwitchingCamera = false;
				break;
			}

			case START_PREVIEW_DONE: {
				mActivity
						.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
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

			case STOP_VIDEO_ASYNC_DONE: {
				onAsyncStopedImpl();
				break;
			}

			case START_VIDEO_ASYNC_DONE: {
				onAsyncStartedImpl();
				break;
			}

			default:
				Log.v(TAG, "Unhandled message: " + msg.what);
				break;
			}
		}
	}

	private BroadcastReceiver mReceiver = null;

	private class MyBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
				stopVideoRecording();
			} else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
			}
		}
	}

	private String createName(long dateTaken) {
		Date date = new Date(dateTaken);
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				mActivity.getString(R.string.video_file_name_format), Locale.US);

		return dateFormat.format(date);
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
		Log.v(TAG, "init video module");
		super.init(activity, root);
		mActivity = activity;
		mPendingModuleIndex = -1;
		// get display layout
		DisplayMetrics dm = new DisplayMetrics();
		mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
		mGlRootView = mActivity.getGLRootView();
		mGlRootView.setScreenSize(dm.widthPixels, dm.heightPixels);

		mPreferences = new ComboPreferences(mActivity);
		mCameraId = getPreferredCameraId(mPreferences);
		mPreferences.setLocalId(mActivity, mCameraId);

		// Create Video UI
		mUI = new VideoUI(activity, this, root, mPreferences);
		mUI.setListener(this);

		mContentResolver = mActivity.getContentResolver();
		mIsVideoCaptureIntent = Util
				.isVideoCaptureIntent(mActivity.getIntent());
		mQuickCapture = mActivity.getIntent().getBooleanExtra(
				EXTRA_QUICK_CAPTURE, false);
		mLocationManager = new LocationManager(mActivity, null);
		// Make sure camera device is opened.
		mCameraDevice = mActivity.getCameraDevice();
		if (mCameraDevice == null) {
			if (activity.isPaused()) {
				Log.v(TAG, "activity is paused, so cancel init");
				return;
			} else {
				mOpenCameraFail = true;
				Util.showErrorAndFinish(mActivity,
						R.string.cannot_connect_camera);
				return;
			}
		} else {
			mCameraOpened = true;
			mParameters = mCameraDevice.getParameters();
			initializeCapabilities();
			if (mFocusManager == null)
				initializeFocusManager();
		}

		readVideoPreferences();
		mUI.setOrientationIndicator(0, false);
		setDisplayOrientation();
		mUI.showTimeLapseUI(mCaptureTimeLapse);
		initializeVideoSnapshot();
		resizeForPreviewAspectRatio();

		loadCameraPreferences();
		mPendingSwitchCameraId = -1;
		mUI.updateOnScreenIndicators(mParameters, mPreferences);
		mUI.enableShutter(false);
		mActivity.getGLRootView().setFilter(0, null);

		if (PlatformHelper.supportVideoStartAsync()
				|| PlatformHelper.supportVideoStopAsync()) {
			mLockCameraForAsyncAndFocus = new Object();
		}

		stopPreviewAfterInit();
	}

	private void stopPreviewAfterInit() {
		// because of recording is diffrent of preview, so stop-preview needed.
		mCameraDevice.stopPreview();
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
	}

	// SingleTapListener
	// Preview area is touched. Take a picture.
	@Override
	public void onSingleTapUp(View view, int x, int y) {
		if (mPaused
				|| mCameraDevice == null
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SNAPSHOTTING
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED
				|| mPendingModuleIndex != CameraActivity.UNKNOWN_MODULE_INDEX
				|| mIsInReviewMode) {
			Log.d(TAG,
					"mFirstTimeInitialized=" + "  camera state:"
							+ mActivity.getCameraState());
			return;
		}

		// set auto focus while recording video
		// if (!mMediaRecorderRecording) {
		if (!mFocusAreaSupported && !mMeteringAreaSupported)
			return;
		mFocusManager.onSingleTapUp(x, y);
		// }
		// UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
		// UsageStatistics.ACTION_CAPTURE_DONE, "VideoSnapshot");
	}

	@Override
	public void onStop() {
	}

	private void loadCameraPreferences() {
		CameraSettings settings = new CameraSettings(mActivity, mParameters,
				mCameraId, CameraHolder.instance().getCameraInfo());
		// Remove the video quality preference setting when the quality is given
		// in the intent.
		mPreferenceGroup = filterPreferenceScreenByIntent(settings
				.getPreferenceGroup(R.xml.video_preferences));
	}

	@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
	private static int getLowVideoQuality() {
		if (ApiHelper.HAS_FINE_RESOLUTION_QUALITY_LEVELS) {
			return CamcorderProfile.QUALITY_480P;
		} else {
			return CamcorderProfile.QUALITY_LOW;
		}
	}

	@Override
	public void onOrientationChanged(int orientation) {
		// We keep the last known orientation. So if the user first orient
		// the camera then point the camera to floor or sky, we still have
		// the correct orientation.

		if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN)
			return;
		mOrientation = Util.roundOrientation(orientation, mOrientation);
		mUI.onOrientationChanged(mOrientation);

		// Show the toast after getting the first orientation changed.
		if (mHandler.hasMessages(SHOW_TAP_TO_SNAPSHOT_TOAST)) {
			mHandler.removeMessages(SHOW_TAP_TO_SNAPSHOT_TOAST);
			showTapToSnapshotToast();
		}
	}

	private void startPlayVideoActivity() {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(mCurrentVideoUri,
				convertOutputFormatToMimeType(mProfile.fileFormat));
		try {
			intent.setComponent(new ComponentName("com.aliyun.video",
					"com.aliyun.video.VideoPlayerActivity"));
			mActivity.startActivity(intent);
		} catch (ActivityNotFoundException ex) {
			Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
		}
	}

	@Override
	public void onReviewPlayClicked(View v) {
		startPlayVideoActivity();
	}

	@Override
	public void onReviewDoneClicked(View v) {
		mIsInReviewMode = false;
		doReturnToCaller(true);
	}

	@Override
	public void onReviewCancelClicked(View v) {
		mIsInReviewMode = false;
		stopVideoRecording();
		doReturnToCaller(false);
	}

	public void onReviewRetakeClicked(View view) {
		mIsInReviewMode = false;
		if (isFlashTorchHold) {
			mParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
			mCameraDevice.setParameters(mParameters);
			isFlashTorchHold = false;
		}
		mUI.hideReviewUI();
	}

	@Override
	public boolean isInReviewMode() {
		return mIsInReviewMode;
	}

	private void onStopVideoRecording() {
		boolean recordFail = stopVideoRecording();
		mRecordingTimeCountsDown = false;
		if (mIsVideoCaptureIntent) {
			if (mQuickCapture) {
				doReturnToCaller(!recordFail);
			} else if (!recordFail) {
				if (mParameters.getFlashMode() != null
						&& mParameters.getFlashMode().equals(
								Parameters.FLASH_MODE_TORCH)) {
					mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
					isFlashTorchHold = true;
					mCameraDevice.setParameters(mParameters);
				}
				showCaptureResult();
			}
		} else if (!recordFail) {
			// Start capture animation.
			if (!mPaused && ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
				// The capture animation is disabled on ICS because we use
				// SurfaceView
				// for preview during recording. When the recording is done, we
				// switch
				// back to use SurfaceTexture for preview and we need to stop
				// then start
				// the preview. This will cause the preview flicker since the
				// preview
				// will not be continuous for a short period of time.

				// Get orientation directly from display rotation to make sure
				// it's up
				// to date. OnConfigurationChanged callback usually kicks in a
				// bit later, if
				// device is rotated during recording.
				mDisplayRotation = Util.getDisplayRotation(mActivity);
			}
		}
	}

	public void onProtectiveCurtainClick(View v) {
		// Consume clicks
	}

	@Override
	public void onShutterButtonClick() {
		if (mUI.collapseCameraControls()
				|| mSwitchingCamera
				|| mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED)
			return;

		AudioManager mAudioManager = (AudioManager) mActivity
				.getSystemService(Context.AUDIO_SERVICE);
		int mSystemVolume = mAudioManager
				.getStreamVolume(AudioManager.STREAM_SYSTEM);
		boolean stop = mMediaRecorderRecording;
		if (!needShutterSound) {
			mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
		}
		if (stop) {
			onStopVideoRecording();
		} else {
			startVideoRecording();
		}
		if (!needShutterSound) {
			mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM,
					mSystemVolume, 0);
		}
		mUI.enableShutter(false);

		if ((stop && !PlatformHelper.supportVideoStopAsync())
				|| (!stop && !PlatformHelper.supportVideoStartAsync())) {
			// Keep the shutter button disabled when in video capture intent
			// mode and recording is stopped. It'll be re-enabled when
			// re-take button is clicked.
			if (!(mIsVideoCaptureIntent && stop)) {
				mHandler.sendEmptyMessageDelayed(ENABLE_SHUTTER_BUTTON,
						SHUTTER_BUTTON_TIMEOUT);
			}
		}

		mFocusManager.removeMessages();
		mUI.clearFocus();
	}

	@Override
	public void onShutterButtonFocus(boolean pressed) {
		mUI.setShutterPressed(pressed);
	}

	private void readVideoPreferences() {
		// aboriginal logic: to get default video quality
		// String defaultQuality =
		// CameraSettings.getDefaultVideoQuality(mCameraId,
		// mActivity.getResources().getString(R.string.pref_video_quality_default));
		// String videoQuality =
		// mPreferences.getString(CameraSettings.KEY_VIDEO_QUALITY,
		// defaultQuality);
		int quality = getPreferredQuality(mCameraId);

		// Set video quality, for third-party invocation
		Intent intent = mActivity.getIntent();
		if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
			int extraVideoQuality = intent.getIntExtra(
					MediaStore.EXTRA_VIDEO_QUALITY, 0);
			if (extraVideoQuality > 0) {
				quality = CamcorderProfile.QUALITY_HIGH;
			} else { // 0 is mms.
				quality = CamcorderProfile.QUALITY_LOW;
			}
		}
		// Set video duration limit. The limit is read from the preference,
		// unless it is specified in the intent.
		if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
			int seconds = intent
					.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
			mMaxVideoDurationInMs = 1000 * seconds;
		} else {
			mMaxVideoDurationInMs = CameraSettings
					.getMaxVideoDuration(mActivity);
		}
		// Read time lapse recording interval.
		if (ApiHelper.HAS_TIME_LAPSE_RECORDING) {
			// Get the interval from preference
			String frameIntervalStr = mPreferences
					.getString(
							CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
							mActivity
									.getString(R.string.pref_video_time_lapse_frame_interval_default));
			mTimeBetweenTimeLapseFrameCaptureMs = Integer
					.parseInt(frameIntervalStr);
			mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);
		}
		mProfile = PlatformHelper.CamcorderProfile_get(mCameraId, quality);
		// This should be checked instead directly +1000.
		if (mCaptureTimeLapse)
			quality += 1000;
		getDesiredPreviewSize();
	}

	@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
	private void getDesiredPreviewSize() {
		mParameters = mCameraDevice.getParameters();
		if (ApiHelper.HAS_GET_SUPPORTED_VIDEO_SIZE) {
			if (mParameters.getSupportedVideoSizes() == null) {
				mDesiredPreviewWidth = mProfile.videoFrameWidth;
				mDesiredPreviewHeight = mProfile.videoFrameHeight;
			} else { // Driver supports separates outputs for preview and video.
				List<Size> sizes = mParameters.getSupportedPreviewSizes();
				Size preferred = mParameters.getPreferredPreviewSizeForVideo();
				int product = preferred.width * preferred.height;
				Iterator<Size> it = sizes.iterator();
				// Remove the preview sizes that are not preferred.
				while (it.hasNext()) {
					Size size = it.next();
					if (size.width * size.height > product) {
						it.remove();
					}
				}
				Size optimalSize = Util.getOptimalPreviewSize(mActivity, sizes,
						(double) mProfile.videoFrameWidth
								/ mProfile.videoFrameHeight);
				mDesiredPreviewWidth = optimalSize.width;
				mDesiredPreviewHeight = optimalSize.height;
			}
		} else {
			mDesiredPreviewWidth = mProfile.videoFrameWidth;
			mDesiredPreviewHeight = mProfile.videoFrameHeight;
		}
		// int size[] =
		// Util.getSize(mActivity.getResources().getString(R.string.video_preview_size));
		// mDesiredPreviewWidth = size[0];
		// mDesiredPreviewHeight = size[1];
	}

	private void resizeForPreviewAspectRatio() {
		mUI.setAspectRatio((double) mProfile.videoFrameWidth
				/ mProfile.videoFrameHeight);
	}

	@Override
	public void installIntentFilter() {
		// install an intent filter to receive SD card related events.
		IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_EJECT);
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		intentFilter.addDataScheme("file");
		mReceiver = new MyBroadcastReceiver();
		mActivity.registerReceiver(mReceiver, intentFilter);
	}

	@Override
	public void onResumeBeforeSuper() {
		mPaused = false;
	}

	@Override
	public void onResumeAfterSuper() {
		Log.v(TAG, "onResumeAfterSuper");

		if (mOpenCameraFail)
			return;
		mUI.enableShutter(false);
		mZoomValue = 0;
		mUI.updateVideoThumbnail();
		mUI.updatePhotoThumbnail();
		showVideoSnapshotUI(false);
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {

			if (mCameraDevice == null) {
				mCameraDevice = mActivity.getCameraDevice();
				if (mCameraDevice == null) {
					if (mActivity.isPaused()) {
						Log.v(TAG,
								"activity is paused, so cancel onResumeAfterSuper");
						return;
					} else {
						mOpenCameraFail = true;
					}
				} else {
					mCameraOpened = true;
					mParameters = mCameraDevice.getParameters();
					if (mFocusManager == null)
						initializeFocusManager();
				}
			}

			if (mOpenCameraFail) {
				Util.showErrorAndFinish(mActivity,
						R.string.cannot_connect_camera);
				return;
			}
			readVideoPreferences();
			resizeForPreviewAspectRatio();

			mActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					startPreview();
				}
			});

		} else {
			// preview already started
			mUI.enableShutter(true);
		}

		// Initializing it here after the preview is started.
		mUI.initializeZoom(mParameters);
		keepScreenOnAwhile();

		// Initialize location service.
		initializeLocationSettings();

		// The onResume time may not be called due to mActivity.getCameraState()
		// incorrect
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
			mOnResumeTime = SystemClock.uptimeMillis();
			mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
		}
		// Dismiss open menu if exists.
		// UsageStatistics.onContentViewChanged(
		// UsageStatistics.COMPONENT_CAMERA, "VideoModule");
		super.onResumeAfterSuper();
	}

	private void initializeFocusManager() {
		// Create FocusManager object. startPreview needs it.
		// if mFocusManager not null, reuse it
		// otherwise create a new instance
		if (mFocusManager != null) {
			mFocusManager.removeMessages();
		} else {
			CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
			boolean mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
			String[] defaultFocusModes = mActivity
					.getResources()
					.getStringArray(R.array.pref_camera_focusmode_default_array);
			mFocusManager = new FocusOverlayManager(mPreferences,
					defaultFocusModes, mParameters, this, mirror,
					mActivity.getMainLooper(), mUI);
		}
	}

	private void setDisplayOrientation() {
		mDisplayRotation = Util.getDisplayRotation(mActivity);
		// The display rotation is handled by gallery.
		mCameraDisplayOrientation = Util.getDisplayOrientation(0, mCameraId);
		// GLRoot also uses the DisplayRotation, and needs to be told to layout
		// to update
		// mActivity.getGLRoot().requestLayoutContentPane();
	}

	@Override
	public int onZoomChanged(int index, boolean isSmooth) {
		// Not useful to change zoom value when the activity is paused.
		if (mPaused)
			return index;

		Log.d("dyb", "mZoomValue = " + mZoomValue);
		if (mParameters == null || mCameraDevice == null)
			return index;
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
			mCameraDevice.startSmoothZoom(mZoomValue);
		} else {
			mZoomValue = index;
			mParameters.setZoom(mZoomValue);
			mCameraDevice.setParameters(mParameters);
		}

		Parameters p = mCameraDevice.getParameters();
		if (p != null)
			return p.getZoom();
		return index;
	}

	private void startPreview() {
		Log.v(TAG, "startPreview");
		if (mCameraDevice == null) {
			Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
			return;
		}
		mCameraDevice.setErrorCallback(mErrorCallback);
		if (mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEW_STOPPED)
			stopPreview();

		setDisplayOrientation();
		mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
		setCameraParameters(UPDATE_PARAM_PREFERENCE);
		setCameraParameters();
		mFocusManager.overrideFocusMode(null);
		String focusMode = mFocusManager.getFocusMode();
		Log.d("dyb", "focus mode is " + focusMode);
		mParameters.setFocusMode(mFocusManager.getFocusMode());
		updateAutoFocusMoveCallback();
		try {
			SurfaceTexture st = mGlRootView.getSurfaceTexture();
			if (st == null) {
				// isSurfaceTextureNull = true;
				return;
			}
			mCameraDevice.setPreviewTextureAsync(st);
			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
				mCameraDevice.startPreviewAsync();
			}
		} catch (Throwable ex) {
			mCameraDevice.setZoomChangeListener(null);
			mCameraDevice.setErrorCallback(null);
			mActivity.closeCamera();
			mCameraDevice = null;
			mActivity
					.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
			mFocusManager.onCameraReleased();

			throw new RuntimeException("startPreview failed", ex);
		} finally {
			mActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (mOpenCameraFail) {
						Util.showErrorAndFinish(mActivity,
								R.string.cannot_connect_camera);
					}
				}
			});
		}
		setCameraParameters(UPDATE_PARAM_ALL);
		onCameraOpened();
		mActivity.getGLRootView().startPreview(mCameraId);
		mFocusManager.onPreviewStarted();
		onPreviewStarted();
	}

	private void onCameraOpened() {
		mUI.onCameraOpened(mPreferenceGroup, mPreferences, mParameters, this);
		mUI.initializeFlash(mParameters);
	}

	private void onPreviewStarted() {
		mUI.enableShutter(true);
		mActivity.getGLRootView().setPreviewSize(mDesiredPreviewWidth,
				mDesiredPreviewHeight, true);
		mActivity.getGLRootView().setSmoothChange(false);
		int size[] = new int[3];
		mActivity.getGLRootView().getDrawingAreaSize(size);
		mFocusManager.setPreviewSize(size[0], size[1], size[2]);
		mHandler.sendEmptyMessageDelayed(START_PREVIEW_DONE, 500);
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
	}

	@Override
	public void stopPreview() {
		Log.v(TAG, "stopPreview");
		if (mCameraDevice != null
				&& mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEW_STOPPED) {
			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_FOCUSING) {
				Log.v(TAG, "cancel auto focus before stop preview");
				mCameraDevice.cancelAutoFocus();
			}
			Log.v(TAG, "CameraDevice stopPreview");
			mCameraDevice.stopPreview();
			mActivity
					.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
		}
	}

	private void releasePreviewResources() {
		if (ApiHelper.HAS_SURFACE_TEXTURE) {
			if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
				mUI.hideSurfaceView();
			}
		}
	}

	@Override
	public void onPauseBeforeSuper() {
		Log.v(TAG, "onPauseBeforeSuper");
		super.onPauseBeforeSuper();
		mPaused = true;
		mUI.clearFocus();

		if (isVideoRecording() || isVideoStarting()) {
			// Camera will be released in onStopVideoRecording.
			mStartVideoRecordingThread = null;
			onStopVideoRecording();
			mStopVideoRecordingThread = null;
		}

		if (!mMediaRecorderRecording || !PlatformHelper.supportVideoStopAsync()) {
			if (mFocusManager != null)
				mFocusManager.removeMessages();

			if (mCameraDevice != null) {
				mCameraDevice.setFaceDetectionListener(null);
				mCameraDevice.setErrorCallback(null);
				mCameraDevice.setOneshotPreviewCallback(null);
				mCameraDevice.setAutoFocusMoveCallback(null);
				mCameraDevice = null;
			}
			mActivity
					.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
			if (mFocusManager != null) {
				mFocusManager.onCameraReleased();
			}

			// Close the file descriptor and clear the video namer only if the
			// effects are not active. If effects are active, we need to wait
			// till we get the callback from the Effects that the graph is done
			// recording. That also needs a change in the stopVideoRecording()
			// call to not call closeCamera if the effects are active, because
			// that will close down the effects are well, thus making this if
			// condition invalid.
			closeVideoFileDescriptor();

			releasePreviewResources();

			if (mReceiver != null) {
				mActivity.unregisterReceiver(mReceiver);
				mReceiver = null;
			}
			resetScreenOn();

			if (mLocationManager != null)
				mLocationManager.recordLocation(false);

			mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
			mHandler.removeMessages(SWITCH_CAMERA);
			mHandler.removeMessages(SWITCH_CAMERA_START_ANIMATION);
			mHandler.removeMessages(START_PREVIEW_DONE);
			mHandler.removeMessages(DISMISS_ZOOM_UI);
			mPendingSwitchCameraId = -1;
			mSwitchingCamera = false;
			// Call onPause after stopping video recording. So the camera can be
			// released as soon as possible.
		}
	}

	@Override
	public void onPauseAfterSuper() {
		mIsAutoFocusCallback = false;
	}

	@Override
	public void onUserInteraction() {
		if (!mMediaRecorderRecording && !mActivity.isFinishing()) {
			keepScreenOnAwhile();
		}
	}

	@Override
	public boolean onBackPressed() {
		Log.v(TAG, "onBackPressed");
		if (mPaused)
			return true;
		if (isVideoRecording() || isVideoStarting()) {
			onStopVideoRecording();
			return true;
		} else {
			return mUI.removeTopLevelPopup();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Do not handle any key if the activity is paused.
		if (mPaused) {
			return true;
		}

		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return onVolumeKeyDown(event);
		case KeyEvent.KEYCODE_CAMERA:
			if (event.getRepeatCount() == 0 && !mIsInReviewMode) {
				mUI.pressShutter(true);
			}
			return true;
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
			// Shutter
			if (mIsInReviewMode
					|| mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEWING) {
				return true;
			}
			if (event.getRepeatCount() == 0) {
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
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return onVolumeKeyUp(event);
		case KeyEvent.KEYCODE_CAMERA:
			mUI.pressShutter(false);
			if (event.getRepeatCount() == 0 && !mIsInReviewMode) {
				mUI.clickShutter();
			}
			return true;
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
		Log.v(TAG, "onVolumeKeyUp(), event = " + event.getKeyCode());
		Log.v(TAG, "function = " + function);
		if (function.equals(entryValues[2])) {
			// Volume Adjustment
			return false;
		} else if (function.equals(entryValues[0])) {
			// Shutter
			if (mIsInReviewMode
					|| mActivity.getCameraState() != CameraActivity.CAMERA_STATE_PREVIEWING) {
				return true;
			}
			mUI.pressShutter(false);
			onShutterButtonClick();
			return true;
		} else if (function.equals(entryValues[1])) {
			// Zoom
			return true;
		}
		return false;
	}

	private void doReturnToCaller(boolean valid) {
		Intent resultIntent = new Intent();
		int resultCode;
		if (valid) {
			resultCode = Activity.RESULT_OK;
			resultIntent.setData(mCurrentVideoUri);
		} else {
			resultCode = Activity.RESULT_CANCELED;
		}
		mActivity.setResult(resultCode, resultIntent);
		mActivity.finish();
	}

	private void cleanupEmptyFile() {
		if (mVideoFilename != null) {
			File f = new File(mVideoFilename);
			if (f.length() == 0 && f.delete()) {
				Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
				mVideoFilename = null;
			}
		}
	}

	// Prepares media recorder.
	private void initializeRecorder() {
		// If the mCameraDevice is null, then this activity is going to finish
		if (mCameraDevice == null)
			return;

		Intent intent = mActivity.getIntent();
		Bundle myExtras = intent.getExtras();

		long requestedSizeLimit = 0;
		closeVideoFileDescriptor();
		if (mIsVideoCaptureIntent && myExtras != null) {
			Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
			if (saveUri != null) {
				try {
					mVideoFileDescriptor = mContentResolver.openFileDescriptor(
							saveUri, "rw");
					mCurrentVideoUri = saveUri;
				} catch (java.io.FileNotFoundException ex) {
					// invalid uri
					Log.e(TAG, ex.toString());
				}
			}
			requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
		}
		mMediaRecorder = new MediaRecorder();

		// setupMediaRecorderPreviewDisplay();
		// Unlock the camera object before passing it to media recorder.
		mCameraDevice.unlock();
		mCameraDevice.waitDone();
		mMediaRecorder.setCamera(mCameraDevice.getCamera());
		if (!mCaptureTimeLapse) {
			mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		}

		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setProfile(mProfile);
		mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
		if (mCaptureTimeLapse) {
			double fps = 1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs;
			setCaptureRate(mMediaRecorder, fps);
		}

		setRecordLocation();

		// Set output file.
		// Try Uri in the intent first. If it doesn't exist, use our own
		// instead.
		if (mVideoFileDescriptor != null) {
			mMediaRecorder.setOutputFile(mVideoFileDescriptor
					.getFileDescriptor());
		} else {
			generateVideoFilename(mProfile.fileFormat);
			mMediaRecorder.setOutputFile(mVideoFilename);
		}

		// Set maximum file size.
		long maxFileSize = mActivity.getStorageSpace()
				- Storage.LOW_STORAGE_THRESHOLD;
		if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
			maxFileSize = requestedSizeLimit;
		}

		try {
			mMediaRecorder.setMaxFileSize(maxFileSize);
		} catch (RuntimeException exception) {
			// We are going to ignore failure of setMaxFileSize here, as
			// a) The composer selected may simply not support it, or
			// b) The underlying media framework may not handle 64-bit range
			// on the size restriction.
		}

		// See android.hardware.Camera.Parameters.setRotation for
		// documentation.
		// Note that mOrientation here is the device orientation, which is the
		// opposite of
		// what activity.getWindowManager().getDefaultDisplay().getRotation()
		// would return,
		// which is the orientation the graphics need to rotate in order to
		// render correctly.
		int rotation = 0;
		if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
			CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				rotation = (info.orientation - mOrientation + 360) % 360;
			} else { // back-facing camera
				rotation = (info.orientation + mOrientation) % 360;
			}
		}
		mMediaRecorder.setOrientationHint(rotation);

		try {
			mMediaRecorder.prepare();
		} catch (IOException e) {
			Log.e(TAG, "prepare failed for " + mVideoFilename, e);
			releaseMediaRecorder();
			return;
		}

		mMediaRecorder.setOnErrorListener(this);
		mMediaRecorder.setOnInfoListener(this);
	}

	@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
	private static void setCaptureRate(MediaRecorder recorder, double fps) {
		recorder.setCaptureRate(fps);
	}

	@TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setRecordLocation() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			Location loc = mLocationManager.getCurrentLocation();
			if (loc != null && mIsRecordLocation) {
				mMediaRecorder.setLocation((float) loc.getLatitude(),
						(float) loc.getLongitude());
			}
		}
	}

	private void releaseMediaRecorder() {
		Log.v(TAG, "Releasing media recorder.");
		if (mMediaRecorder != null) {
			cleanupEmptyFile();
			mMediaRecorder.reset();
			mMediaRecorder.release();
			mMediaRecorder = null;
		}
		mVideoFilename = null;
	}

	private void generateVideoFilename(int outputFileFormat) {
		long dateTaken = System.currentTimeMillis();
		String title = createName(dateTaken);
		// Used when emailing.
		String filename = title
				+ convertOutputFormatToFileExt(outputFileFormat);
		String mime = convertOutputFormatToMimeType(outputFileFormat);
		String path = Storage.DIRECTORY + '/' + filename;
		String tmpPath = path + ".tmp";
		mCurrentVideoValues = new ContentValues(9);
		mCurrentVideoValues.put(Video.Media.TITLE, title);
		mCurrentVideoValues.put(Video.Media.DISPLAY_NAME, filename);
		mCurrentVideoValues.put(Video.Media.DATE_TAKEN, dateTaken);
		mCurrentVideoValues.put(MediaColumns.DATE_MODIFIED, dateTaken / 1000);
		mCurrentVideoValues.put(Video.Media.MIME_TYPE, mime);
		mCurrentVideoValues.put(Video.Media.DATA, path);
		mCurrentVideoValues.put(
				Video.Media.RESOLUTION,
				Integer.toString(mProfile.videoFrameWidth) + "x"
						+ Integer.toString(mProfile.videoFrameHeight));
		Location loc = mLocationManager.getCurrentLocation();
		if (loc != null) {
			mCurrentVideoValues.put(Video.Media.LATITUDE, loc.getLatitude());
			mCurrentVideoValues.put(Video.Media.LONGITUDE, loc.getLongitude());
		}
		mVideoFilename = tmpPath;
		Log.v(TAG, "New video filename: " + mVideoFilename);
	}

	private void saveVideo() {
		if (mVideoFileDescriptor == null) {
			long duration = SystemClock.uptimeMillis() - mRecordingStartTime;
			if (duration > 0) {
				if (mCaptureTimeLapse) {
					duration = getTimeLapseVideoLength(duration);
				}
			} else {
				Log.w(TAG, "Video duration <= 0 : " + duration);
			}
			mActivity.getMediaSaveService().addVideo(mCurrentVideoFilename,
					duration, mCurrentVideoValues, mOnVideoSavedListener,
					mContentResolver);
		}
		mCurrentVideoValues = null;
	}

	private void deleteVideoFile(String fileName) {
		Log.v(TAG, "Deleting video " + fileName);
		File f = new File(fileName);
		if (!f.delete()) {
			Log.v(TAG, "Could not delete " + fileName);
		}
	}

	private PreferenceGroup filterPreferenceScreenByIntent(
			PreferenceGroup screen) {
		// Intent intent = mActivity.getIntent();
		// if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
		// CameraSettings.removePreferenceFromScreen(screen,
		// CameraSettings.KEY_VIDEO_QUALITY);
		// }
		//
		// if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
		// CameraSettings.removePreferenceFromScreen(screen,
		// CameraSettings.KEY_VIDEO_QUALITY);
		// }
		return screen;
	}

	// from MediaRecorder.OnErrorListener
	@Override
	public void onError(MediaRecorder mr, int what, int extra) {
		Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
		if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
			// We may have run out of space on the sdcard.
			stopVideoRecording();
			mActivity.updateStorageSpaceAndHint();
		}
	}

	// from MediaRecorder.OnInfoListener
	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
			Log.v(TAG, "media recoder reached max duration");
			if (mMediaRecorderRecording)
				onStopVideoRecording();
		} else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
			Log.v(TAG, "media recoder reached max size");
			if (mMediaRecorderRecording)
				onStopVideoRecording();
			// Show the toast.
			Toast.makeText(mActivity, R.string.video_reach_size_limit,
					Toast.LENGTH_LONG).show();
		}
	}

	/*
	 * Make sure we're not recording music playing in the background, ask the
	 * MediaPlaybackService to pause playback.
	 */
	private void pauseAudioPlayback() {
		// Shamelessly copied from MediaPlaybackService.java, which
		// should be public, but isn't.
		Intent i = new Intent("com.android.music.musicservicecommand");
		i.putExtra("command", "pause");

		mActivity.sendBroadcast(i);
	}

	// For testing.
	public boolean isRecording() {
		return mMediaRecorderRecording;
	}

	private void startVideoRecording() {
		if (PlatformHelper.supportVideoStartAsync()) {
			startVideoRecordingAsync();
		} else {
			startVideoRecordingSync();
		}
	}

	private void startVideoRecordingAsync() {
		Log.v(TAG, "startVideoRecordingAsync");

		// mUI.enablePreviewThumb(false);
		// mActivity.setSwipingEnabled(false);

		mActivity.updateStorageSpaceAndHint();
		if (mActivity.getStorageSpace() <= Storage.LOW_STORAGE_THRESHOLD) {
			Log.v(TAG, "Storage issue, ignore the start request");
			return;
		}

		if (!mCameraDevice.waitDone()) {
			return;
		}

		mCurrentVideoUri = null;
		initializeRecorder();
		if (mMediaRecorder == null) {
			Log.e(TAG, "Fail to initialize media recorder");
			return;
		}

		pauseAudioPlayback();

		// Log.v(TAG, "setOneshotPreviewCallback");
		// // To get video thumbnail bitmap
		// mFetchingVideoThumbnail = true;
		// mCameraDevice.setPreviewCallback(this);
		if (mStartVideoRecordingThread != null) {
			mStartVideoRecordingThread = null;
		}
		mStartVideoRecordingThread = new StartVideoRecordingThread(this);
		mStartVideoRecordingThread.start();
	}

	private final class StartVideoRecordingThread extends Thread {
		private VideoModule mOwner;
		private boolean mIsProcessing = false;

		public StartVideoRecordingThread(VideoModule owner) {
			mOwner = owner;
		}

		public boolean isProcessing() {
			return mIsProcessing;
		}

		private void setProcessing(boolean flag) {
			synchronized (this) {
				mIsProcessing = flag;
			}
		}

		public void run() {
			try {
				if (PlatformHelper.supportVideoStopAsync()
						&& mStopVideoRecordingThread != null) {
					synchronized (mStopVideoRecordingThread) {
						while (mStopVideoRecordingThread.isProcessing()) {
							Log.v(TAG, "wait for StopVideoRecordingThread");
							mStopVideoRecordingThread.wait();
						}
					}
				}
				Log.v(TAG, "begin start media recorder async");
				setProcessing(true);
				PlatformHelper.handleBeforeVideoStartAsync(mOwner);
				synchronized (mLockCameraForAsyncAndFocus) {
					Log.v(TAG, "media recorder start begin");
					mMediaRecorder.start(); // Recording is now started
					Log.v(TAG, "media recorder start end");
				}
			} catch (RuntimeException e) {
				Log.e(TAG, "Could not start media recorder. ", e);
				releaseMediaRecorder();
				// If start fails, frameworks will not lock the camera for us.
				mCameraDevice.lock();
				setProcessing(false);
			} catch (Exception e) {
				Log.e(TAG, "async start handleBeforeVideoStartAsync error", e);
				setProcessing(false);
			}
		}
	}

	private void startVideoRecordingSync() {
		Log.v(TAG, "startVideoRecordingSync");
		// mUI.enablePreviewThumb(false);
		// mActivity.setSwipingEnabled(false);

		mActivity.updateStorageSpaceAndHint();
		if (mActivity.getStorageSpace() <= Storage.LOW_STORAGE_THRESHOLD) {
			Log.v(TAG, "Storage issue, ignore the start request");
			return;
		}

		if (!mCameraDevice.waitDone()) {
			return;
		}

		mCurrentVideoUri = null;
		initializeRecorder();
		if (mMediaRecorder == null) {
			Log.e(TAG, "Fail to initialize media recorder");
			return;
		}

		pauseAudioPlayback();

		// Log.v(TAG, "setOneshotPreviewCallback");
		// // To get video thumbnail bitmap
		// mFetchingVideoThumbnail = true;
		// mCameraDevice.setPreviewCallback(this);

		try {
			mMediaRecorder.start(); // Recording is now started
		} catch (RuntimeException e) {
			Log.e(TAG, "Could not start media recorder. ", e);
			releaseMediaRecorder();
			// If start fails, frameworks will not lock the camera for us.
			mCameraDevice.lock();
			return;
		}

		// Make sure the video recording has started before announcing
		// this in accessibility.
		// AccessibilityUtils.makeAnnouncement(mActivity.getShutterButton(),
		// mActivity.getString(R.string.video_recording_started));

		// The parameters might have been altered by MediaRecorder already.
		// We need to force mCameraDevice to refresh before getting it.
		mCameraDevice.refreshParameters();
		// The parameters may have been changed by MediaRecorder upon starting
		// recording. We need to alter the parameters if we support camcorder
		// zoom. To reduce latency when setting the parameters during zoom, we
		// update mParameters here once.
		if (ApiHelper.HAS_ZOOM_WHEN_RECORDING) {
			mParameters = mCameraDevice.getParameters();
		}

		// cancel auto focus move while recording.
		mCameraDevice.setAutoFocusMoveCallback(null);

		mUI.enableCameraControls(false);

		mMediaRecorderRecording = true;
		if (!Util.systemRotationLocked(mActivity)) {
			// mActivity.getOrientationManager().lockOrientation();
		}
		mRecordingStartTime = SystemClock.uptimeMillis();
		mUI.showRecordingUI(true, mParameters.isZoomSupported());

		updateRecordingTime();
		keepScreenOn();
		// UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
		// UsageStatistics.ACTION_CAPTURE_START, "Video");
	}

	private void showCaptureResult() {
		mIsInReviewMode = true;
		Bitmap bitmap = null;
		if (mVideoFileDescriptor != null) {
			bitmap = Thumbnail.createVideoThumbnailBitmap(
					mVideoFileDescriptor.getFileDescriptor(),
					mDesiredPreviewWidth);
		} else if (mCurrentVideoFilename != null) {
			bitmap = Thumbnail.createVideoThumbnailBitmap(
					mCurrentVideoFilename, mDesiredPreviewWidth);
		}
		if (bitmap != null) {
			// MetadataRetriever already rotates the thumbnail. We should rotate
			// it to match the UI orientation (and mirror if it is front-facing
			// camera).
			CameraInfo[] info = CameraHolder.instance().getCameraInfo();
			boolean mirror = (info[mCameraId].facing == CameraInfo.CAMERA_FACING_FRONT);
			bitmap = Util.rotateAndMirror(bitmap, 0, mirror);
			mUI.showReviewImage(bitmap);
		}

		mUI.showReviewUI();
		mUI.enableCameraControls(false);
		mUI.showTimeLapseUI(false);
	}

	private boolean stopVideoRecording() {
		Log.v(TAG, "stopVideoRecording");
		if (PlatformHelper.supportVideoStopAsync() && !mPaused
				&& !mIsVideoCaptureIntent) {
			return stopVideoRecordingAsync();
		} else {
			return stopVideoRecordingSync();
		}
	}

	private boolean stopVideoRecordingAsync() {
		Log.v(TAG, "stopVideoRecordingAsync");

		boolean fail = false;
		if (isVideoRecording() || isVideoStarting()) {
			if (mStopVideoRecordingThread != null) {
				mStopVideoRecordingThread = null;
			}
			mStopVideoRecordingThread = new StopVideoRecordingThread(this);
			mStopVideoRecordingThread.start();
		} else {
			// always release media recorder if no effects running
			releaseMediaRecorder();
			// whenever media recorder is released, report to user track
			if (!mPaused) {
				mCameraDevice.lock();
				mCameraDevice.waitDone();
				if (ApiHelper.HAS_SURFACE_TEXTURE
						&& !ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
					stopPreview();
					// Switch back to use SurfaceTexture for preview.
					// ((CameraScreenNail)
					// mActivity.mCameraScreenNail).setOneTimeOnFrameDrawnListener(
					// mFrameDrawnListener);
					startPreview();
				}
			}
			mUI.rotateOutThumbnail();
			// Update the parameters here because the parameters might have been
			// altered
			// by MediaRecorder.
			if (!mPaused)
				mParameters = mCameraDevice.getParameters();

			// recover auto focus move while recording.
			mCameraDevice
					.setAutoFocusMoveCallback((android.hardware.Camera.AutoFocusMoveCallback) mAutoFocusMoveCallback);
		}
		return fail;
	}

	private final class StopVideoRecordingThread extends Thread {
		private VideoModule mOwner;
		private boolean mIsProcessing = false;

		public StopVideoRecordingThread(VideoModule owner) {
			mOwner = owner;
		}

		public boolean isProcessing() {
			return mIsProcessing;
		}

		private void setProcessing(boolean flag) {
			synchronized (this) {
				mIsProcessing = flag;
			}
		}

		public void run() {
			boolean fail = false;
			try {
				if (PlatformHelper.supportVideoStartAsync()
						&& mStartVideoRecordingThread != null) {
					synchronized (mStartVideoRecordingThread) {
						while (mStartVideoRecordingThread.isProcessing()) {
							Log.v(TAG, "wait for StartVideoRecordingThread");
							mStartVideoRecordingThread.wait();
						}
					}
				}
				setProcessing(true);
				Log.v(TAG, "begin stop media recorder async");
				mMediaRecorder.setOnErrorListener(null);
				mMediaRecorder.setOnInfoListener(null);
				PlatformHelper.handleBeforeStopAsync(mOwner);
				synchronized (mLockCameraForAsyncAndFocus) {
					mMediaRecorder.stop();
				}
			} catch (RuntimeException e) {
				Log.e(TAG, "async stop fail", e);
				fail = true;
			} catch (Exception e) {
				Log.e(TAG, "async stop handleBeforeStopAsync error", e);
				fail = true;
			}

			if (fail) {
				if (mVideoFilename != null)
					deleteVideoFile(mVideoFilename);
				setProcessing(false);
			}
		}
	}

	private boolean stopVideoRecordingSync() {
		Log.v(TAG, "stopVideoRecordingSync");
		boolean fail = false;

		if (mMediaRecorderRecording) {

			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_FOCUSING) {
				Log.v(TAG, "cancel auto focus before stop recorder");
				mCameraDevice.cancelAutoFocus();
			}

			boolean shouldAddToMediaStoreNow = false;

			try {
				mMediaRecorder.setOnErrorListener(null);
				mMediaRecorder.setOnInfoListener(null);
				mMediaRecorder.stop();
				shouldAddToMediaStoreNow = true;
				mCurrentVideoFilename = mVideoFilename;
				Log.v(TAG,
						"stopVideoRecording: Setting current video filename: "
								+ mCurrentVideoFilename);
				// AccessibilityUtils.makeAnnouncement(mActivity.getShutterButton(),
				// mActivity.getString(R.string.video_recording_stopped));
				if (!mIsVideoCaptureIntent) {
					startPreview();
				}
			} catch (RuntimeException e) {
				Log.e(TAG, "stop fail", e);
				if (mVideoFilename != null)
					deleteVideoFile(mVideoFilename);
				fail = true;
			}
			mMediaRecorderRecording = false;
			if (!Util.systemRotationLocked(mActivity)) {
				// mActivity.getOrientationManager().unlockOrientation();
			}

			// If the activity is paused, this means activity is interrupted
			// during recording. Release the camera as soon as possible because
			// face unlock or other applications may need to use the camera.
			// However, if the effects are active, then we can only release the
			// camera and cannot release the effects recorder since that will
			// stop the graph. It is possible to separate out the Camera release
			// part and the effects release part. However, the effects recorder
			// does hold on to the camera, hence, it needs to be "disconnected"
			// from the camera in the closeCamera call.

			mUI.showRecordingUI(false, mParameters.isZoomSupported());
			if (!mIsVideoCaptureIntent) {
				mUI.enableCameraControls(true);
			}
			// The orientation was fixed during video recording. Now make it
			// reflect the device orientation as video recording is stopped.
			mUI.setOrientationIndicator(0, true);
			keepScreenOnAwhile();
			if (shouldAddToMediaStoreNow) {
				saveVideo();
			}
			mFocusManager.removeMessages();
			mUI.clearFocus();
			cancelAutoFocus();
		}
		// always release media recorder if no effects running
		releaseMediaRecorder();
		if (!mPaused) {
			mCameraDevice.lock();
			mCameraDevice.waitDone();
			if (ApiHelper.HAS_SURFACE_TEXTURE
					&& !ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
				stopPreview();
				// Switch back to use SurfaceTexture for preview.
				// ((CameraScreenNail)
				// mActivity.mCameraScreenNail).setOneTimeOnFrameDrawnListener(
				// mFrameDrawnListener);
				startPreview();
			}

			// Update the parameters here because the parameters might have been
			// altered
			// by MediaRecorder.
			mParameters = mCameraDevice.getParameters();

			// if stop recorder with home, it should not display thumbnail
			// animation when resumed
			mUI.rotateOutThumbnail();
		}

		// recover auto focus move while recording.
		mCameraDevice
				.setAutoFocusMoveCallback((android.hardware.Camera.AutoFocusMoveCallback) mAutoFocusMoveCallback);
		return fail;
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

	private void keepScreenOn() {
		mHandler.removeMessages(CLEAR_SCREEN_DELAY);
		mActivity.getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private static String millisecondToTimeString(long milliSeconds,
			boolean displayCentiSeconds) {
		long seconds = milliSeconds / 1000; // round down to compute seconds
		long minutes = seconds / 60;
		long hours = minutes / 60;
		long remainderMinutes = minutes - (hours * 60);
		long remainderSeconds = seconds - (minutes * 60);

		StringBuilder timeStringBuilder = new StringBuilder();

		// Hours
		if (hours > 0) {
			if (hours < 10) {
				timeStringBuilder.append('0');
			}
			timeStringBuilder.append(hours);

			timeStringBuilder.append(':');
		} else {
			timeStringBuilder.append('0');
			timeStringBuilder.append('0');
			timeStringBuilder.append(':');
		}

		// Minutes
		if (remainderMinutes < 10) {
			timeStringBuilder.append('0');
		}
		timeStringBuilder.append(remainderMinutes);
		timeStringBuilder.append(':');

		// Seconds
		if (remainderSeconds < 10) {
			timeStringBuilder.append('0');
		}
		timeStringBuilder.append(remainderSeconds);

		// Centi seconds
		if (displayCentiSeconds) {
			timeStringBuilder.append('.');
			long remainderCentiSeconds = (milliSeconds - seconds * 1000) / 10;
			if (remainderCentiSeconds < 10) {
				timeStringBuilder.append('0');
			}
			timeStringBuilder.append(remainderCentiSeconds);
		}

		return timeStringBuilder.toString();
	}

	private long getTimeLapseVideoLength(long deltaMs) {
		// For better approximation calculate fractional number of frames
		// captured.
		// This will update the video time at a higher resolution.
		double numberOfFrames = (double) deltaMs
				/ mTimeBetweenTimeLapseFrameCaptureMs;
		return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
	}

	private void initializeLocationSettings() {
		mIsRecordLocation = mPreferences.getBoolean(
				CameraSettings.KEY_RECORD_LOCATION, true);
		mLocationManager.recordLocation(mIsRecordLocation);
	}

	private void updateRecordingTime() {
		if (!mMediaRecorderRecording) {
			return;
		}
		long now = SystemClock.uptimeMillis();
		long delta = now - mRecordingStartTime;
		mUTVideoDuration = delta;

		// Starting a minute before reaching the max duration
		// limit, we'll countdown the remaining time instead.
		boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0 && delta >= mMaxVideoDurationInMs - 60000);

		long deltaAdjusted = delta;
		if (countdownRemainingTime) {
			deltaAdjusted = Math.max(0, mMaxVideoDurationInMs - deltaAdjusted) + 999;
		}
		String text;

		long targetNextUpdateDelay;
		if (!mCaptureTimeLapse) {
			text = millisecondToTimeString(deltaAdjusted, false);
			targetNextUpdateDelay = 1000;
		} else {
			// The length of time lapse video is different from the length
			// of the actual wall clock time elapsed. Display the video length
			// only in format hh:mm:ss.dd, where dd are the centi seconds.
			text = millisecondToTimeString(getTimeLapseVideoLength(delta), true);
			targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
		}

		mUI.setRecordingTime(text);

		if (mRecordingTimeCountsDown != countdownRemainingTime) {
			// Avoid setting the color on every update, do it only
			// when it needs changing.
			mRecordingTimeCountsDown = countdownRemainingTime;

			int color = mActivity
					.getResources()
					.getColor(
							countdownRemainingTime ? R.color.recording_time_remaining_text
									: R.color.recording_time_elapsed_text);

			mUI.setRecordingTimeTextColor(color);
		}

		long actualNextUpdateDelay = targetNextUpdateDelay
				- (delta % targetNextUpdateDelay);
		mHandler.sendEmptyMessageDelayed(UPDATE_RECORD_TIME,
				actualNextUpdateDelay);
	}

	@SuppressWarnings("deprecation")
	private void setCameraParameters() {
		Log.d("dyb", "desired preview size(" + mDesiredPreviewWidth + ","
				+ mDesiredPreviewHeight + ")");

		// if (previewBitmapBack == null && mCameraId == 0) {
		// int bestRatio = Util.findBestRatio(mDesiredPreviewWidth, 160);
		// previewBitmapBack = Bitmap.createBitmap(mDesiredPreviewHeight /
		// bestRatio,
		// mDesiredPreviewWidth / bestRatio, Config.ARGB_8888);
		// mActivity.getGLRootView().setMul(bestRatio);
		// } else if (previewBitmapFront == null && mCameraId != 0) {
		// int bestRatio = Util.findBestRatio(mDesiredPreviewWidth, 160);
		// previewBitmapFront = Bitmap.createBitmap(mDesiredPreviewHeight /
		// bestRatio,
		// mDesiredPreviewWidth / bestRatio, Config.ARGB_8888);
		// mActivity.getGLRootView().setMul(bestRatio);
		// }
		mParameters.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);

		mParameters.setPreviewFrameRate(mProfile.videoFrameRate);
		mSmoothZoomSupported = mParameters.isSmoothZoomSupported();
		PlatformHelper.setZSD(false, mParameters);
		// Set white balance parameter.
		String whiteBalance = mPreferences.getString(
				CameraSettings.KEY_WHITE_BALANCE,
				mActivity.getString(R.string.pref_camera_whitebalance_default));
		List<String> supportWhiteBalaces = mParameters
				.getSupportedWhiteBalance();
		if (Util.isSupported(whiteBalance, supportWhiteBalaces)) {
			mParameters.setWhiteBalance(whiteBalance);
		} else {
			whiteBalance = mParameters.getWhiteBalance();
			if (whiteBalance == null) {
				whiteBalance = Parameters.WHITE_BALANCE_AUTO;
			}
		}

		// Set zoom.
		if (mParameters.isZoomSupported()) {
			mParameters.setZoom(mZoomValue);
		}

		// Set continuous autofocus.
		List<String> supportedFocus = mParameters.getSupportedFocusModes();
		if (Util.isSupported(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
				supportedFocus)) {
			mParameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
		}

		//mParameters.set(Util.RECORDING_HINT, Util.TRUE);

		// Enable video stabilization. Convenience methods not available in API
		// level <= 14
		String vstabSupported = mParameters
				.get("video-stabilization-supported");
		if ("true".equals(vstabSupported)) {
			mParameters.set("video-stabilization", "true");
		}

		// Set picture size.
		// The logic here is different from the logic in still-mode camera.
		// There we determine the preview size based on the picture size, but
		// here we determine the picture size based on the preview size.
		List<Size> supported = mParameters.getSupportedPictureSizes();
		Size optimalSize = Util.getOptimalVideoSnapshotPictureSize(supported,
				(double) mDesiredPreviewWidth / mDesiredPreviewHeight);
		Size original = mParameters.getPictureSize();
		if (!original.equals(optimalSize)) {
			mParameters.setPictureSize(optimalSize.width, optimalSize.height);
		}
		Log.v(TAG, "Video snapshot size is " + optimalSize.width + "x"
				+ optimalSize.height);

		// Set JPEG quality.
		int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(
				mCameraId, CameraProfile.QUALITY_HIGH);
		mParameters.setJpegQuality(jpegQuality);

		// setFocusAreasIfSupported();

		mCameraDevice.setParameters(mParameters);
		// Keep preview size up to date.
		mParameters = mCameraDevice.getParameters();

		String key = mActivity
				.getString(R.string.camera_setting_item_shutter_sound_key);
		boolean shutterSound = mPreferences.getBoolean(key, true);
		if (shutterSound) {
			needShutterSound = true;
		} else {
			needShutterSound = false;
		}
		updateCameraScreenNailSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
	}

	private void updateCameraScreenNailSize(int width, int height) {

		if (mCameraDisplayOrientation % 180 != 0) {
			int tmp = width;
			width = height;
			height = tmp;
		}

		// // CameraScreenNail screenNail = (CameraScreenNail)
		// mActivity.mCameraScreenNail;
		// int oldWidth = screenNail.getWidth();
		// int oldHeight = screenNail.getHeight();
		//
		// if (oldWidth != width || oldHeight != height) {
		// screenNail.setSize(width, height);
		// // screenNail.enableAspectRatioClamping();
		// // mActivity.notifyScreenNailChanged();
		// }
		//
		// if (screenNail.getSurfaceTexture() == null) {
		// screenNail.acquireSurfaceTexture();
		// }
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	}

	public void onCancelBgTraining(View v) {
		// Write default effect out to shared prefs
		// Tell VideoCamer to re-init based on new shared pref values.
		onSharedPreferenceChanged();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Log.v(TAG, "onConfigurationChanged");
		setDisplayOrientation();
	}

	@Override
	public void onOverriddenPreferencesClicked() {
	}

	@Override
	// Delete this after old camera code is removed
	public void onRestorePreferencesClicked() {
	}

	@Override
	public void onSharedPreferenceChanged() {
		Log.v(TAG, "onSharedPreferenceChanged");
		// ignore the events after "onPause()" or preview has not started yet
		if (mPaused)
			return;
		synchronized (mPreferences) {
			// If mCameraDevice is not ready then we can set the parameter in
			// startPreview().
			if (mCameraDevice == null)
				return;

			initializeLocationSettings();

			readVideoPreferences();
			mUI.showTimeLapseUI(mCaptureTimeLapse);
			// We need to restart the preview if preview size is changed.
			Size size = mParameters.getPreviewSize();
			if (size.width != mDesiredPreviewWidth
					|| size.height != mDesiredPreviewHeight) {
				stopPreview();
				resizeForPreviewAspectRatio();
				startPreview(); // Parameters will be set in startPreview().
			} else {
				setCameraParameters();
			}
			mUI.updateOnScreenIndicators(mParameters, mPreferences);
		}
	}

	protected void setCameraId(int cameraId) {
		if (mPreferenceGroup == null)
			loadCameraPreferences();
		ListPreference pref = mPreferenceGroup
				.findPreference(CameraSettings.KEY_CAMERA_ID);
		if (pref != null)
			pref.setValue("" + cameraId);
	}

	private void switchCamera() {
		Log.v(TAG, "switchCamera");
		if (mPaused)
			return;

		mParameters = mCameraDevice.getParameters();
		Size previewSize = mParameters.getPreviewSize();
		if (PlatformHelper.supportSwitchBlur()) {
			if (mCameraId == 0)
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, previewData, mCameraId);
			else
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, previewData, mCameraId);
		}

		Log.d(TAG, "Start to switch camera.");
		mCameraId = mPendingSwitchCameraId;
		mPendingSwitchCameraId = -1;
		setCameraId(mCameraId);
		mUI.animateBeforeSwitchingCamera(mCameraId);
		stopPreview();
		if (mFocusManager != null)
			mFocusManager.removeMessages();

		mActivity.closeCamera();
		mCameraDevice = null;
		mCameraOpened = false;
		mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
		mFocusManager.onCameraReleased();

		mUI.collapseCameraControls();
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
		mCameraOpened = true;

		mFocusManager.setParameters(mParameters);
		if (mOpenCameraFail) {
			Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
			return;
		}
		initializeCapabilities();
		readVideoPreferences();
		startPreview();
		mUI.animateAfterSwitchingCamera(mCameraId);
		initializeVideoSnapshot();
		resizeForPreviewAspectRatio();

		loadCameraPreferences();

		// From onResume
		mUI.initializeZoom(mParameters);
		mUI.setOrientationIndicator(0, false);

		if (ApiHelper.HAS_SURFACE_TEXTURE) {
			// Start switch camera animation. Post a message because
			// onFrameAvailable from the old camera may already exist.
			mHandler.sendEmptyMessage(SWITCH_CAMERA_START_ANIMATION);
		}
		mUI.updateOnScreenIndicators(mParameters, mPreferences);
	}

	// Preview texture has been copied. Now camera can be released and the
	// animation can be started.
	@Override
	public void onPreviewTextureCopied() {
		mHandler.sendEmptyMessage(SWITCH_CAMERA);
	}

	@Override
	public void onCaptureTextureCopied() {
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent m) {
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_SWITCHING_CAMERA)
			return true;
		return mUI.dispatchTouchEvent(m);
	}

	private void initializeVideoSnapshot() {
		if (mParameters == null)
			return;
		if (Util.isVideoSnapshotSupported(mParameters)
				&& !mIsVideoCaptureIntent) {
			// mActivity.setSingleTapUpListener(mUI.getPreview());
			// Show the tap to focus toast if this is the first start.
			if (mPreferences.getBoolean(
					CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN, true)) {
				// Delay the toast for one second to wait for orientation.
				mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_SNAPSHOT_TOAST,
						1000);
			}
		} else {
			// mActivity.setSingleTapUpListener(null);
		}
	}

	void showVideoSnapshotUI(boolean enabled) {
		if (mParameters == null)
			return;
		if (Util.isVideoSnapshotSupported(mParameters)
				&& !mIsVideoCaptureIntent) {
			mUI.enableShutter(!enabled);
		}
	}

	@Override
	public void onFullScreenChanged(boolean full) {
		mUI.onFullScreenChanged(full);
		if (ApiHelper.HAS_SURFACE_TEXTURE) {
			// if (mActivity.mCameraScreenNail != null) {
			// ((CameraScreenNail)
			// mActivity.mCameraScreenNail).setFullScreen(full);
			// }
			return;
		}
	}

	private final class JpegPictureCallback implements PictureCallback {
		Location mLocation;

		public JpegPictureCallback(Location loc) {
			mLocation = loc;
		}

		@Override
		public void onPictureTaken(byte[] jpegData,
				android.hardware.Camera camera) {
			Log.v(TAG, "onPictureTaken");
			showVideoSnapshotUI(false);
			storeImage(jpegData, mLocation);
			mUI.enableSnapShotButton(true);
		}
	}

	private void storeImage(final byte[] data, Location loc) {
		long dateTaken = System.currentTimeMillis();
		String title = Util.createJpegName(dateTaken);
		ExifInterface exif = Exif.getExif(data);
		int orientation = Exif.getOrientation(exif);
		// Size s = mParameters.getPictureSize();
		// BugID:104020: use video profile size as video snapshot size
		// temporally only support MTK platform
		int width, height;
		if ((mJpegRotation + orientation) % 180 == 0) {
			width = mProfile.videoFrameWidth;
			height = mProfile.videoFrameHeight;
		} else {
			width = mProfile.videoFrameHeight;
			height = mProfile.videoFrameWidth;
		}
		mActivity.getMediaSaveService().addImage(data, title, dateTaken, loc,
				width, height, orientation, exif, mOnPhotoSavedListener,
				mContentResolver);
	}

	private String convertOutputFormatToMimeType(int outputFileFormat) {
		if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
			return "video/mp4";
		}
		return "video/3gpp";
	}

	private String convertOutputFormatToFileExt(int outputFileFormat) {
		if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
			return ".mp4";
		}
		return ".3gp";
	}

	private void closeVideoFileDescriptor() {
		if (mVideoFileDescriptor != null) {
			try {
				mVideoFileDescriptor.close();
			} catch (IOException e) {
				Log.e(TAG, "Fail to close fd", e);
			}
			mVideoFileDescriptor = null;
		}
	}

	private void showTapToSnapshotToast() {
		// new RotateTextToast(mActivity, R.string.video_snapshot_hint, 0)
		// .show();
		// Clear the preference.
		Editor editor = mPreferences.edit();
		editor.putBoolean(CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN, false);
		editor.apply();
	}

	@Override
	public boolean updateStorageHintOnResume() {
		return true;
	}

	// required by OnPreferenceChangedListener
	@Override
	public void onCameraPickerClicked(int cameraId) {
		if (mPaused || mPendingSwitchCameraId != -1)
			return;
		mPendingSwitchCameraId = cameraId;
		mSwitchingCamera = true;
		if (PlatformHelper.supportSwitchBlur()) {
			mCameraDevice.setOneshotPreviewCallback(this);
		} else {
			switchCamera();
		}
	}

	@Override
	public void onMediaSaveServiceConnected(MediaSaveService s) {
		// do nothing.
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

	// The subset of parameters we need to update in setCameraParameters().
	private static final int UPDATE_PARAM_INITIALIZE = 1;
	private static final int UPDATE_PARAM_ZOOM = 2;
	private static final int UPDATE_PARAM_PREFERENCE = 4;
	private static final int UPDATE_PARAM_ALL = -1;

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
		setAutoExposureLockIfSupported();
		setAutoWhiteBalanceLockIfSupported();
		setFocusAreasIfSupported();
		setMeteringAreasIfSupported();

		// Set a preview size that is closest to the viewfinder height and has
		// the right aspect ratio.
		List<Size> sizes = mParameters.getSupportedPreviewSizes();

		mFlashMode = mPreferences
				.getString(
						CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
						mActivity
								.getString(R.string.pref_camera_video_flashmode_default));

		List<String> supportedFlash = mParameters.getSupportedFlashModes();
		if (Util.isSupported(mFlashMode, supportedFlash) && !mIsInReviewMode) {
			mParameters.setFlashMode(mFlashMode);
		} else {
			mFlashMode = mParameters.getFlashMode();
			if (mFlashMode == null) {
				mFlashMode = mActivity
						.getString(R.string.pref_camera_flashmode_no_flash);
			}
		}
		mFocusManager.overrideFocusMode(null);
		mParameters.setFocusMode(mFocusManager.getFocusMode());
		mParameters.setAntibanding(Parameters.ANTIBANDING_AUTO);
		// updateAutoFocusMoveCallback();
	}

	private void setFocusAreasIfSupported() {
		if (mFocusAreaSupported && !mIsAutoFocusCallback) {
			mParameters.setFocusAreas(mFocusManager.getFocusAreas());
		}
	}

	private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();
	private final Object mAutoFocusMoveCallback = new AutoFocusMoveCallback();

	private void updateAutoFocusMoveCallback() {
		if (mParameters.getFocusMode().equals(
				Util.FOCUS_MODE_CONTINUOUS_PICTURE)) {
			mCameraDevice
					.setAutoFocusMoveCallback((AutoFocusMoveCallback) mAutoFocusMoveCallback);
		} else {
			mCameraDevice.setAutoFocusMoveCallback(null);
		}
	}

	@Override
	public void autoFocus() {
		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
			mActivity.setCameraState(CameraActivity.CAMERA_STATE_FOCUSING);
			mCameraDevice.autoFocus(mAutoFocusCallback);
		}

		mIsAutoFocusCallback = true;
	}

	@Override
	public void cancelAutoFocus() {
		if (mCameraDevice == null)
			return;

		if (PlatformHelper.supportVideoStartAsync()
				|| PlatformHelper.supportVideoStopAsync()) {
			try {
				synchronized (mLockCameraForAsyncAndFocus) {
					mCameraDevice.cancelAutoFocus();
				}

			} catch (Exception e) {
				Log.v(TAG, "cancelAutoFocus error!");
			}
		} else {
			mCameraDevice.cancelAutoFocus();
		}

		if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_FOCUSING) {
			mActivity.setCameraState(CameraActivity.CAMERA_STATE_PREVIEWING);
		}
		setCameraParameters(UPDATE_PARAM_PREFERENCE);
	}

	@Override
	public boolean capture() {
		return false;
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

	@Override
	public void onCameraFlashModeClicked(int modeId) {
		if (mPaused)
			return;
		IconListPreference pref = (IconListPreference) mPreferenceGroup
				.findPreference(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE);
		if (pref != null) {
			String value = (String) pref.getEntryValues()[(modeId + 1) % 2];
			Log.d("dyb", "pref count " + value);
			pref.setValue(value);
			mParameters.setFlashMode(value);
			if (mCameraDevice != null) {
				mCameraDevice.setParameters(mParameters);
			}
			// setCameraParameters(UPDATE_PARAM_ALL);
			mUI.updateFlashButton(value);
		}
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

		if (mIsVideoCaptureIntent || mMediaRecorderRecording) {
			return;
		}
		Log.v(TAG, "VideoModule -- onSwipe(), direction = " + direction
				+ ", mMediaRecorderRecording = " + mMediaRecorderRecording
				+ ", mCameraState = " + mActivity.getCameraState());
		if (direction == PreviewGestures.DIR_LEFT) {
			if (mActivity.getCameraState() == CameraActivity.CAMERA_STATE_PREVIEWING) {
				mPendingModuleIndex = CameraActivity.PHOTO_MODULE_INDEX;
				mCameraDevice.setOneshotPreviewCallback(this);
			}
		} else if (direction == PreviewGestures.DIR_RIGHT) {
			// do nothing currently
		}
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Log.v(CameraActivity.ChangeModuleTag, "onPreviewFrame video");
		previewData = data;
		if (mPendingModuleIndex != CameraActivity.UNKNOWN_MODULE_INDEX) {
			mParameters = mCameraDevice.getParameters();
			Size previewSize = mParameters.getPreviewSize();
			if (mCameraId == 0)
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, previewData, mCameraId);
			else
				mActivity.getGLRootView().startFadeIn(previewSize.width,
						previewSize.height, previewData, mCameraId);
			mUI.animateToModule(CameraActivity.PHOTO_MODULE_INDEX);
		} else if (mPendingSwitchCameraId != -1) {
			mHandler.sendEmptyMessage(SWITCH_CAMERA);
		}
	}

	@Override
	public void onFlipAnimationEnd() {
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

	private void setMeteringAreasIfSupported() {
		if (mMeteringAreaSupported && !mIsAutoFocusCallback) {
			// Use the same area for focus and metering.
			mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
		}
	}

	private void initializeCapabilities() {
		Parameters mInitialParams = mCameraDevice.getParameters();
		mFocusAreaSupported = Util.isFocusAreaSupported(mInitialParams);
		mMeteringAreaSupported = Util.isMeteringAreaSupported(mInitialParams);
		mAeLockSupported = Util.isAutoExposureLockSupported(mInitialParams);
		mAwbLockSupported = Util
				.isAutoWhiteBalanceLockSupported(mInitialParams);
	}

	@Override
	public void takeSnapshot() {
		Log.v(TAG, "takeSnapshot");
		// Set rotation and gps data.
		MediaSaveService s = mActivity.getMediaSaveService();
		if (mPaused || s == null || s.isQueueFull()) {
			Log.v(TAG, "takeSnapshot not allow");
			return;
		}
		mUI.enableSnapShotButton(false);
		mJpegRotation = Util.getJpegRotation(mCameraId, mOrientation);
		mParameters.setRotation(mJpegRotation);
		Location loc = mLocationManager.getCurrentLocation();
		Util.setGpsParameters(mParameters, loc);
		mCameraDevice.setParameters(mParameters);

		mUI.startCaptureAnimation();
		mCameraDevice.takePicture(null, null, null,
				new JpegPictureCallback(loc));
	}

	private int getPreferredQuality(int cameraId) {
		CAMCORDER_PROFILES = (mCameraId == 0) ? mActivity.getResources()
				.getIntArray(R.array.video_camcorder_profile_back) : mActivity
				.getResources().getIntArray(
						R.array.video_camcorder_profile_front);
		String key = mActivity
				.getString(R.string.camera_setting_item_video_resolution_key);
		String defValue = mActivity
				.getString(R.string.camera_setting_item_video_resolution_default);
		String value = mPreferences.getString(key, defValue);
		Log.d("dyb_setting", "value = " + value);
		String[] values = mActivity.getResources().getStringArray(
				R.array.entryvalues_camera_settings_video_resolution);
		int quality = 0;
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(value)) {
				Log.d("dyb_setting", "value " + i + " = " + values[i]);
				quality = CAMCORDER_PROFILES[i];
				break;
			}
		}
		Log.d("dyb_setting", "quality = " + quality);
		if (!PlatformHelper.CamcorderProfile_hasProfile(mCameraId, quality)) {
			quality = CamcorderProfile.QUALITY_HIGH;
		}
		return quality;
	}

	@Override
	public int getOrientation() {
		return mOrientation;
	}

	@Override
	public void onDestroy() {
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
		mHandler.sendEmptyMessage(CHANGE_MODULE);
	}

	@Override
	public boolean isVideoRecording() {
		return mMediaRecorderRecording;
	}

	@Override
	public boolean isVideoStarting() {
		return mStartVideoRecordingThread != null
				&& mStartVideoRecordingThread.isProcessing();
	}

	@Override
	public boolean isVideoStopping() {
		return mStopVideoRecordingThread != null
				&& mStopVideoRecordingThread.isProcessing();
	}

	@Override
	public int getCameradId() {
		return mCameraId;
	}

	@Override
	public void frameAvailable() {
		Log.v(CameraActivity.ChangeModuleTag, "frameAvailable video");
		mActivity.getGLRootView().startFadeOut();
	}

	@Override
	public void onAsyncStoped() {
		Log.v(TAG, "onAsyncStoped");
		mHandler.sendEmptyMessage(STOP_VIDEO_ASYNC_DONE);
	}

	@Override
	public void onAsyncStarted() {
		Log.v(TAG, "onAsyncStarted");
		mHandler.sendEmptyMessage(START_VIDEO_ASYNC_DONE);
	}

	@Override
	public MediaRecorder getMediaRecorder() {
		return mMediaRecorder;
	}

	private void onAsyncStopedImpl() {
		Log.v(TAG, "onAsyncStopedImpl");
		if (mMediaRecorderRecording) {
			mUI.showSaveUI();
			mCurrentVideoFilename = mVideoFilename;
			saveVideo();

			if (!mIsVideoCaptureIntent) {
				startPreview();
			}

			mMediaRecorderRecording = false;
			if (!Util.systemRotationLocked(mActivity)) {
				// mActivity.getOrientationManager().unlockOrientation();
			}

			// If the activity is paused, this means activity is interrupted
			// during recording. Release the camera as soon as possible because
			// face unlock or other applications may need to use the camera.
			// However, if the effects are active, then we can only release the
			// camera and cannot release the effects recorder since that will
			// stop the graph. It is possible to separate out the Camera release
			// part and the effects release part. However, the effects recorder
			// does hold on to the camera, hence, it needs to be "disconnected"
			// from the camera in the closeCamera call.

			mUI.showRecordingUI(false, mParameters.isZoomSupported());
			if (!mIsVideoCaptureIntent) {
				mUI.enableCameraControls(true);
			}
			// The orientation was fixed during video recording. Now make it
			// reflect the device orientation as video recording is stopped.
			mUI.setOrientationIndicator(0, true);
			keepScreenOnAwhile();

			mUI.hideSaveUI();
			synchronized (mStopVideoRecordingThread) {
				mStopVideoRecordingThread.setProcessing(false);
				mStopVideoRecordingThread.notifyAll();
				mStopVideoRecordingThread = null;
			}
		}
		// always release media recorder if no effects running
		releaseMediaRecorder();
		// whenever media recorder is released, report to user track
		if (!mPaused && mCameraDevice != null) {
			mCameraDevice.lock();
			mCameraDevice.waitDone();
			if (ApiHelper.HAS_SURFACE_TEXTURE
					&& !ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
				stopPreview();
				// Switch back to use SurfaceTexture for preview.
				// ((CameraScreenNail)
				// mActivity.mCameraScreenNail).setOneTimeOnFrameDrawnListener(
				// mFrameDrawnListener);
				startPreview();
			}
			// Update the parameters here because the parameters might have been
			// altered
			// by MediaRecorder.
			mParameters = mCameraDevice.getParameters();
		}
		mUI.rotateOutThumbnail();

		if (mCameraDevice != null) {
			// recover auto focus move while recording.
			mCameraDevice
					.setAutoFocusMoveCallback((android.hardware.Camera.AutoFocusMoveCallback) mAutoFocusMoveCallback);
		}

		// UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
		// fail ? UsageStatistics.ACTION_CAPTURE_FAIL :
		// UsageStatistics.ACTION_CAPTURE_DONE, "Video",
		// SystemClock.uptimeMillis() - mRecordingStartTime);

		if (!(mIsVideoCaptureIntent)) {
			mHandler.sendEmptyMessage(ENABLE_SHUTTER_BUTTON);
		}

		if (mPaused) {
			if (mFocusManager != null)
				mFocusManager.removeMessages();

			stopPreview();

			mCameraDevice.setZoomChangeListener(null);
			mCameraDevice.setErrorCallback(null);
			mActivity.closeCamera();
			mCameraDevice = null;
			mActivity
					.setCameraState(CameraActivity.CAMERA_STATE_PREVIEW_STOPPED);
			mFocusManager.onCameraReleased();

			// Close the file descriptor and clear the video namer only if the
			// effects are not active. If effects are active, we need to wait
			// till we get the callback from the Effects that the graph is done
			// recording. That also needs a change in the stopVideoRecording()
			// call to not call closeCamera if the effects are active, because
			// that will close down the effects are well, thus making this if
			// condition invalid.
			closeVideoFileDescriptor();

			releasePreviewResources();

			if (mReceiver != null) {
				mActivity.unregisterReceiver(mReceiver);
				mReceiver = null;
			}
			resetScreenOn();

			if (mLocationManager != null)
				mLocationManager.recordLocation(false);

			mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
			mHandler.removeMessages(SWITCH_CAMERA);
			mHandler.removeMessages(SWITCH_CAMERA_START_ANIMATION);
			mHandler.removeMessages(START_PREVIEW_DONE);
			mHandler.removeMessages(DISMISS_ZOOM_UI);
			mPendingSwitchCameraId = -1;
			mSwitchingCamera = false;
			// Call onPause after stopping video recording. So the camera can be
			// released as soon as possible.
		}

	}

	private void onAsyncStartedImpl() {
		Log.v(TAG, "onAsyncStartedImpl");

		// Make sure the video recording has started before announcing
		// this in accessibility.
		// AccessibilityUtils.makeAnnouncement(mActivity.getShutterButton(),
		// mActivity.getString(R.string.video_recording_started));

		// The parameters might have been altered by MediaRecorder already.
		// We need to force mCameraDevice to refresh before getting it.
		mCameraDevice.refreshParameters();
		// The parameters may have been changed by MediaRecorder upon starting
		// recording. We need to alter the parameters if we support camcorder
		// zoom. To reduce latency when setting the parameters during zoom, we
		// update mParameters here once.
		if (ApiHelper.HAS_ZOOM_WHEN_RECORDING) {
			mParameters = mCameraDevice.getParameters();
		}

		// cancel auto focus move while recording.
		mCameraDevice.setAutoFocusMoveCallback(null);

		mUI.enableCameraControls(false);

		mMediaRecorderRecording = true;
		if (!Util.systemRotationLocked(mActivity)) {
			// mActivity.getOrientationManager().lockOrientation();
		}
		synchronized (mStartVideoRecordingThread) {
			mStartVideoRecordingThread.setProcessing(false);
			mStartVideoRecordingThread.notifyAll();
			mStartVideoRecordingThread = null;
		}

		mRecordingStartTime = SystemClock.uptimeMillis();
		mUI.showRecordingUI(true, mParameters.isZoomSupported());

		updateRecordingTime();
		keepScreenOn();
		// UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
		// UsageStatistics.ACTION_CAPTURE_START, "Video");

		mHandler.sendEmptyMessage(ENABLE_SHUTTER_BUTTON);
	}
}
