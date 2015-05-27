package cc.fotoplace.camera;

import static cc.fotoplace.camera.Util.Assert;

import android.graphics.SurfaceTexture;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
//Yunos:Bug93380 fix Begin
import android.os.ConditionVariable;
//Yunos:Bug93380 fix End
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
//Yunos:Bug93380 fix Begin
import java.util.concurrent.Semaphore;

//Yunos:Bug93380 fix End

public class CameraManager {
	private static final String TAG = "CameraManager";
	private static CameraManager sCameraManager = new CameraManager();

	private ConditionVariable mSig = new ConditionVariable(); // ConditionVariable类方便线程同步操作。http://www.bdqn.cn/news/201306/9632.shtml
	private Parameters mParameters;
	private boolean mParametersIsDirty;
	private IOException mReconnectIOException;

	private static final int RELEASE = 1;
	private static final int RECONNECT = 2;
	private static final int UNLOCK = 3;
	private static final int LOCK = 4;
	private static final int SET_PREVIEW_TEXTURE_ASYNC = 5;
	private static final int START_PREVIEW_ASYNC = 6;
	private static final int STOP_PREVIEW = 7;
	private static final int SET_PREVIEW_CALLBACK_WITH_BUFFER = 8;
	private static final int ADD_CALLBACK_BUFFER = 9;
	private static final int AUTO_FOCUS = 10;
	private static final int CANCEL_AUTO_FOCUS = 11;
	private static final int SET_AUTO_FOCUS_MOVE_CALLBACK = 12;
	private static final int SET_DISPLAY_ORIENTATION = 13;
	private static final int SET_ZOOM_CHANGE_LISTENER = 14;
	private static final int SET_FACE_DETECTION_LISTENER = 15;
	private static final int START_FACE_DETECTION = 16;
	private static final int STOP_FACE_DETECTION = 17;
	private static final int SET_ERROR_CALLBACK = 18;
	private static final int SET_PARAMETERS = 19;
	private static final int GET_PARAMETERS = 20;
	private static final int SET_PREVIEW_DISPLAY_ASYNC = 21;
	private static final int SET_PREVIEW_CALLBACK = 22;
	private static final int ENABLE_SHUTTER_SOUND = 23;
	private static final int REFRESH_PARAMETERS = 24;
	private static final int SET_ONESHOT_PREVIEW_CALLBACK = 25;

	private Handler mCameraHandler;
	private CameraProxy mCameraProxy;
	private android.hardware.Camera mCamera;

	// Used to retain a copy of Parameters for setting parameters.
	private Parameters mParamsToSet;

	// This holder is used when we need to pass the exception
	// back to the calling thread. SynchornousQueue doesn't
	// allow we to pass a null object thus a holder is needed.

	public static CameraManager instance() {
		return sCameraManager;
	}

	private CameraManager() {
		HandlerThread ht = new HandlerThread("Camera Handler Thread");
		ht.start();
		mCameraHandler = new CameraHandler(ht.getLooper());
	}

	private class CameraHandler extends Handler {
		CameraHandler(Looper looper) {
			super(looper);
		}

		private void startFaceDetection() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera startFaceDetection begin");
			mCamera.startFaceDetection();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera startFaceDetection end");
		}

		private void stopFaceDetection() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera stopFaceDetection begin");
			mCamera.stopFaceDetection();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera stopFaceDetection end");
		}

		private void setFaceDetectionListener(FaceDetectionListener listener) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setFaceDetectionListener begin");
			mCamera.setFaceDetectionListener(listener);
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setFaceDetectionListener end");
		}

		private void setPreviewTexture(Object surfaceTexture) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setPreviewTexture begin");
			try {
				mCamera.setPreviewTexture((SurfaceTexture) surfaceTexture);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setPreviewTexture end");
		}

		private void enableShutterSound(boolean enable) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera enableShutterSound begin");
			mCamera.enableShutterSound(enable);
			Log.v(CameraActivity.HWPerformanceTag,
					"camera enableShutterSound begin");
		}

		/*
		 * This method does not deal with the build version check. Everyone
		 * should check first before sending message to this handler.
		 */
		@Override
		public void handleMessage(final Message msg) {
			try {
				switch (msg.what) {
				case RELEASE:
					mCamera.release();
					mCamera = null;
					mCameraProxy = null;
					break;

				case RECONNECT:
					mReconnectIOException = null;
					try {
						mCamera.reconnect();
					} catch (IOException ex) {
						mReconnectIOException = ex;
					}
					break;

				case UNLOCK:
					mCamera.unlock();
					break;

				case LOCK:
					mCamera.lock();
					break;

				case SET_PREVIEW_TEXTURE_ASYNC:
					setPreviewTexture(msg.obj);
					return; // no need to call mSig.open()

				case SET_PREVIEW_DISPLAY_ASYNC:
					try {
						mCamera.setPreviewDisplay((SurfaceHolder) msg.obj);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					return; // no need to call mSig.open()

				case START_PREVIEW_ASYNC:
					mCamera.startPreview();
					return; // no need to call mSig.open()

				case STOP_PREVIEW:
					mCamera.stopPreview();
					break;

				case SET_PREVIEW_CALLBACK_WITH_BUFFER:
					mCamera.setPreviewCallbackWithBuffer((PreviewCallback) msg.obj);
					break;

				case SET_ONESHOT_PREVIEW_CALLBACK:
					mCamera.setOneShotPreviewCallback((PreviewCallback) msg.obj);
					break;

				case ADD_CALLBACK_BUFFER:
					mCamera.addCallbackBuffer((byte[]) msg.obj);
					break;

				case AUTO_FOCUS:
					mCamera.autoFocus((AutoFocusCallback) msg.obj);
					break;

				case CANCEL_AUTO_FOCUS:
					mCamera.cancelAutoFocus();
					break;

				case SET_AUTO_FOCUS_MOVE_CALLBACK:
					setAutoFocusMoveCallback(mCamera, msg.obj);
					break;

				case SET_DISPLAY_ORIENTATION:
					mCamera.setDisplayOrientation(msg.arg1);
					break;

				case SET_ZOOM_CHANGE_LISTENER:
					mCamera.setZoomChangeListener((OnZoomChangeListener) msg.obj);
					break;

				case SET_FACE_DETECTION_LISTENER:
					setFaceDetectionListener((FaceDetectionListener) msg.obj);
					break;

				case START_FACE_DETECTION:
					startFaceDetection();
					break;

				case STOP_FACE_DETECTION:
					stopFaceDetection();
					break;

				case SET_ERROR_CALLBACK:
					mCamera.setErrorCallback((ErrorCallback) msg.obj);
					break;

				case SET_PARAMETERS:
					mParametersIsDirty = true;
					mParamsToSet.unflatten((String) msg.obj);
					mCamera.setParameters(mParamsToSet);
					break;

				case GET_PARAMETERS:
					if (mParametersIsDirty) {
						mParameters = mCamera.getParameters();
						mParametersIsDirty = false;
					}
					break;

				case SET_PREVIEW_CALLBACK:
					mCamera.setPreviewCallback((PreviewCallback) msg.obj);
					break;

				case ENABLE_SHUTTER_SOUND:
					enableShutterSound((msg.arg1 == 1) ? true : false);
					break;

				case REFRESH_PARAMETERS:
					mParametersIsDirty = true;
					break;

				default:
					break;
				}
			} catch (RuntimeException e) {
				if (msg.what != RELEASE && mCamera != null) {
					try {
						mCamera.release();
						mCameraProxy = null;
					} catch (Exception ex) {
						Log.e(TAG, "Fail to release the camera.");
					}
					mCamera = null;
				}
			}
			openSig();
		}
	}

	private void setAutoFocusMoveCallback(android.hardware.Camera camera,
			Object cb) {
		camera.setAutoFocusMoveCallback((AutoFocusMoveCallback) cb);
	}

	// Open camera synchronously. This method is invoked in the context of a
	// background thread.
	CameraProxy cameraOpen(int cameraId) {
		// Cannot open camera in mCameraHandler, otherwise all camera events
		// will be routed to mCameraHandler looper, which in turn will call
		// event handler like Camera.onFaceDetection, which in turn will modify
		// UI and cause exception like this:
		// CalledFromWrongThreadException: Only the original thread that created
		// a view hierarchy can touch its views.
		Log.v(CameraActivity.HWPerformanceTag, "camera open begin");
		mCamera = android.hardware.Camera.open(cameraId);
		Log.v(CameraActivity.HWPerformanceTag, "camera open end");
		if (mCamera != null) {
			mParametersIsDirty = true;
			if (mParamsToSet == null) {
				mParamsToSet = mCamera.getParameters();
			}
			mCameraProxy = new CameraProxy();
			return mCameraProxy;
		} else {
			return null;
		}
	}

	public class CameraProxy {

		private CameraProxy() {
			Assert(mCamera != null);
		}

		public android.hardware.Camera getCamera() {
			return mCamera;
		}

		public void release() {
			// release() must be synchronous so we know exactly when the camera
			// is released and can continue on.
			Log.v(CameraActivity.HWPerformanceTag, "camera release begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(RELEASE);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera release end");
		}

		public void reconnect() throws IOException {
			Log.v(CameraActivity.HWPerformanceTag, "camera reconnect begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(RECONNECT);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera reconnect end");
			if (mReconnectIOException != null) {
				throw mReconnectIOException;
			}
		}

		public void unlock() {
			Log.v(CameraActivity.HWPerformanceTag, "camera unlock begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(UNLOCK);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera unlock end");
		}

		public void lock() {
			Log.v(CameraActivity.HWPerformanceTag, "camera lock begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(LOCK);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera lock end");
		}

		public void setPreviewTextureAsync(final SurfaceTexture surfaceTexture) {
			mCameraHandler.obtainMessage(SET_PREVIEW_TEXTURE_ASYNC,
					surfaceTexture).sendToTarget();
		}

		public void setPreviewDisplayAsync(final SurfaceHolder surfaceHolder) {
			mCameraHandler.obtainMessage(SET_PREVIEW_DISPLAY_ASYNC,
					surfaceHolder).sendToTarget();
		}

		public void startPreviewAsync() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera startPreviewAsync begin");
			mCameraHandler.sendEmptyMessage(START_PREVIEW_ASYNC);
			Log.v(CameraActivity.HWPerformanceTag,
					"camera startPreviewAsync end");
		}

		// stopPreview() is synchronous because many resources should be
		// released 停止预览相机
		// the preview is stopped.
		public void stopPreview() {
			Log.v(CameraActivity.HWPerformanceTag, "camera stop preview begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(STOP_PREVIEW);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera stop preview end");
		}

		public void startSmoothZoom(int zoom) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera startSmoothZoom begin");
			mCamera.startSmoothZoom(zoom);
			Log.v(CameraActivity.HWPerformanceTag, "camera startSmoothZoom end");
		}

		public void stopSmoothZoom() {
			Log.v(CameraActivity.HWPerformanceTag, "camera stopSmoothZoom end");
			mCamera.stopSmoothZoom();
			Log.v(CameraActivity.HWPerformanceTag, "camera stopSmoothZoom end");
		}
      //设置
		public void setPreviewCallback(final PreviewCallback cb) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setPreviewCallback begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_PREVIEW_CALLBACK, cb)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setPreviewCallback end");
		}

		public void setOneshotPreviewCallback(final PreviewCallback cb) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setOneshotPreviewCallback begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_ONESHOT_PREVIEW_CALLBACK, cb)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setOneshotPreviewCallback end");
		}

		public void setPreviewCallbackWithBuffer(final PreviewCallback cb) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setPreviewCallbackWithBuffer begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_PREVIEW_CALLBACK_WITH_BUFFER, cb)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setPreviewCallbackWithBuffer end");
		}

		public void addCallbackBuffer(byte[] callbackBuffer) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera addCallbackBuffer begin");
			closeSig();
			mCameraHandler.obtainMessage(ADD_CALLBACK_BUFFER, callbackBuffer)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera addCallbackBuffer end");
		}

		public void autoFocus(AutoFocusCallback cb) {
			Log.v(CameraActivity.HWPerformanceTag, "camera auto focus begin");
			closeSig();
			mCameraHandler.obtainMessage(AUTO_FOCUS, cb).sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera auto focus end");
		}

		public void cancelAutoFocus() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera cancel auto focus begin");
			closeSig();
			mCameraHandler.removeMessages(AUTO_FOCUS);
			mCameraHandler.sendEmptyMessage(CANCEL_AUTO_FOCUS);
			mCameraHandler.removeMessages(SET_AUTO_FOCUS_MOVE_CALLBACK);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera cancel auto focus end");
		}

		public void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setAutoFocusMoveCallback begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_AUTO_FOCUS_MOVE_CALLBACK, cb)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setAutoFocusMoveCallback end");
		}

		public void takePicture(final ShutterCallback shutter,
				final PictureCallback raw, final PictureCallback postview,
				final PictureCallback jpeg) {
			Log.v(CameraActivity.HWPerformanceTag, "camera take picture begin");
			closeSig();
			// Too many parameters, so use post for simplicity
			mCameraHandler.post(new Runnable() {
				@Override
				public void run() {
					mCamera.takePicture(shutter, raw, postview, jpeg);
					openSig();
				}
			});
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera take picture end");
		}

		public void takePicture2(final ShutterCallback shutter,
				final PictureCallback raw, final PictureCallback postview,
				final PictureCallback jpeg, final int cameraState,
				final int focusState) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera take picture 2 begin");
			closeSig();
			// Too many parameters, so use post for simplicity
			mCameraHandler.post(new Runnable() {
				@Override
				public void run() {
					try {
						mCamera.takePicture(shutter, raw, postview, jpeg);
						openSig();
					} catch (RuntimeException e) {
						Log.w(TAG, "take picture failed; cameraState:"
								+ cameraState + ", focusState:" + focusState);
						throw e;
					}
				}
			});
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera take picture 2 end");
		}

		public void setDisplayOrientation(int degrees) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setDisplayOrientation begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_DISPLAY_ORIENTATION, degrees, 0)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setDisplayOrientation end");
		}

		public void setZoomChangeListener(OnZoomChangeListener listener) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setZoomChangeListener begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_ZOOM_CHANGE_LISTENER, listener)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setZoomChangeListener end");
		}

		public void setFaceDetectionListener(FaceDetectionListener listener) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setFaceDetectionListener begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_FACE_DETECTION_LISTENER, listener)
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setFaceDetectionListener end");
		}

		public void startFaceDetection() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera startFaceDetection begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(START_FACE_DETECTION);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera startFaceDetection end");
		}

		public void stopFaceDetection() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera stopFaceDetection begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(STOP_FACE_DETECTION);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera stopFaceDetection end");
		}

		public void setErrorCallback(ErrorCallback cb) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setErrorCallback begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_ERROR_CALLBACK, cb).sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera setErrorCallback end");
		}

		public void setParameters(Parameters params) {
			if (params == null) {
				Log.v(TAG, "null parameters in setParameters()");
				return;
			}
			Log.v(CameraActivity.HWPerformanceTag,
					"camera set parameters begin");
			closeSig();
			mCameraHandler.obtainMessage(SET_PARAMETERS, params.flatten())
					.sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera set parameters end");
		}

		public Parameters getParameters() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera get parameters begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(GET_PARAMETERS);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag, "camera get parameters end");
			return mParameters;
		}

		public void refreshParameters() {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera refresh parameters begin");
			closeSig();
			mCameraHandler.sendEmptyMessage(REFRESH_PARAMETERS);
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera refresh parameters end");
		}

		public void enableShutterSound(boolean enable) {
			Log.v(CameraActivity.HWPerformanceTag,
					"camera enableShutterSound begin");
			closeSig();
			mCameraHandler.obtainMessage(ENABLE_SHUTTER_SOUND,
					(enable ? 1 : 0), 0).sendToTarget();
			blockSig();
			Log.v(CameraActivity.HWPerformanceTag,
					"camera enableShutterSound end");
		}

		// return false if cancelled.
		public boolean waitDone() {
			final Object waitDoneLock = new Object();
			final Runnable unlockRunnable = new Runnable() {
				@Override
				public void run() {
					synchronized (waitDoneLock) {
						waitDoneLock.notifyAll();
					}
				}
			};

			synchronized (waitDoneLock) {
				mCameraHandler.post(unlockRunnable);
				try {
					waitDoneLock.wait();
				} catch (InterruptedException ex) {
					Log.v(TAG, "waitDone interrupted");
					return false;
				}
			}
			return true;
		}
	}

	// / M: ConditionVariable may open multi thread, so here we use semphore to
	// lock camera proxy.
	private Semaphore mSemphore = new Semaphore(1);

	// 重置

	private void closeSig() {
		Log.d(TAG, "sginal: acquiring semphore");// , new Throwable());
		try {
			mSemphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mSig.close();
		Log.d(TAG, "sginal: acquired semphore");
	}

	// 阻塞
	private void blockSig() {
		Log.d(TAG, "sginal: blocking");
		mSig.block();
		Log.d(TAG, "sginal: released blocking");
	}

	private void openSig() {
		Log.d(TAG, "sginal: releasing semphore");
		mSig.open();
		mSemphore.release();
		Log.d(TAG, "sginal: released semphore");
	}
}
