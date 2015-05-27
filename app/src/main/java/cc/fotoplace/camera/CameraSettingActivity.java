package cc.fotoplace.camera;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.view.MenuItem;
import cc.fotoplace.camera.platform.PlatformHelper;

public class CameraSettingActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	private static final String SHARED_PREF_NAME = "_preferences_camera";
	private boolean mIsfromvideo = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		if (getIntent() != null && getIntent().getExtras() != null) {
			mIsfromvideo = getIntent().getExtras().getBoolean("fromVideo");
		}
		getPreferenceManager().setSharedPreferencesName(
				getPackageName() + SHARED_PREF_NAME);
		addPreferencesFromResource(R.xml.preferences);
		init();
		setTitle(R.string.settings_header);
		getActionBar().setDisplayHomeAsUpEnabled(true);
//		initActionBar();
//		setTitle2(getString(R.string.settings_header));
//		showBackKey(true);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceManager().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		updateSummary();
	}

	@Override
	protected void onPause() {
		getPreferenceManager().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(getString(R.string.camera_setting_item_loacation_key))) {// 1
			updateLoactionSummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_shutter_sound_key))) {// 3
			updateShutterSoundSummary();
		} else if (key.equals(getString(R.string.camera_setting_item_zsd))) {// 6
			updateZSDSummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_force_autofocus))) {// 7
			updateForceFocusSummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_sdcard_key))) {
			updateDefaultSavePathSummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_preview_size_key))) {// 4
			updatePreviewSizeSummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_beauty_level_key))) {// 9
			updateFaceBeautySummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_volume_key_function_key))) {// 2
			updateVolumeKeySummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_photo_resolution_key))) {// 5
			updatePhotoResolutionSummary();
		} else if (key
				.equals(getString(R.string.camera_setting_item_video_resolution_key))) {// 8
			updateVideoResolutionSummary();
		}
	}

	private void init() {
		initDefaultSavePath();
		initVideoQuality();
	}

	private void updateSummary() {
		updatePreviewSizeSummary();
		updateFaceBeautySummary();
		updateVolumeKeySummary();
		updatePhotoResolutionSummary();
		updateVideoResolutionSummary();
		updateDefaultSavePathSummary();
	}

	private void updateLoactionSummary() {
		String key = getString(R.string.camera_setting_item_loacation_key);
		boolean value = getPreferenceManager().getSharedPreferences()
				.getBoolean(key, false);
	}

	private void updateShutterSoundSummary() {
		String key = getString(R.string.camera_setting_item_shutter_sound_key);
		boolean value = getPreferenceManager().getSharedPreferences()
				.getBoolean(key, false);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			getPreferenceScreen().removePreference((Preference)findPreference(key));
		}
	}

	private void updateZSDSummary() {
		String key = getString(R.string.camera_setting_item_zsd);
		boolean value = getPreferenceManager().getSharedPreferences()
				.getBoolean(key, false);
	}

	private void updateForceFocusSummary() {
		String key = getString(R.string.camera_setting_item_force_autofocus);
		boolean value = getPreferenceManager().getSharedPreferences()
				.getBoolean(key, false);
	}

	private void updatePreviewSizeSummary() {
		String key = getString(R.string.camera_setting_item_preview_size_key);
		Preference pref = findPreference(key);
		String defValue = getResources().getString(
				R.string.camera_setting_item_preview_size_default);
		String[] entryValues = getResources().getStringArray(
				R.array.entryvalues_camera_settings_preview_size);
		String[] entris = getResources().getStringArray(
				R.array.entries_camera_settings_preview_size);
		String current = getPreferenceManager().getSharedPreferences()
				.getString(key, defValue);
		if (/*CameraSettings.mCameraId == CameraInfo.CAMERA_FACING_FRONT
				||*/ mIsfromvideo) {
			pref.setEnabled(false);
			current = entryValues[0];
		}
		for (int i = 0; i < entryValues.length; i++) {
			if (current.equals(entryValues[i])) {
				pref.setSummary(entris[i]);
				break;
			}
		}
	}

	private void updateFaceBeautySummary() {
		String key = getString(R.string.camera_setting_item_beauty_level_key);
		Preference pref = findPreference(key);
		String defValue = getResources().getString(
				R.string.camera_setting_item_beauty_level_default);
		String[] entryValues = getResources().getStringArray(
				R.array.entryvalues_camera_settings_beauty_level);
		String[] entris = getResources().getStringArray(
				R.array.entries_camera_settings_beauty_level);
		String current = getPreferenceManager().getSharedPreferences()
				.getString(key, defValue);
		for (int i = 0; i < entryValues.length; i++) {
			if (current.equals(entryValues[i])) {
				pref.setSummary(entris[i]);
				break;
			}
		}
	}

	private void updateVolumeKeySummary() {
		String key = getString(R.string.camera_setting_item_volume_key_function_key);
		Preference pref = findPreference(key);
		String defValue = getResources().getString(
				R.string.camera_setting_item_volume_key_function_default);
		String[] entryValues = getResources().getStringArray(
				R.array.entryvalues_camera_settings_volumne_key_fuction);
		String[] entris = getResources().getStringArray(
				R.array.entries_camera_settings_volumne_key_fuction);
		String current = getPreferenceManager().getSharedPreferences()
				.getString(key, defValue);
		for (int i = 0; i < entryValues.length; i++) {
			if (current.equals(entryValues[i])) {
				pref.setSummary(entris[i]);
				break;
			}
		}
	}

	private void updateDefaultSavePathSummary() {
		if (!PlatformHelper.supportExternalSDCard())
			return;
		String key = getString(R.string.camera_setting_item_sdcard_key);
		Preference pref = findPreference(key);
		String defValue = getResources().getString(
				R.string.camera_setting_item_sdcard_default);
		String[] entryValues = getResources().getStringArray(
				R.array.entryvalues_camera_settings_sdcard);
		String[] entris = getResources().getStringArray(
				R.array.entries_camera_settings_sdcard);
		String current = getPreferenceManager().getSharedPreferences()
				.getString(key, defValue);
		for (int i = 0; i < entryValues.length; i++) {
			if (current.equals(entryValues[i])) {
				pref.setSummary(entris[i]);
				break;
			}
		}
	}

	private void updatePhotoResolutionSummary() {
		String key = getString(R.string.camera_setting_item_photo_resolution_key);
		Preference pref = findPreference(key);
		String defValue = getResources().getString(
				R.string.camera_setting_item_photo_resolution_default);
		String[] entryValues = getResources().getStringArray(
				R.array.entryvalues_camera_settings_photo_resolution);
		String[] entris = getResources().getStringArray(
				R.array.entries_camera_settings_photo_resolution);
		String current = getPreferenceManager().getSharedPreferences()
				.getString(key, defValue);
		if (mIsfromvideo) {
			pref.setEnabled(false);
			current = defValue;
		}
		for (int i = 0; i < entryValues.length; i++) {
			if (current.equals(entryValues[i])) {
				pref.setSummary(entris[i]);
				break;
			}
		}
	}

	private void updateVideoResolutionSummary() {
		if (!PlatformHelper.supportVideoQuality())
			return;
		String key = getString(R.string.camera_setting_item_video_resolution_key);
		Preference pref = findPreference(key);
		String defValue = getResources().getString(
				R.string.camera_setting_item_video_resolution_default);
		String[] entryValues = getResources().getStringArray(
				R.array.entryvalues_camera_settings_video_resolution);
		String[] entris = getResources().getStringArray(
				R.array.entries_camera_settings_video_resolution);
		String current = getPreferenceManager().getSharedPreferences()
				.getString(key, defValue);
		for (int i = 0; i < entryValues.length; i++) {
			if (current.equals(entryValues[i])) {
				pref.setSummary(entris[i]);
				break;
			}
		}
	}

	private void initDefaultSavePath() {
		if (PlatformHelper.supportExternalSDCard()) {
			String key = getString(R.string.camera_setting_item_sdcard_key);
			Preference pref = findPreference(key);
			String defValue = getResources().getString(
					R.string.camera_setting_item_sdcard_default);
			String[] entryValues = getResources().getStringArray(
					R.array.entryvalues_camera_settings_sdcard);
			String[] entris = getResources().getStringArray(
					R.array.entries_camera_settings_sdcard);
			String current = getPreferenceManager().getSharedPreferences()
					.getString(key, defValue);
			for (int i = 0; i < entryValues.length; i++) {
				if (current.equals(entryValues[i])) {
					pref.setSummary(entris[i]);
					break;
				}
			}
			Storage.init(this);
			if (!PlatformHelper.supportExternalSDCard() ||
			// BugID:99590: allow user select SD Card in Storage Mode.
					(!Storage.isExternalStorageAvailable() && Storage
							.isInternalStorageAvailable())) {
				pref.setEnabled(false);
			}
		} else {
			PreferenceCategory photoCategory = (PreferenceCategory) findPreference(getString(R.string.camera_setting_category_general_key));
			Preference storagePref = findPreference(getString(R.string.camera_setting_item_sdcard_key));
			photoCategory.removePreference(storagePref);
		}
	}

	private void initVideoQuality() {
		if (!PlatformHelper.supportVideoQuality()) {
			PreferenceScreen prefScreen = getPreferenceScreen();
			PreferenceCategory videoCategory = (PreferenceCategory) findPreference(getString(R.string.camera_setting_category_video_key));
			prefScreen.removePreference(videoCategory);
		}
	}

}
