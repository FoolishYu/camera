package cc.fotoplace.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.util.Log;
import cc.fotoplace.camera.platform.PlatformHelper;

/**
 * Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
	private static final int NOT_FOUND = -1;
	public static final String KEY_VERSION = "pref_version_key_alicamera";
	public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
	public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
	public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
	public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL = "pref_video_time_lapse_frame_interval_key";
	public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
	public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
	public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
	public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
	public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
	public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
	public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
	public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
	public static final String KEY_TIMER = "pref_camera_timer_key";
	public static final String KEY_TIMER_SOUND_EFFECTS = "pref_camera_timer_sound_key";
	public static final String KEY_VIDEO_EFFECT = "pref_video_effect_key";
	public static final String KEY_CAMERA_ID = "pref_camera_id_key";    //相机id
	public static final String KEY_CAMERA_HDR = "pref_camera_hdr_key";
	public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN = "pref_camera_first_use_hint_shown_key";
	public static final String KEY_VIDEO_FIRST_USE_HINT_SHOWN = "pref_video_first_use_hint_shown_key";
	public static final String KEY_PHOTOSPHERE_PICTURESIZE = "pref_photosphere_picturesize_key";
	public static final String KEY_SCANCAMERA_FLASH_MODE = "pref_camera_scan_flashmode_key";
	public static final String KEY_FACE_BEAUTY = "pref_camera_face_beauty";
	public static final String KEY_DEFAULT_SAVE_PATH = "pref_save_path";  //存储地址
	public static final String KEY_FIRST_TIME_USE_CAMERA = "pref_first_time_use_camera_key";
	public static final String KEY_SUPPORT_FACE_BEAUTY = "pref_beauty";
	public static final String KEY_PHOTO_ALIGNMENT_LINES = "pref_photo_alignment_lines_key";
	public static final String EXPOSURE_DEFAULT_VALUE = "0";
	public static final String KEY_CAP_MODE = "cap-mode";
	public static final String CAP_MODE_FACEBEUATY = "face_beauty";
	public static final String CAP_MODE_NORMAL = "normal";
	public static final int CURRENT_VERSION = 5;
	public static final int CURRENT_LOCAL_VERSION = 2;

	private static final String TAG = "CameraSettings";

	private final Context mContext;
	private final Parameters mParameters;
	private final CameraInfo[] mCameraInfo;
	public static int mCameraId;

	public CameraSettings(Activity activity, Parameters parameters,
			int cameraId, CameraInfo[] cameraInfo) {
		mContext = activity;
		mParameters = parameters;
		mCameraId = cameraId;
		mCameraInfo = cameraInfo;
	}

	public PreferenceGroup getPreferenceGroup(int preferenceRes) {
		PreferenceInflater inflater = new PreferenceInflater(mContext);
		PreferenceGroup group = (PreferenceGroup) inflater
				.inflate(preferenceRes);
		if (mParameters != null)
			initPreference(group);
		return group;
	}

	@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
	public static String getDefaultVideoQuality(int cameraId,
			String defaultQuality) {
		if (PlatformHelper.CamcorderProfile_hasProfile(cameraId,
				Integer.valueOf(defaultQuality))) {
			return defaultQuality;
		}
		return Integer.toString(CamcorderProfile.QUALITY_HIGH);
	}

	public static void initialCameraPictureSize(Context context,
			Parameters parameters) {
		// When launching the camera app first time, we will set the picture
		// size to the first one in the list defined in "arrays.xml" and is also
		// supported by the driver.
		List<Size> supported = parameters.getSupportedPictureSizes();
		if (supported == null)
			return;
		for (String candidate : context.getResources().getStringArray(
				R.array.pref_camera_picturesize_entryvalues)) {
			if (setCameraPictureSize(candidate, supported, parameters)) {
				SharedPreferences.Editor editor = ComboPreferences.get(context)
						.edit();
				editor.putString(KEY_PICTURE_SIZE, candidate);
				editor.apply();
				return;
			}
		}
		Log.e(TAG, "No supported picture size found");
	}

	public static void removePreferenceFromScreen(PreferenceGroup group,
			String key) {
		removePreference(group, key);
	}

	public static boolean setCameraPictureSize(String candidate,
			List<Size> supported, Parameters parameters) {
		int index = candidate.indexOf('x');
		if (index == NOT_FOUND)
			return false;
		int width = Integer.parseInt(candidate.substring(0, index));
		int height = Integer.parseInt(candidate.substring(index + 1));
		for (Size size : supported) {
			if (size.width == width && size.height == height) {
				parameters.setPictureSize(width, height);
				return true;
			}
		}
		return false;
	}

	public static int getMaxVideoDuration(Context context) {
		int duration = 0; // in milliseconds, 0 means unlimited.
		try {
			duration = context.getResources().getInteger(
					R.integer.max_video_recording_length);
		} catch (Resources.NotFoundException ex) {
		}
		return duration;
	}

	private void initPreference(PreferenceGroup group) {
		ListPreference videoQuality = group.findPreference(KEY_VIDEO_QUALITY);
		ListPreference timeLapseInterval = group
				.findPreference(KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
		ListPreference pictureSize = group.findPreference(KEY_PICTURE_SIZE);
		ListPreference whiteBalance = group.findPreference(KEY_WHITE_BALANCE);
		ListPreference sceneMode = group.findPreference(KEY_SCENE_MODE);
		ListPreference flashMode = group.findPreference(KEY_FLASH_MODE);
		ListPreference focusMode = group.findPreference(KEY_FOCUS_MODE);
		// IconListPreference exposure =
		ListPreference exposure = group.findPreference(KEY_EXPOSURE);
		// CountDownTimerPreference timer =
		// ListPreference timer = group.findPreference(KEY_TIMER);
		// ListPreference countDownSoundEffects =
		// group.findPreference(KEY_TIMER_SOUND_EFFECTS);
		// IconListPreference cameraIdPref =
		ListPreference cameraIdPref = group.findPreference(KEY_CAMERA_ID);
		ListPreference videoFlashMode = group
				.findPreference(KEY_VIDEOCAMERA_FLASH_MODE);
		// ListPreference videoEffect = group.findPreference(KEY_VIDEO_EFFECT);
		ListPreference cameraHdr = group.findPreference(KEY_CAMERA_HDR);

		// Since the screen could be loaded from different resources, we need
		// to check if the preference is available here
		if (videoQuality != null) {
			filterUnsupportedOptions(group, videoQuality,
					getSupportedVideoQuality());
		}

		if (pictureSize != null) {
			filterUnsupportedOptions(
					group,
					pictureSize,
					sizeListToStringList(mParameters.getSupportedPictureSizes()));
			filterSimilarPictureSize(group, pictureSize);
		}
		if (whiteBalance != null) {
			filterUnsupportedOptions(group, whiteBalance,
					mParameters.getSupportedWhiteBalance());
		}
		if (sceneMode != null) {
			filterUnsupportedOptions(group, sceneMode,
					mParameters.getSupportedSceneModes());
		}
		if (flashMode != null) {
			filterUnsupportedOptions(group, flashMode,
					mParameters.getSupportedFlashModes());
		}
		if (focusMode != null) {
			if (!Util.isFocusAreaSupported(mParameters)) {
				filterUnsupportedOptions(group, focusMode,
						mParameters.getSupportedFocusModes());
			} else {
				// Remove the focus mode if we can use tap-to-focus.
				removePreference(group, focusMode.getKey());
			}
		}
		if (videoFlashMode != null) {
			filterUnsupportedOptions(group, videoFlashMode,
					mParameters.getSupportedFlashModes());
		}
		if (exposure != null)
			buildExposureCompensation(group, exposure);
		if (cameraIdPref != null)
			buildCameraId(group, cameraIdPref);

		if (timeLapseInterval != null) {
			if (ApiHelper.HAS_TIME_LAPSE_RECORDING) {
				resetIfInvalid(timeLapseInterval);
			} else {
				removePreference(group, timeLapseInterval.getKey());
			}
		}
		if (cameraHdr != null
				&& (!ApiHelper.HAS_CAMERA_HDR || !Util
						.isCameraHdrSupported(mParameters))) {
			removePreference(group, cameraHdr.getKey());
		}
	}

	private void buildExposureCompensation(PreferenceGroup group, /* IconListPreference */
			ListPreference exposure) {
		int max = mParameters.getMaxExposureCompensation();
		int min = mParameters.getMinExposureCompensation();
		if (max == 0 && min == 0) {
			removePreference(group, exposure.getKey());
			return;
		}
		float step = mParameters.getExposureCompensationStep();

		// show only integer values for exposure compensation
		int maxValue = Math.min(3, (int) Math.floor(max * step));
		int minValue = Math.max(-3, (int) Math.ceil(min * step));
		String explabel = mContext.getResources().getString(
				R.string.pref_exposure_label);
		CharSequence entries[] = new CharSequence[maxValue - minValue + 1];
		CharSequence entryValues[] = new CharSequence[maxValue - minValue + 1];
		CharSequence labels[] = new CharSequence[maxValue - minValue + 1];
		// int[] icons = new int[maxValue - minValue + 1];
		// TypedArray iconIds = mContext.getResources().obtainTypedArray(
		// R.array.pref_camera_exposure_icons);
		for (int i = minValue; i <= maxValue; ++i) {
			entryValues[i - minValue] = Integer.toString(Math.round(i / step));
			StringBuilder builder = new StringBuilder();
			if (i > 0)
				builder.append('+');
			entries[i - minValue] = builder.append(i).toString();
			labels[i - minValue] = explabel + " " + builder.toString();
			// icons[i - minValue] = iconIds.getResourceId(3 + i, 0);
		}
		// exposure.setUseSingleIcon(true);
		exposure.setEntries(entries);
		exposure.setLabels(labels);
		exposure.setEntryValues(entryValues);
		// exposure.setLargeIconIds(icons);
	}

	private void buildCameraId(PreferenceGroup group, /* IconListPreference */
			ListPreference preference) {
		int numOfCameras = mCameraInfo.length;
		if (numOfCameras < 2) {
			removePreference(group, preference.getKey());
			return;
		}
		CharSequence[] entryValues = new CharSequence[numOfCameras];
		for (int i = 0; i < numOfCameras; ++i) {
			entryValues[i] = "" + i;
		}
		preference.setEntryValues(entryValues);
	}

	private static boolean removePreference(PreferenceGroup group, String key) {
		for (int i = 0, n = group.size(); i < n; i++) {
			CameraPreference child = group.get(i);
			if (child instanceof PreferenceGroup) {
				if (removePreference((PreferenceGroup) child, key)) {
					return true;
				}
			}
			if (child instanceof ListPreference
					&& ((ListPreference) child).getKey().equals(key)) {
				group.removePreference(i);
				return true;
			}
		}
		return false;
	}

	private void filterUnsupportedOptions(PreferenceGroup group,
			ListPreference pref, List<String> supported) {
		// Remove the preference if the parameter is not supported or there is
		// only one options for the settings.
		if (supported == null || supported.size() <= 1) {
			removePreference(group, pref.getKey());
			return;
		}
		pref.filterUnsupported(supported);
		if (pref.getEntries().length <= 1) {
			removePreference(group, pref.getKey());
			return;
		}

		resetIfInvalid(pref);
	}

	private void filterSimilarPictureSize(PreferenceGroup group,
			ListPreference pref) {
		pref.filterDuplicated();
		if (pref.getEntries().length <= 1) {
			removePreference(group, pref.getKey());
			return;
		}
		resetIfInvalid(pref);
	}

	private void resetIfInvalid(ListPreference pref) {
		// Set the value to the first entry if it is invalid.
		String value = pref.getValue();
		if (pref.findIndexOfValue(value) == NOT_FOUND) {
			pref.setValueIndex(0);
		}
	}

	private static List<String> sizeListToStringList(List<Size> sizes) {
		ArrayList<String> list = new ArrayList<String>();
		for (Size size : sizes) {
			list.add(String.format(Locale.ENGLISH, "%dx%d", size.width,
					size.height));
		}
		return list;
	}

	public static void upgradeLocalPreferences(SharedPreferences pref) {
		int version;
		try {
			version = pref.getInt(KEY_LOCAL_VERSION, 0);
		} catch (Exception ex) {
			version = 0;
		}
		if (version == CURRENT_LOCAL_VERSION)
			return;

		SharedPreferences.Editor editor = pref.edit();
		if (version == 1) {
			// We use numbers to represent the quality now. The quality
			// definition is identical to
			// that of CamcorderProfile.java.
			editor.remove("pref_video_quality_key");
		}
		editor.putInt(KEY_LOCAL_VERSION, CURRENT_LOCAL_VERSION);
		editor.apply();
	}

	public static void upgradeGlobalPreferences(SharedPreferences pref) {
		upgradeOldVersion(pref);
		upgradeCameraId(pref);
	}

	private static void upgradeOldVersion(SharedPreferences pref) {
		int version;
		try {
			version = pref.getInt(KEY_VERSION, 0);
		} catch (Exception ex) {
			version = 0;
		}
		if (version == CURRENT_VERSION)
			return;

		SharedPreferences.Editor editor = pref.edit();
		if (version == 0) {
			// We won't use the preference which change in version 1.
			// So, just upgrade to version 1 directly
			version = 1;
		}
		if (version == 1) {
			// Change jpeg quality {65,75,85} to {normal,fine,superfine}
			String quality = pref.getString(KEY_JPEG_QUALITY, "85");
			if (quality.equals("65")) {
				quality = "normal";
			} else if (quality.equals("75")) {
				quality = "fine";
			} else {
				quality = "superfine";
			}
			editor.putString(KEY_JPEG_QUALITY, quality);
			version = 2;
		}
		if (version == 2) {
			// Compatible with old version shared preference.
			// Change KEY_RECORD_LOCATION_TYPE from boolean to String.
			// editor.putString(KEY_RECORD_LOCATION,
			// pref.getBoolean(KEY_RECORD_LOCATION, false)
			// ? RecordLocationPreference.VALUE_ON
			// : RecordLocationPreference.VALUE_NONE);
			version = 3;
		}
		if (version == 3) {
			// Just use video quality to replace it and
			// ignore the current settings.
			editor.remove("pref_camera_videoquality_key");
			editor.remove("pref_camera_video_duration_key");
		}

		editor.putInt(KEY_VERSION, CURRENT_VERSION);
		editor.apply();
	}

	private static void upgradeCameraId(SharedPreferences pref) {
		// The id stored in the preference may be out of range if we are running
		// inside the emulator and a webcam is removed.
		// Note: This method accesses the global preferences directly, not the
		// combo preferences.
		int cameraId = readPreferredCameraId(pref);
		if (cameraId == 0)
			return; // fast path

		int n = CameraHolder.instance().getNumberOfCameras();
		if (cameraId < 0 || cameraId >= n) {
			writePreferredCameraId(pref, 0);
		}
	}

	public static int readPreferredCameraId(SharedPreferences pref) {
		return Integer.parseInt(pref.getString(KEY_CAMERA_ID, "0"));
	}

	public static String readFlashMode(SharedPreferences pref, int moduleIndex) {
		String flashMode = null;
		switch (moduleIndex) {
		case CameraActivity.PHOTO_MODULE_INDEX:
			flashMode = pref.getString(CameraSettings.KEY_FLASH_MODE,
					Parameters.FLASH_MODE_AUTO);
			break;
		case CameraActivity.VIDEO_MODULE_INDEX:
			flashMode = pref.getString(
					CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
					Parameters.FLASH_MODE_OFF);
			break;
		case CameraActivity.SCANNER_MODULE_INDEX:
			flashMode = pref.getString(
					CameraSettings.KEY_SCANCAMERA_FLASH_MODE,
					Parameters.FLASH_MODE_OFF);
			break;
		case CameraActivity.PANORAMA_MODULE_INDEX:
			flashMode = Parameters.FLASH_MODE_OFF;
		default:
			break;
		}
		return flashMode;
	}

	public static void writePreferredCameraId(SharedPreferences pref,
			int cameraId) {
		Editor editor = pref.edit();
		editor.putString(KEY_CAMERA_ID, Integer.toString(cameraId));
		editor.apply();
	}

	public static int readExposure(ComboPreferences preferences) {
		String exposure = preferences.getString(CameraSettings.KEY_EXPOSURE,
				EXPOSURE_DEFAULT_VALUE);
		try {
			return Integer.parseInt(exposure);
		} catch (Exception ex) {
			Log.e(TAG, "Invalid exposure: " + exposure);
		}
		return 0;
	}

	public static void restorePreferences(Context context,
			ComboPreferences preferences, Parameters parameters) {
		int currentCameraId = readPreferredCameraId(preferences);

		// Clear the preferences of both cameras.
		int backCameraId = CameraHolder.instance().getBackCameraId();
		if (backCameraId != -1) {
			preferences.setLocalId(context, backCameraId);
			Editor editor = preferences.edit();
			editor.clear();
			editor.apply();
		}
		int frontCameraId = CameraHolder.instance().getFrontCameraId();
		if (frontCameraId != -1) {
			preferences.setLocalId(context, frontCameraId);
			Editor editor = preferences.edit();
			editor.clear();
			editor.apply();
		}

		// Switch back to the preferences of the current camera. Otherwise,
		// we may write the preference to wrong camera later.
		preferences.setLocalId(context, currentCameraId);

		upgradeGlobalPreferences(preferences.getGlobal());
		upgradeLocalPreferences(preferences.getLocal());

		// Write back the current camera id because parameters are related to
		// the camera. Otherwise, we may switch to the front camera but the
		// initial picture size is that of the back camera.
		initialCameraPictureSize(context, parameters);
		writePreferredCameraId(preferences, currentCameraId);
	}

	private ArrayList<String> getSupportedVideoQuality() {
		ArrayList<String> supported = new ArrayList<String>();
		// Check for supported quality
		if (ApiHelper.HAS_FINE_RESOLUTION_QUALITY_LEVELS) {
			getFineResolutionQuality(supported);
		} else {
			supported.add(Integer.toString(CamcorderProfile.QUALITY_HIGH));
			CamcorderProfile high = CamcorderProfile.get(mCameraId,
					CamcorderProfile.QUALITY_HIGH);
			CamcorderProfile low = CamcorderProfile.get(mCameraId,
					CamcorderProfile.QUALITY_LOW);
			if (high.videoFrameHeight * high.videoFrameWidth > low.videoFrameHeight
					* low.videoFrameWidth) {
				supported.add(Integer.toString(CamcorderProfile.QUALITY_LOW));
			}
		}

		return supported;
	}

	@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
	private void getFineResolutionQuality(ArrayList<String> supported) {
		if (CamcorderProfile.hasProfile(mCameraId,
				CamcorderProfile.QUALITY_1080P)) {
			supported.add(Integer.toString(CamcorderProfile.QUALITY_1080P));
		}
		if (CamcorderProfile.hasProfile(mCameraId,
				CamcorderProfile.QUALITY_720P)) {
			supported.add(Integer.toString(CamcorderProfile.QUALITY_720P));
		}
		if (CamcorderProfile.hasProfile(mCameraId,
				CamcorderProfile.QUALITY_480P)) {
			supported.add(Integer.toString(CamcorderProfile.QUALITY_480P));
		}
	}

	public static Size getPicSize(List<Size> supported, boolean is4_3,
			boolean isEffect, int index) {
		int resLow4_3 = 1600 * 1200;
		int resLow16_9 = 1600 * 960;
		int resMedium4_3 = 2048 * 1536;
		int resMedium16_9 = 2560 * 1440;
		int retIndex = 0;
		if (is4_3) { // 4 : 3
			if (index == 2 || isEffect) { // low
				retIndex = getClosest(supported, resLow4_3, 4 / 3f);
			} else if (index == 1) { // medium
				retIndex = getClosest(supported, resMedium4_3, 4 / 3f);
			} else { // max
				retIndex = getMax(supported, 4 / 3f);
			}
		} else { // 16 : 9
			if (index == 2 || isEffect) { // low
				retIndex = getClosest(supported, resLow16_9, 16 / 9f);
			} else if (index == 1) { // medium
				retIndex = getClosest(supported, resMedium16_9, 16 / 9f);
			} else { // max
				retIndex = getMax(supported, 16 / 9f);
			}
		}

		Log.d("dyb", "picture size is (" + supported.get(retIndex).width + ","
				+ supported.get(retIndex).height + ")");
		return supported.get(retIndex);
	}

	private static int getClosest(List<Size> supported, int target,
			float targetRatio) {
		int minDiff = target;
		int retInt = 0;
		for (int i = 0; i < supported.size(); i++) {
			int diff = Math.abs(supported.get(i).width
					* supported.get(i).height - target);
			float ratio = supported.get(i).width
					/ (float) supported.get(i).height;
			if (diff < minDiff && Math.abs(ratio - targetRatio) < 0.2f) {
				minDiff = diff;
				retInt = i;
			}
		}
		return retInt;
	}

	private static int getMax(List<Size> supported, float targetRatio) {
		int retInt = 0;
		int maxRes = 0;
		for (int i = 0; i < supported.size(); i++) {
			int res = supported.get(i).width * supported.get(i).height;
			float ratio = supported.get(i).width
					/ (float) supported.get(i).height;
			if (res > maxRes && Math.abs(ratio - targetRatio) < 0.2f) {
				maxRes = res;
				retInt = i;
			}
		}
		return retInt;
	}

	public static boolean getFullScreenPreference(Activity mActivity,
			ComboPreferences mPreferences) {
		String key = mActivity.getResources().getString(
				R.string.camera_setting_item_preview_size_key);
		String defValue = mActivity.getResources().getString(
				R.string.camera_setting_item_preview_size_default);
		String[] entryValues = mActivity.getResources().getStringArray(
				R.array.entryvalues_camera_settings_preview_size);
		String current = mPreferences.getString(key, defValue);
		if (current.equals(entryValues[1])) {
			return false;
		} else {
			return true;
		}
	}

}
