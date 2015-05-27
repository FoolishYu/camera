package cc.fotoplace.camera;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;

/**
 * Collection of utility functions used in this package.
 */
public class Util {
	private static final String TAG = "Util";

	// Orientation hysteresis amount used in rounding, in degrees
	public static final int ORIENTATION_HYSTERESIS = 5;

	public static final String REVIEW_ACTION = "com.yunos.camera.action.REVIEW";
	// See android.hardware.Camera.ACTION_NEW_PICTURE.
	public static final String ACTION_NEW_PICTURE = "android.hardware.action.NEW_PICTURE";
	// See android.hardware.Camera.ACTION_NEW_VIDEO.
	public static final String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";

	// Fields from android.hardware.Camera.Parameters
	public static final String FOCUS_MODE_CONTINUOUS_PICTURE = "continuous-picture";
	public static final String RECORDING_HINT = "recording-hint";//录像指示器
	private static final String AUTO_EXPOSURE_LOCK_SUPPORTED = "auto-exposure-lock-supported";
	private static final String AUTO_WHITE_BALANCE_LOCK_SUPPORTED = "auto-whitebalance-lock-supported";
	private static final String VIDEO_SNAPSHOT_SUPPORTED = "video-snapshot-supported";
	public static final String SCENE_MODE_HDR = "hdr";
	public static final String TRUE = "true";
	public static final String FALSE = "false";
//	public static final String SCAN_ACTION = "com.yunos.camera.SCAN_QRCODE";
	public static int displayWidth;
	public static int displayHeight;
	public static boolean USE_FILTER = true;

	public static boolean isSupported(String value, List<String> supported) {
		return supported == null ? false : supported.indexOf(value) >= 0;
	}

	public static boolean isAutoExposureLockSupported(Parameters params) {
		return TRUE.equals(params.get(AUTO_EXPOSURE_LOCK_SUPPORTED));
	}

	public static boolean isAutoWhiteBalanceLockSupported(Parameters params) {
		return TRUE.equals(params.get(AUTO_WHITE_BALANCE_LOCK_SUPPORTED));
	}

	public static boolean isVideoSnapshotSupported(Parameters params) {
		return TRUE.equals(params.get(VIDEO_SNAPSHOT_SUPPORTED));
	}

	public static boolean isCameraHdrSupported(Parameters params) {
		List<String> supported = params.getSupportedSceneModes();
		return (supported != null) && supported.contains(SCENE_MODE_HDR);
	}

	public static boolean isMeteringAreaSupported(Parameters params) {
		//// 检查是否支持测光区域 
		return params.getMaxNumMeteringAreas() > 0;
	}

	public static boolean isFocusAreaSupported(Parameters params) {
		return (params.getMaxNumFocusAreas() > 0 && isSupported(
				Parameters.FOCUS_MODE_AUTO, params.getSupportedFocusModes()));

	}

	// Private intent extras. Test only.
	private static final String EXTRAS_CAMERA_FACING = "android.intent.extras.CAMERA_FACING";

	private static float sPixelDensity = 1;
	private static ImageFileNamer sImageFileNamer;

	private Util() {
	}

	public static void initialize(Context context) {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager wm = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(metrics);
		displayWidth = metrics.widthPixels;
		displayHeight = metrics.heightPixels;
		sPixelDensity = metrics.density;
		sImageFileNamer = new ImageFileNamer(
				context.getString(R.string.image_file_name_format));
	}

	public static int dpToPixel(int dp) {
		return Math.round(sPixelDensity * dp);
	}

	// Rotates the bitmap by the specified degree.
	// If a new bitmap is created, the original bitmap is recycled.
	public static Bitmap rotate(Bitmap b, int degrees) {
		return rotateAndMirror(b, degrees, false);
	}

	// Rotates and/or mirrors the bitmap. If a new bitmap is created, the
	// original bitmap is recycled.
	public static Bitmap rotateAndMirror(Bitmap b, int degrees, boolean mirror) {
		if ((degrees != 0 || mirror) && b != null) {
			Matrix m = new Matrix();
			// Mirror first.
			// horizontal flip + rotation = -rotation + horizontal flip
			if (mirror) {
				m.postScale(-1, 1);
				degrees = (degrees + 360) % 360;
				if (degrees == 0 || degrees == 180) {
					m.postTranslate(b.getWidth(), 0);
				} else if (degrees == 90 || degrees == 270) {
					m.postTranslate(b.getHeight(), 0);
				} else {
					throw new IllegalArgumentException("Invalid degrees="
							+ degrees);
				}
			}
			if (degrees != 0) {
				// clockwise
				m.postRotate(degrees, (float) b.getWidth() / 2,
						(float) b.getHeight() / 2);
			}

			try {
				Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(),
						b.getHeight(), m, true);
				if (b != b2) {
					b.recycle();
					b = b2;
				}
			} catch (OutOfMemoryError ex) {
				// We have no memory to rotate. Return the original bitmap.
			}
		}
		return b;
	}

	/*
	 * Compute the sample size as a function of minSideLength and
	 * maxNumOfPixels. minSideLength is used to specify that minimal width or
	 * height of a bitmap. maxNumOfPixels is used to specify the maximal size in
	 * pixels that is tolerable in terms of memory usage. The function returns a
	 * sample size based on the constraints. Both size and minSideLength can be
	 * passed in as -1 which indicates no care of the corresponding constraint.
	 * The functions prefers returning a sample size that generates a smaller
	 * bitmap, unless minSideLength = -1. Also, the function rounds up the
	 * sample size to a power of 2 or multiple of 8 because BitmapFactory only
	 * honors sample size this way. For example, BitmapFactory downsamples an
	 * image by 2 even though the request is 3. So we round up the sample size
	 * to avoid OOM.
	 */
	public static int computeSampleSize(BitmapFactory.Options options,
			int minSideLength, int maxNumOfPixels) {
		int initialSize = computeInitialSampleSize(options, minSideLength,
				maxNumOfPixels);

		int roundedSize;
		if (initialSize <= 8) {
			roundedSize = 1;
			while (roundedSize < initialSize) {
				roundedSize <<= 1;
			}
		} else {
			roundedSize = (initialSize + 7) / 8 * 8;
		}

		return roundedSize;
	}

	private static int computeInitialSampleSize(BitmapFactory.Options options,
			int minSideLength, int maxNumOfPixels) {
		double w = options.outWidth;
		double h = options.outHeight;

		int lowerBound = (maxNumOfPixels < 0) ? 1 : (int) Math.ceil(Math.sqrt(w
				* h / maxNumOfPixels));
		int upperBound = (minSideLength < 0) ? 128 : (int) Math.min(
				Math.floor(w / minSideLength), Math.floor(h / minSideLength));

		if (upperBound < lowerBound) {
			// return the larger one when there is no overlapping zone.
			return lowerBound;
		}

		if (maxNumOfPixels < 0 && minSideLength < 0) {
			return 1;
		} else if (minSideLength < 0) {
			return lowerBound;
		} else {
			return upperBound;
		}
	}

	public static Bitmap makeBitmap(byte[] jpegData, int maxNumOfPixels) {
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory
					.decodeByteArray(jpegData, 0, jpegData.length, options);
			if (options.mCancel || options.outWidth == -1
					|| options.outHeight == -1) {
				return null;
			}
			options.inSampleSize = computeSampleSize(options, -1,
					maxNumOfPixels);
			options.inJustDecodeBounds = false;

			options.inDither = false;
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length,
					options);
		} catch (OutOfMemoryError ex) {
			Log.e(TAG, "Got oom exception ", ex);
			return null;
		}
	}

	public static void closeSilently(Closeable c) {
		if (c == null)
			return;
		try {
			c.close();
		} catch (Throwable t) {
			// do nothing
		}
	}

	public static void Assert(boolean cond) {
		if (!cond) {
			// AssertionError(示例,出错代码）
			throw new AssertionError();
		}
	}

	private static void throwIfCameraDisabled(Activity activity)
			throws CameraDisabledException {
		// Check if device policy has disabled the camera.
		DevicePolicyManager dpm = (DevicePolicyManager) activity
				.getSystemService(Context.DEVICE_POLICY_SERVICE);
		if (dpm.getCameraDisabled(null)) {
			throw new CameraDisabledException();
		}
	}

	public static CameraManager.CameraProxy openCamera(Activity activity,
			int cameraId) throws CameraHardwareException,
			CameraDisabledException {
		throwIfCameraDisabled(activity);

		try {
			return CameraHolder.instance().open(cameraId);
		} catch (CameraHardwareException e) {
			throw e;
		}
	}

	public static void showErrorAndFinish(final Activity activity, int msgId) {
		DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				activity.finish();
			}
		};
		TypedValue out = new TypedValue();
		activity.getTheme().resolveAttribute(android.R.attr.alertDialogIcon,
				out, true);
		new AlertDialog.Builder(activity).setCancelable(false)
				.setTitle(R.string.camera_error_title).setMessage(msgId)
				.setNeutralButton(android.R.string.ok, buttonListener)
				.setIcon(out.resourceId).show();
	}

	public static <T> T checkNotNull(T object) {
		if (object == null)
			throw new NullPointerException();
		return object;
	}

	public static boolean equals(Object a, Object b) {
		return (a == b) || (a == null ? false : a.equals(b));
	}

	public static int nextPowerOf2(int n) {
		n -= 1;
		n |= n >>> 16;
		n |= n >>> 8;
		n |= n >>> 4;
		n |= n >>> 2;
		n |= n >>> 1;
		return n + 1;
	}

	public static float distance(float x, float y, float sx, float sy) {
		float dx = x - sx;
		float dy = y - sy;
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	public static int clamp(int x, int min, int max) {
		if (x > max)
			return max;
		if (x < min)
			return min;
		return x;
	}

	public static boolean systemRotationLocked(Activity activity) {
		return Settings.System.getInt(activity.getContentResolver(),
				Settings.System.ACCELEROMETER_ROTATION, 0) == 0;
	}

	public static int getDisplayRotation(Activity activity) {
		int rotation = activity.getWindowManager().getDefaultDisplay()
				.getRotation();
		switch (rotation) {
		case Surface.ROTATION_0:
			return 0;
		case Surface.ROTATION_90:
			return 90;
		case Surface.ROTATION_180:
			return 180;
		case Surface.ROTATION_270:
			return 270;
		}
		return 0;
	}

	public static int getDisplayOrientation(int degrees, int cameraId) {
		// See android.hardware.Camera.setDisplayOrientation for
		// documentation.
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		} else { // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		return result;
	}

	public static int getCameraOrientation(int cameraId) {
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		return info.orientation;
	}

	public static int roundOrientation(int orientation, int orientationHistory) {
		boolean changeOrientation = false;
		if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
			changeOrientation = true;
		} else {
			int dist = Math.abs(orientation - orientationHistory);
			dist = Math.min(dist, 360 - dist);
			changeOrientation = (dist >= 45 + ORIENTATION_HYSTERESIS);
		}
		if (changeOrientation) {
			return ((orientation + 45) / 90 * 90) % 360;
		}
		return orientationHistory;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private static Point getDefaultDisplaySize(Activity activity, Point size) {
		Display d = activity.getWindowManager().getDefaultDisplay();
		d.getSize(size);
		return size;
	}

	public static Size getOptimalPreviewSize(Activity currentActivity,
			List<Size> sizes, double targetRatio) {
		// Use a very small tolerance because we want an exact match.
		final double ASPECT_TOLERANCE = 0.001;
		if (sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		// Because of bugs of overlay and layout, we sometimes will try to
		// layout the viewfinder in the portrait orientation and thus get the
		// wrong size of preview surface. When we change the preview size, the
		// new overlay will be created before the old one closed, which causes
		// an exception. For now, just get the screen size.
		Point point = getDefaultDisplaySize(currentActivity, new Point());
		int targetHeight = Math.min(point.x, point.y);
		// Try to find an size match aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}
		// Cannot find the one match the aspect ratio. This should not happen.
		// Ignore the requirement.
		if (optimalSize == null) {
			Log.w(TAG, "No preview size match the aspect ratio");
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	// Returns the largest picture size which matches the given aspect ratio.
	public static Size getOptimalVideoSnapshotPictureSize(List<Size> sizes,
			double targetRatio) {
		// Use a very small tolerance because we want an exact match.
		final double ASPECT_TOLERANCE = 0.001;
		if (sizes == null)
			return null;

		Size optimalSize = null;

		// Try to find a size matches aspect ratio and has the largest width
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (optimalSize == null || size.width > optimalSize.width) {
				optimalSize = size;
			}
		}

		// Cannot find one that matches the aspect ratio. This should not
		// happen.
		// Ignore the requirement.
		if (optimalSize == null) {
			Log.w(TAG, "No picture size match the aspect ratio");
			for (Size size : sizes) {
				if (optimalSize == null || size.width > optimalSize.width) {
					optimalSize = size;
				}
			}
		}
		return optimalSize;
	}

	public static void dumpParameters(Parameters parameters) {
		String flattened = parameters.flatten();
		StringTokenizer tokenizer = new StringTokenizer(flattened, ";");
		Log.d(TAG, "Dump all camera parameters:");
		while (tokenizer.hasMoreElements()) {
			Log.d(TAG, tokenizer.nextToken());
		}
	}

	/**
	 * Returns whether the device is voice-capable (meaning, it can do MMS).
	 */
	public static boolean isMmsCapable(Context context) {
		TelephonyManager telephonyManager = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager == null) {
			return false;
		}

		try {
			Class<?> partypes[] = new Class[0];
			Method sIsVoiceCapable = TelephonyManager.class.getMethod(
					"isVoiceCapable", partypes);

			Object arglist[] = new Object[0];
			Object retobj = sIsVoiceCapable.invoke(telephonyManager, arglist);
			return (Boolean) retobj;
		} catch (java.lang.reflect.InvocationTargetException ite) {
			// Failure, must be another device.
			// Assume that it is voice capable.
		} catch (IllegalAccessException iae) {
			// Failure, must be an other device.
			// Assume that it is voice capable.
		} catch (NoSuchMethodException nsme) {
		}
		return true;
	}

	// This is for test only. Allow the camera to launch the specific camera.
	public static int getCameraFacingIntentExtras(Activity currentActivity) {
		int cameraId = -1;

		int intentCameraId = currentActivity.getIntent().getIntExtra(
				Util.EXTRAS_CAMERA_FACING, -1);

		if (isFrontCameraIntent(intentCameraId)) {
			// Check if the front camera exist
			int frontCameraId = CameraHolder.instance().getFrontCameraId();
			if (frontCameraId != -1) {
				cameraId = frontCameraId;
			}
		} else if (isBackCameraIntent(intentCameraId)) {
			// Check if the back camera exist
			int backCameraId = CameraHolder.instance().getBackCameraId();
			if (backCameraId != -1) {
				cameraId = backCameraId;
			}
		}
		return cameraId;
	}

	private static boolean isFrontCameraIntent(int intentCameraId) {
		return (intentCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
	}

	private static boolean isBackCameraIntent(int intentCameraId) {
		return (intentCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK);
	}

	private static int sLocation[] = new int[2];

	// This method is not thread-safe.
	public static boolean pointInView(float x, float y, View v) {
		v.getLocationInWindow(sLocation);
		return x >= sLocation[0] && x < (sLocation[0] + v.getWidth())
				&& y >= sLocation[1] && y < (sLocation[1] + v.getHeight());
	}

	public static int[] getRelativeLocation(View reference, View view) {
		reference.getLocationInWindow(sLocation);
		int referenceX = sLocation[0];
		int referenceY = sLocation[1];
		view.getLocationInWindow(sLocation);
		sLocation[0] -= referenceX;
		sLocation[1] -= referenceY;
		return sLocation;
	}

	public static boolean isUriValid(Uri uri, ContentResolver resolver) {
		if (uri == null)
			return false;

		try {
			ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
			if (pfd == null) {
				Log.e(TAG, "Fail to open URI. URI=" + uri);
				return false;
			}
			pfd.close();
		} catch (IOException ex) {
			return false;
		}
		return true;
	}

	public static void viewUri(Uri uri, Context context) {
		if (!isUriValid(uri, context.getContentResolver())) {
			Log.e(TAG, "Uri invalid. uri=" + uri);
			return;
		}

		try {
			context.startActivity(new Intent(Util.REVIEW_ACTION, uri));
		} catch (ActivityNotFoundException ex) {
			try {
				context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
			} catch (ActivityNotFoundException e) {
				Log.e(TAG, "review image fail. uri=" + uri, e);
			}
		}
	}

	public static void dumpRect(RectF rect, String msg) {
		Log.v(TAG, msg + "=(" + rect.left + "," + rect.top + "," + rect.right
				+ "," + rect.bottom + ")");
	}

	public static void rectFToRect(RectF rectF, Rect rect) {
		rect.left = Math.round(rectF.left);
		rect.top = Math.round(rectF.top);
		rect.right = Math.round(rectF.right);
		rect.bottom = Math.round(rectF.bottom);
	}

	public static void prepareMatrix(Matrix matrix, boolean mirror,
			int displayOrientation, int viewWidth, int viewHeight) {
		// Need mirror for front camera.
		matrix.setScale(mirror ? -1 : 1, 1);
		// This is the value for android.hardware.Camera.setDisplayOrientation.
		matrix.postRotate(displayOrientation);
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
		matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
	}

	public static String createJpegName(long dateTaken) {
		synchronized (sImageFileNamer) {
			return sImageFileNamer.generateName(dateTaken);
		}
	}

	public static void broadcastNewPicture(Context context, Uri uri) {
		context.sendBroadcast(new Intent(ACTION_NEW_PICTURE, uri));
		// Keep compatibility
		context.sendBroadcast(new Intent("cc.fotoplace.camera.NEW_PICTURE", uri));
	}

	public static void fadeIn(View view, float startAlpha, float endAlpha,
			long duration) {
		if (view.getVisibility() == View.VISIBLE)
			return;

		view.setVisibility(View.VISIBLE);
		Animation animation = new AlphaAnimation(startAlpha, endAlpha);
		animation.setDuration(duration);
		view.startAnimation(animation);
		// We disabled the button in fadeOut(), so enable it here.
		view.setEnabled(true);
	}

	public static void fadeIn(View view) {
		fadeIn(view, 0F, 1F, 400);
	}

	public static void fadeOut(View view, float startAlpha, float endAlpha,
			long duration) {
		if (view.getVisibility() != View.VISIBLE)
			return;

		// Since the button is still clickable before fade-out animation
		// ends, we disable the button first to block click.
		view.setEnabled(false);
		Animation animation = new AlphaAnimation(startAlpha, endAlpha);
		animation.setDuration(duration);
		view.startAnimation(animation);
		view.setVisibility(View.GONE);
	}

	public static void fadeOut(View view) {
		fadeOut(view, 1F, 0F, 400);
	}

	public static void transistTo(Context context, ImageView view,
			int fromResId, int toResId, int duration) {
		TransitionDrawable tranDrawable = new TransitionDrawable(
				new Drawable[] { context.getResources().getDrawable(fromResId),
						context.getResources().getDrawable(toResId), });
		view.setImageDrawable(tranDrawable);
		tranDrawable.setCrossFadeEnabled(true);
		tranDrawable.startTransition(duration);
	}

	public static void alphaOut(View view, float startAlpha, float endAlpha,
			long duration) {
		if (view.getVisibility() != View.VISIBLE)
			return;
		// Since the button is still clickable before fade-out animation
		// ends, we disable the button first to block click.
		view.setEnabled(false);
		Animation animation = new AlphaAnimation(startAlpha, endAlpha);
		animation.setDuration(duration);
		animation.setFillAfter(true);
		view.startAnimation(animation);
	}

	public static void alphaOut(View view) {
		alphaOut(view, 1F, 0F, 400);
	}

	public static void alphaIn(View view, float startAlpha, float endAlpha,
			long duration) {
		if (view.getVisibility() != View.VISIBLE)
			return;
		Animation animation = new AlphaAnimation(startAlpha, endAlpha);
		animation.setDuration(duration);
		animation.setFillAfter(true);
		view.startAnimation(animation);
		// We disabled the button in fadeOut(), so enable it here.
		view.setEnabled(true);
	}

	public static void alphaIn(View view) {
		alphaIn(view, 0F, 1F, 400);
	}

	public static int getJpegRotation(int cameraId, int orientation) {
		// See android.hardware.Camera.Parameters.setRotation for
		// documentation.
		int rotation = 0;
		if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
			CameraInfo info = CameraHolder.instance().getCameraInfo()[cameraId];
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				rotation = (info.orientation - orientation + 360) % 360;
			} else { // back-facing camera
				rotation = (info.orientation + orientation) % 360;
			}
		}
		return rotation;
	}

	public static void setGpsParameters(Parameters parameters, Location loc) {
		// Clear previous GPS location from the parameters.
		parameters.removeGpsData();

		// We always encode GpsTimeStamp
		parameters.setGpsTimestamp(System.currentTimeMillis() / 1000);

		// Set GPS location.
		if (loc != null) {
			double lat = loc.getLatitude();
			double lon = loc.getLongitude();
			boolean hasLatLon = (lat != 0.0d) || (lon != 0.0d);

			if (hasLatLon) {
				Log.d(TAG, "Set gps location");
				parameters.setGpsLatitude(lat);
				parameters.setGpsLongitude(lon);
				parameters.setGpsProcessingMethod(loc.getProvider()
						.toUpperCase(Locale.US));
				if (loc.hasAltitude()) {
					parameters.setGpsAltitude(loc.getAltitude());
				} else {
					// for NETWORK_PROVIDER location provider, we may have
					// no altitude information, but the driver needs it, so
					// we fake one.
					parameters.setGpsAltitude(0);
				}
				// if (loc.getTime() != 0) {
				// Location.getTime() is UTC in milliseconds.
				// gps-timestamp is UTC in seconds.
				// long utcTimeSeconds = loc.getTime() / 1000;
				// parameters.setGpsTimestamp(utcTimeSeconds);
				// }
			} else {
				loc = null;
			}
		}
	}

	private static class ImageFileNamer {
		private SimpleDateFormat mFormat;

		// The date (in milliseconds) used to generate the last name.
		private long mLastDate;

		// Number of names generated for the same second.
		private int mSameSecondCount;

		public ImageFileNamer(String format) {
			mFormat = new SimpleDateFormat(format, Locale.US);
		}

		public String generateName(long dateTaken) {
			Date date = new Date(dateTaken);
			String result = mFormat.format(date);
			int ms = (int) (dateTaken % 1000);
			if (ms < 10)
				result = result + "_00" + ms;
			else if (ms < 100)
				result = result + "_0" + ms;
			else
				result = result + "_" + ms;

			// If the last name was generated for the same second,
			// we append _1, _2, etc to the name.
			// if (dateTaken / 1000 == mLastDate / 1000) {
			// mSameSecondCount++;
			// result += "_" + mSameSecondCount;
			// } else {
			// mLastDate = dateTaken;
			// mSameSecondCount = 0;
			// }

			return result;
		}
	}

	public static int[] getSize(String str) {
		int idx = str.indexOf('x');
		String strW = str.substring(0, idx);
		String strH = str.substring(idx + 1);
		int w = Integer.valueOf(strW);
		int h = Integer.valueOf(strH);
		return new int[] { w, h };
	}

	public static String getTotalMemory() {
		String str1 = "/proc/meminfo";
		String str2 = "";
		String total = "1";
		BufferedReader localBufferedReader = null;
		try {
			FileReader fr = new FileReader(str1);
			localBufferedReader = new BufferedReader(fr, 8192);
			while ((str2 = localBufferedReader.readLine()) != null) {
				if (str2.startsWith("MemTotal")) {
					total = str2.substring(9, str2.length() - 2);
					total = total.trim();
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (localBufferedReader != null)
					localBufferedReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return total;
	}

	public static boolean isVideoCaptureIntent(Intent intent) {
		String action = intent.getAction();
		return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
	}

	public static boolean isImageCaptureIntent(Intent intent) {
		String action = intent.getAction();
		return MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
		/* || ActivityBase.ACTION_IMAGE_CAPTURE_SECURE.equals(action) */;
	}

	public static boolean isVideoCameraIntent(Intent intent) {
		String action = intent.getAction();
		return (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(action));
	}

//	public static boolean isScanIntent(Intent intent) {
//		String action = intent.getAction();
//		return (Util.SCAN_ACTION.equals(action));
//	}
//
	public static void copyFilterFiles(Resources resources, String assetDir,
			String targetDir) {
		String[] files;
		String tempZip = targetDir + "temp.zip";
		try {
			files = resources.getAssets().list(assetDir);
		} catch (IOException e1) {
			return;
		}
		File folder = new File(targetDir);

		if (!folder.exists()) {
			if (!folder.mkdirs()) {
				Log.e("mk", "cannot create directory.");
			}
		}
		String fileName = files[0];
		InputStream in = null;
		try {
			in = resources.getAssets().open(assetDir + "/" + fileName);
			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in));
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				if (ze.isDirectory()) {
					File f = new File(targetDir + ze.getName());
					f.mkdir();
					continue;
				}
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				int count;
				while ((count = zis.read(buffer)) != -1) {
					baos.write(buffer, 0, count);
				}
				String outFileName = targetDir + ze.getName();
				FileOutputStream fout = new FileOutputStream(outFileName);
				byte[] bytes = baos.toByteArray();
				fout.write(bytes);
				fout.close();
			}
			zis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void copyAssets(Resources resources, String assetDir,
			String targetDir) {
		String[] files;
		try {
			files = resources.getAssets().list(assetDir);
			for (String file : files) {
				Log.v("mk", "file = " + file);
			}
		} catch (IOException e1) {
			return;
		}
		File workingPath = new File(targetDir);

		if (!workingPath.exists()) {
			if (!workingPath.mkdirs()) {
				Log.e("mk", "cannot create directory.");
			}
		}

		for (int i = 0; i < files.length; i++) {
			try {
				String fileName = files[i];
				String ppString = assetDir + "/" + fileName;
				long start = System.currentTimeMillis();
				String list[] = resources.getAssets().list(ppString);
				long end = System.currentTimeMillis();
				Log.d("dyb_time", "list file time is " + (end - start) + "ms");
				if (list != null && list.length != 0) {
					if (0 == assetDir.length()) {
						copyAssets(resources, fileName, targetDir + fileName
								+ "/");
					} else {
						copyAssets(resources, assetDir + "/" + fileName,
								targetDir + fileName + "/");
					}
					continue;
				}
				File outFile = new File(workingPath, fileName);
				if (outFile.exists())
					outFile.delete();
				InputStream in = null;
				if (0 != assetDir.length())
					in = resources.getAssets().open(assetDir + "/" + fileName);
				else
					in = resources.getAssets().open(fileName);
				OutputStream out = new FileOutputStream(outFile);
				// Transfer bytes from in to out
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				in.close();
				out.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static String getUuid() {
		Class<?> clazz = null;
		Method method = null;
		String uuid = null;
		try {
			clazz = Class.forName("android.os.SystemProperties");
			method = clazz.getDeclaredMethod("get",
					new Class[] { String.class });
			uuid = (String) method.invoke(null,
					new Object[] { "ro.aliyun.clouduuid" });
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			clazz = null;
			method = null;
		}
		return uuid;
	}

	public static String getYunOSVersion() {
		return System.getProperty("ro.yunos.version");
	}

	public static int dip2px(Context context, float dpValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		Log.d("dyb_scale", " " + scale);
		return (int) (dpValue * scale + 0.5f);
	}

	public static int findBestRatio(int raw, int target) {
		int diff4 = Math.abs(raw / 4 - target);
		int diff8 = Math.abs(raw / 8 - target);
		return diff4 < diff8 ? 4 : 8;
	}

	public static final boolean isOPen(final Context context) {
		android.location.LocationManager locationManager = (android.location.LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);
		boolean gps = locationManager
				.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
		boolean network = locationManager
				.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
		Log.v("mk", "gps = " + gps + " network = " + network);
		if (gps || network) {
			return true;
		}
		return false;
	}

	public static final String getNetworkType(final Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI); // wifi
		NetworkInfo gprs = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_MOBILE); // gprs
		if (wifi != null && wifi.getState() == State.CONNECTED) {
			return "wifi";
		} else if (gprs != null && gprs.getState() == State.CONNECTED) {
			return "mobile";
		}
		return "none";
	}

	public static boolean isNetworkConnected(Context context) {
		if (context != null) {
			ConnectivityManager mConnectivityManager = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo mNetworkInfo = mConnectivityManager
					.getActiveNetworkInfo();
			if (mNetworkInfo != null) {
				return mNetworkInfo.isAvailable();
			}
		}
		return false;
	}

	public static boolean isWifiConnected(Context context) {
		if (context != null) {
			ConnectivityManager mConnectivityManager = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo mWiFiNetworkInfo = mConnectivityManager
					.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if (mWiFiNetworkInfo != null) {
				return mWiFiNetworkInfo.isAvailable();
			}
		}
		return false;
	}

	public static boolean isMobileConnected(Context context) {
		if (context != null) {
			ConnectivityManager mConnectivityManager = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo mMobileNetworkInfo = mConnectivityManager
					.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
			if (mMobileNetworkInfo != null) {
				return mMobileNetworkInfo.isAvailable();
			}
		}
		return false;
	}
}
