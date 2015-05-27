package cc.fotoplace.camera;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import cc.fotoplace.camera.filters.FilterData;
import cc.fotoplace.camera.filters.FiltersManager;
import cc.fotoplace.camera.filters.FiltersUtil;


public abstract class ActivityBase extends Activity {

	protected boolean mSecureCamera;
	private static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE = "android.media.action.STILL_IMAGE_CAMERA_SECURE";//第三方调用相机
	public static final String ACTION_IMAGE_CAPTURE_SECURE = "android.media.action.IMAGE_CAPTURE_SECURE";
	public static final String SECURE_CAMERA_EXTRA = "secure_camera";

	private static final String SYSTEM_IMAGE_APP_PACKAGE_NAME = "cc.fotoplace.image";
	private static final String SYSTEM_VIDEO_APP_PACKAGE_NAME = "cc.fotoplace.video";
	private static final String SYSTEM_IMAGE_APP_ACTIVITY_NAME = "cc.fotoplace.image.app.Gallery";
	private static final String SYSTEM_VIDEO_APP_ACTIVITY_NAME = "cc.fotoplace.video.VideoCenterActivity";

	private static final String SECURE_ALBUM_KEY = "media-set-path";
	private static final String SECURE_ALBUM_PREFIX = "/local/user/{";
	private static final String SECURE_ALBUM_IMAGE = "/local/image/item/";
	private static final String SECURE_ALBUM_VIDEO = "/local/video/item/";
	private static final String SECURE_ALBUM_SEPARATER = ",";
	private static final String SECURE_ALBUM_SUFFIX = "}";
	private StringBuilder mSecureAlbumBuilder = null;

	private static final int MSG_SHOW_RECORD_LOCATION_DIALOG = 1;
	private static final int MSG_SHOW_LOCATION_SETTING_DIALOG = 2;
	// This variable is used for lock screen camera.
	protected static int sSecureAlbumId;
	private static boolean sFirstStartAfterScreenOn = true;
	private ComboPreferences mPreferences;
	private AlertDialog mLocationAlertDialog = null;
	public List<FilterData> filterData;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		Intent it = getIntent();
		Log.d("mk", "action = " + it.getAction());
		Log.d("mk", "component = " + it.getComponent());
		Log.d("mk", "package = " + it.getPackage());
		Log.d("mk", "type = " + it.getType());
		Log.d("mk", "data = " + it.getData());
		Bundle b = it.getExtras();
		if (b != null)
			for (String key : b.keySet())
				if (b.get(key) != null)
					Log.d("mk", "extra, key=" + key + ", value="
							+ b.get(key).toString());
		// Check if this is in the secure camera mode.
		Intent intent = getIntent();
		String action = intent.getAction();
		if (INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)) {
			mSecureCamera = true;
			// Use a new album when this is started from the lock screen.
			sSecureAlbumId++;
		} else if (ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
			mSecureCamera = true;
		} else {
			mSecureCamera = intent.getBooleanExtra(SECURE_CAMERA_EXTRA, false);
		}
		if (mSecureCamera) {
			IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
			registerReceiver(mScreenOffReceiver, filter);
			getApplicationContext().registerReceiver(getScreenOffReceiver(),
					filter);
		}
		mPreferences = new ComboPreferences(this);
		mPreferences.setLocalId(this, 0);
		checkAppVersion();
		// Only run when user first time use camera
		//if (!("com.yunos.camera.SCAN_QRCODE".equals(action)))
			runCameraFirstTime();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mSecureCamera) {
			unregisterReceiver(mScreenOffReceiver);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mHandler.removeMessages(UPDATE_STORAGE_HINT);
		uninstallIntentFilter();
	}

	@Override
	protected void onRestart() {
		super.onRestart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		installIntentFilter();
		updateDefaultSavePath();
		if (updateStorageHintOnResume()) {
			updateStorageSpace();
			mHandler.sendEmptyMessageDelayed(UPDATE_STORAGE_HINT, 200);
		}
	}

	// This method only invoke at the first time user start camera
	private void runCameraFirstTime() {
		if (mPreferences.getBoolean(CameraSettings.KEY_FIRST_TIME_USE_CAMERA,
				true)) {
			// Location Setting
			popUpLocationSettingDialog();
			// Initialize Database
			initilizeFiltersDatabase();
		}
		FiltersUtil.initialize(this);
	}

	private void initilizeFiltersDatabase() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Copy pre-defined filters to /data/data/{package}/files
				Util.copyFilterFiles(getResources(),
						FiltersManager.ASSET_FILTERS_DIRECTORY, getFilesDir()
								+ FiltersManager.REAL_FILTERS_DIRECTORY);
				List<FilterData> filterList = FiltersManager
						.getFiltersDataFromXML(getResources(), getFilesDir()
								+ FiltersManager.REAL_FILTERS_DIRECTORY);
				FiltersManager.insertFilterToDB(getContentResolver(),
						filterList);
			}
		}).start();
	}

	private void popUpLocationSettingDialog() {
		if (mLocationAlertDialog != null) {
			mLocationAlertDialog.dismiss();
			mLocationAlertDialog = null;
		}
		mHandler.sendEmptyMessage(MSG_SHOW_RECORD_LOCATION_DIALOG);
	}

	private void updateDefaultSavePath() {
		Storage.init(this);
		String key = getString(R.string.camera_setting_item_sdcard_key);
		String defValue = getString(R.string.camera_setting_item_sdcard_default);
		String[] entryValues = getResources().getStringArray(
				R.array.entryvalues_camera_settings_sdcard);
		String value = mPreferences.getString(key, defValue);
		boolean external = value.equals(entryValues[0]);
		if (external) {
			Storage.updateMountPoint(Storage.EXTERNAL_SDCARD_PATH);
			if (!Storage.isExternalStorageAvailable()) {
				// BugID:99590: allow user select SD Card in Storage Mode,
				// so we don't force user to change the Storage location.
				if (Storage.isInternalStorageAvailable()) {
					mPreferences
							.edit()
							.putString(CameraSettings.KEY_DEFAULT_SAVE_PATH,
									entryValues[1]).apply();
					Storage.updateMountPoint(Storage.INTERNAL_SDCARD_PATH);
				}
			}
		} else {
			Storage.updateMountPoint(Storage.INTERNAL_SDCARD_PATH);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_MEDIA_MOUNTED)
					|| action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
					|| action.equals(Intent.ACTION_MEDIA_CHECKING)
					|| action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
				updateStorageSpaceAndHint();
			}
		}
	};

	// close activity when screen turns off
	private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			finish();
		}
	};

	private static BroadcastReceiver sScreenOffReceiver = null;

	private synchronized static BroadcastReceiver getScreenOffReceiver() {
		if (sScreenOffReceiver == null) {
			sScreenOffReceiver = new ScreenOffReceiver();
		}

		return sScreenOffReceiver;
	}

	private static class ScreenOffReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			sFirstStartAfterScreenOn = true;
		}
	}
    //注册一个sd卡内存监听
	protected void installIntentFilter() {
		// install an intent filter to receive SD card related events. 
		IntentFilter intentFilter = new IntentFilter(
				Intent.ACTION_MEDIA_MOUNTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
		intentFilter.addDataScheme("file");
		registerReceiver(mReceiver, intentFilter);
	}

	protected void uninstallIntentFilter() {
		unregisterReceiver(mReceiver);
	}

	private void checkAppVersion() {
		int oldVersion = mPreferences.getInt(CameraSettings.KEY_VERSION, 0);
		PackageInfo pinfo;
		try {
			pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			int currentVersion = pinfo.versionCode;
			if (currentVersion > oldVersion) {
				mPreferences.edit().clear().apply();
				mPreferences.edit()
						.putInt(CameraSettings.KEY_VERSION, currentVersion)
						.apply();
				mPreferences.edit().commit();
			}
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block 260
			e.printStackTrace();
		}
	}

	// Storage Related
	private long mStorageSpace = Storage.LOW_STORAGE_THRESHOLD;
	private static final int UPDATE_STORAGE_HINT = 0;
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			AlertDialog.Builder builder = null;
			switch (msg.what) {
			case UPDATE_STORAGE_HINT:
				updateStorageHint();
				return;
			case MSG_SHOW_LOCATION_SETTING_DIALOG:
				builder = new AlertDialog.Builder(ActivityBase.this);
				builder.setPositiveButton(getString(R.string.dialog_set),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								// open the set geo - tagging feature
								Intent intent = new Intent(
										"android.settings.LOCATION_SOURCE_SETTINGS");
								startActivity(intent);
							}
						});
				builder.setNegativeButton(getString(R.string.dialog_ignore),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								// do nothing, just close dialog
							}
						});
				//builder.setTitle(R.string.dialog_location_service_hint);
				builder.setMessage(R.string.dialog_location_service_hint);
				builder.setCancelable(false);
				mLocationAlertDialog = builder.create();
				mLocationAlertDialog.show();
				return;
			case MSG_SHOW_RECORD_LOCATION_DIALOG:
				builder = new AlertDialog.Builder(ActivityBase.this);
				builder.setPositiveButton(getString(R.string.dialog_allow),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								Editor editor = mPreferences.edit();
								editor.putBoolean(
										CameraSettings.KEY_RECORD_LOCATION,
										true);
								editor.putBoolean(
										CameraSettings.KEY_FIRST_TIME_USE_CAMERA,
										false);
								editor.apply();
								if (!Util.isOPen(ActivityBase.this)) {
									mHandler.sendEmptyMessage(MSG_SHOW_LOCATION_SETTING_DIALOG);
								}
							}
						});
				builder.setNegativeButton(
						getString(R.string.dialog_dont_allow),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								Editor editor = mPreferences.edit();
								editor.putBoolean(
										CameraSettings.KEY_RECORD_LOCATION,
										false);
								editor.putBoolean(
										CameraSettings.KEY_FIRST_TIME_USE_CAMERA,
										false);
								editor.apply();
							}
						});
				//builder.setTitle(R.string.dialog_record_location_hint);
				builder.setMessage(R.string.dialog_record_location_hint);
				builder.setCancelable(false);
				mLocationAlertDialog = builder.create();
				mLocationAlertDialog.show();
				return;
			}
		}
	};

	protected void updateStorageSpace() {
		mStorageSpace = Storage.getAvailableSpace();
	}

	protected long getStorageSpace() {
		return mStorageSpace;
	}

	protected void updateStorageSpaceAndHint() {
		updateStorageSpace();
		updateStorageHint(mStorageSpace);
	}

	protected void updateStorageHint() {
		updateStorageHint(mStorageSpace);
	}

	protected boolean updateStorageHintOnResume() {
		return true;
	}

	public boolean isSecureCamera() {
		return mSecureCamera;
	}

	private OnScreenHint mStorageHint;//onScreenHint,一个比toast更加灵活的提示类 

	protected void updateStorageHint(long storageSpace) {
		String message = null;
		if (storageSpace == Storage.UNAVAILABLE) {
			message = getString(R.string.no_storage);
		} else if (storageSpace == Storage.PREPARING) {
			message = getString(R.string.preparing_sd);
		} else if (storageSpace == Storage.UNKNOWN_SIZE) {
			message = getString(R.string.access_sd_fail);
		} else if (storageSpace <= Storage.LOW_STORAGE_THRESHOLD) {
			message = getString(R.string.spaceIsLow_content);
		}
		if (message != null) {
			if (mStorageHint == null) {
				mStorageHint = OnScreenHint.makeText(this, message);
			} else {
				mStorageHint.setText(message);
			}
			mStorageHint.show();
		} else if (mStorageHint != null) {
			mStorageHint.cancel();
			mStorageHint = null;
		}
	}

	public static boolean isFirstStartAfterScreenOn() {
		return sFirstStartAfterScreenOn;
	}

	public static void resetFirstStartAfterScreenOn() {
		sFirstStartAfterScreenOn = false;
	}

	// Go To AliImage, cherry-pick code now, change in future.
	private static final UriMatcher URI_MATCHER = new UriMatcher(
			UriMatcher.NO_MATCH);
	private static final int URI_MATCHER_VIDEO_ID = 1;
	private static final int URI_MATCHER_IMAGE_ID = 2;

	static {
		URI_MATCHER.addURI(MediaStore.AUTHORITY, "external/images/media/#",
				URI_MATCHER_IMAGE_ID);
		URI_MATCHER.addURI(MediaStore.AUTHORITY, "external/video/media/#",
				URI_MATCHER_VIDEO_ID);
	}

	public void gotoImage(Thumbnail thumbnail) {
		if (thumbnail == null) {
			return;
		}
		Uri uri = thumbnail.getUri();
		if (uri == null) {
			Intent intent = new Intent();
			intent.setComponent(new ComponentName(
					SYSTEM_IMAGE_APP_PACKAGE_NAME,
					SYSTEM_IMAGE_APP_ACTIVITY_NAME));
			try {
				startActivity(intent);
			} catch (ActivityNotFoundException e) {
				// ignore no AliImage Scenario
			}
		} else {
			goToGallery(uri);
		}
	}

	public void gotoVideo(Thumbnail thumbnail) {
		if (thumbnail == null) {
			return;
		}
		Uri uri = thumbnail.getUri();
		if (uri == null) {
			Intent intent = new Intent();
			intent.setComponent(new ComponentName(
					SYSTEM_VIDEO_APP_PACKAGE_NAME,
					SYSTEM_VIDEO_APP_ACTIVITY_NAME));
			try {
				startActivity(intent);
			} catch (Exception e) {
				// ignore no AliVideo Scenario
			}
		} else {
			goToGallery(uri);
		}
	}

	private void goToGallery(Uri uri) {
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		int result = URI_MATCHER.match(uri);
		switch (result) {
		case URI_MATCHER_VIDEO_ID:
			intent.setPackage(SYSTEM_VIDEO_APP_PACKAGE_NAME);
			intent.setDataAndType(uri, "video/*");
			break;
		case URI_MATCHER_IMAGE_ID:
			intent.setPackage(SYSTEM_IMAGE_APP_PACKAGE_NAME);
			intent.setDataAndType(uri, "image/*");
			if (mSecureCamera) {
				intent.putExtra(SECURE_ALBUM_KEY, getSecureAlbumString());
			}
			break;
		default:
			break;
		}
		if (mSecureCamera) {
			intent.putExtra(SECURE_CAMERA_EXTRA, true);
		}
		try {
			startActivity(intent);
			return;
		} catch (ActivityNotFoundException e) {
			// ignore the scenario no AliImage or AliVideo
		}
		intent.setPackage(null);
		intent.setData(uri);
		startActivity(intent);
	}

	@Override
	public void onAttachedToWindow() {
		if (mSecureCamera) {
			getWindow().addFlags(
					WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}
		super.onAttachedToWindow();
	}

	@Override
	public void onDetachedFromWindow() {
		if (mSecureCamera) {
			getWindow().clearFlags(
					WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}
		super.onDetachedFromWindow();
	}

	protected void addSecureAlbumItemIfNeeded(boolean isVideo, Uri uri) {
		if (mSecureCamera) {
			String uriString = uri.toString();
			int index = uriString.lastIndexOf("/");
			String id = uriString.substring(index + 1, uriString.length());
			if (mSecureAlbumBuilder == null) {
				mSecureAlbumBuilder = new StringBuilder();
			}
			if (mSecureAlbumBuilder.length() != 0) {
				mSecureAlbumBuilder.insert(0, SECURE_ALBUM_SEPARATER);
			}
			if (isVideo) {
				mSecureAlbumBuilder.insert(0, id);
				mSecureAlbumBuilder.insert(0, SECURE_ALBUM_VIDEO);
				mSecureVideoTaken = true;
			} else {
				mSecureAlbumBuilder.insert(0, id);
				mSecureAlbumBuilder.insert(0, SECURE_ALBUM_IMAGE);
				mSecurePhotoTaken = true;
			}
		}
	}

	private String getSecureAlbumString() {
		if (mSecureCamera) {
			StringBuilder sb = new StringBuilder(mSecureAlbumBuilder);
			sb.insert(0, SECURE_ALBUM_PREFIX);
			sb.append(SECURE_ALBUM_SUFFIX);
			return sb.toString();
		}
		return null;
	}

	private boolean mSecurePhotoTaken = false;
	private boolean mSecureVideoTaken = false;

	protected boolean isSecurePhotoTaken() {
		return mSecurePhotoTaken;
	}

	protected boolean isSecureVideoTaken() {
		return mSecureVideoTaken;
	}

	class DialogHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {

			default:
				break;
			}
		}
	}
}
