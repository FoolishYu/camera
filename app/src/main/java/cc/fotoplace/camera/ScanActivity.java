package cc.fotoplace.camera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ScanActivity extends Activity {

	public static final String EXTRA_SCAN_PROCESS_RESULT = "extra_scan_prcess_result";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = new Intent("cc.fotoplace.camera.SCAN_QRCODE");
		intent.putExtra(EXTRA_SCAN_PROCESS_RESULT, true);
		startActivityForResult(intent, 0);
		finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
}
