package cc.fotoplace.camera.network;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.filters.FilterData;
import cc.fotoplace.camera.filters.FiltersManager;

public class FilterStoreActivity extends Activity implements
		FilterDownloadService.Listener {

	private static final String TAG = "dyb_filter_store";

	class FilterItem {
		public static final int STATE_NORMAL = 0;
		public static final int STATE_DOWNLOADED = 1;
		public static final int STATE_DOWNLOADING = 2;
		public String name;
		public String description;
		public String iconUrl;
		public String downloadUrl;
		public Bitmap iconBitmap;
		public String packageDes;
		public int state;
	}

	private Handler mHandler = new Handler();

	class ViewHolder {
		ImageView mIcon;
		TextView mTitle;
		TextView mDescription;
		Button mDownload;
	}

	class MyAdapter extends BaseAdapter {
		Context mContext;
		LinearLayout linearLayout = null;

		public MyAdapter(Context context) {
			mContext = context;
		}

		@Override
		public int getCount() {
			return mFilterItems.size();
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public Object getItem(int arg0) {
			return mFilterItems.get(arg0);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder = null;
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.filter_store_item,
						parent, false);
				holder = new ViewHolder();
				holder.mIcon = (ImageView) convertView
						.findViewById(R.id.filter_icon);
				holder.mTitle = (TextView) convertView
						.findViewById(R.id.filter_name);
				holder.mDescription = (TextView) convertView
						.findViewById(R.id.filter_description);
				holder.mDownload = (Button) convertView
						.findViewById(R.id.filter_download);
				convertView.setTag(holder);
			}
			final ViewHolder tHolder = (ViewHolder) convertView.getTag();
			if (mFilterItems.get(position).iconBitmap != null)
				tHolder.mIcon
						.setImageBitmap(mFilterItems.get(position).iconBitmap);
			else
				tHolder.mIcon
						.setImageResource(R.drawable.ic_camera_album_photo);
			tHolder.mTitle.setText(mFilterItems.get(position).name);
			tHolder.mDescription
					.setText(mFilterItems.get(position).description);
			switch (mFilterItems.get(position).state) {
			case FilterItem.STATE_NORMAL:
				tHolder.mDownload.setText(R.string.download);
				tHolder.mDownload.setEnabled(true);
				final int pos = position;
				tHolder.mDownload.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View arg0) {
						String url = mFilterItems.get(pos).downloadUrl;
						String pkgName = mFilterItems.get(pos).packageDes;
						mFilterItems.get(pos).state = FilterItem.STATE_DOWNLOADING;
						mDownloadService.getPackage(url, pkgName);
						tHolder.mDownload.setEnabled(false);
						tHolder.mDownload.setText(R.string.downloading);
					}
				});
				break;
			case FilterItem.STATE_DOWNLOADED:
				tHolder.mDownload.setText(R.string.downloaded);
				tHolder.mDownload.setEnabled(false);
				break;
			case FilterItem.STATE_DOWNLOADING:
				tHolder.mDownload.setText(R.string.downloading);
				tHolder.mDownload.setEnabled(false);
				break;
			default:
				break;
			}

			return convertView;
		}
	}

	private LayoutInflater inflater;
	private ListView mListView;
	private TextView mInfoView;
	private MyAdapter mAdapter = new MyAdapter(this);
	// ItemAdapter adapter;
	List<FilterItem> mFilterItems = new ArrayList<FilterItem>();

	// List<Item> items = new ArrayList<Item>();

	private void updateListItems() {
		// items.clear();
		// for (int i = 0; i < mFilterItems.size(); i++) {
		// FilterItem fi = mFilterItems.get(i);
		// ImageView icon = new ImageView(this);
		// icon.setScaleType(ScaleType.CENTER_INSIDE);
		// icon.setImageBitmap(fi.iconBitmap);
		// LinearLayout leftLayout = new LinearLayout(this);
		// leftLayout.setOrientation(LinearLayout.HORIZONTAL);
		// leftLayout.addView(icon);
		// LinearLayout rightLayout = new LinearLayout(this);
		// rightLayout.setOrientation(LinearLayout.HORIZONTAL);
		// //TextView tView = new TextView(this);
		// //tView.setText(R.string.filter_store_free);
		// //LayoutParams lpTV = new LayoutParams(Gravity.CENTER_VERTICAL);
		// //lpTV.rightMargin = Util.dip2px(this, 20);
		// //tView.setLayoutParams(lpTV);
		// //rightLayout.addView(tView);
		// //tView.setPadding(0, 0, Util.dip2px(this, 20), 0);
		// Button btn = new Button(this);
		// if (fi.state == FilterItem.STATE_NORMAL) {
		// btn.setText(R.string.download);
		// btn.setEnabled(true);
		// } else if (fi.state == FilterItem.STATE_DOWNLOADED) {
		// btn.setText(R.string.downloaded);
		// btn.setEnabled(false);
		// } else {
		// btn.setText(R.string.downloading);
		// btn.setEnabled(false);
		// }
		// final int pos = i;
		// final Button fBtn = btn;
		// btn.setOnClickListener(new OnClickListener() {
		//
		// @Override
		// public void onClick(View arg0) {
		// String url = mFilterItems.get(pos).downloadUrl;
		// String pkgName = mFilterItems.get(pos).packageDes;
		// mFilterItems.get(pos).state = FilterItem.STATE_DOWNLOADING;
		// mDownloadService.getPackage(url, pkgName);
		// fBtn.setEnabled(false);
		// fBtn.setText(R.string.downloading);
		// }
		// });
		// rightLayout.addView(btn);
		// items.add(new WidgetText2Item(fi.name, fi.description, leftLayout,
		// rightLayout, R.id.filter_store_widget_item));
		// }
		// adapter = new ItemAdapter(this, items);
		// mListView.setAdapter(adapter);
		// adapter.notifyDataSetChanged();
		mListView.setAdapter(mAdapter);
		mAdapter.notifyDataSetChanged();
	}

	private FilterDownloadService mDownloadService;

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder b) {
			Log.d(TAG, "service connected");
			mDownloadService = ((FilterDownloadService.LocalBinder) b)
					.getService();
			mDownloadService.setListener(FilterStoreActivity.this);
			aquireFilterList();
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mDownloadService = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.filter_store);
		setTitle(R.string.store_title);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		// setActivityContentView(R.layout.filter_store);
		// setTitle2(getText(R.string.store_title));
		// showBackKey(true);
		inflater = LayoutInflater.from(this);
		mListView = (ListView) findViewById(R.id.filter_store_listview);
		mInfoView = (TextView) findViewById(R.id.filter_store_info);
		bindDownloadService();
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
	protected void onPause() {
		super.onPause();
		if (mDownloadService != null)
			mDownloadService.cancelTasks();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mInfoView.setVisibility(View.GONE);
		aquireFilterList();
	}

	@Override
	protected void onDestroy() {
		unbindDownloadService();
		super.onDestroy();
	}

	private void aquireFilterList() {
		Log.d(TAG, "aquireFilterList");
		if (mDownloadService != null) {
			String url = "http://cameraapi.yunos.com/camera/ffl.htm?page_no=1&page_size=20&version=2.7&uuid=XXX";
			mDownloadService.getFilterList(url);
		}
	}

	private void bindDownloadService() {
		Intent intent = new Intent(this, FilterDownloadService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	private void unbindDownloadService() {
		if (mDownloadService != null) {
			mDownloadService.setListener(null);
		}
		if (mConnection != null) {
			Log.d(TAG, "unbindService");
			unbindService(mConnection);
		}
	}

	@Override
	public void onFilterListGet(String str) {
		if (str == null || str == "") {
			// Toast.makeText(this, R.string.filter_getlist_fail,
			// Toast.LENGTH_SHORT).show();
			mInfoView.setVisibility(View.VISIBLE);
			return;
		} else {
			mInfoView.setVisibility(View.GONE);
			Log.v("mk", "====str = " + str);
			mFilterItems.clear();
			try {
				JSONTokener jsonParser = new JSONTokener(str);
				JSONObject data = (JSONObject) jsonParser.nextValue();
				int pageNum = data.getInt("currPage");
				Log.d(TAG, "page num is " + pageNum);
				JSONArray array = data.getJSONArray("data");
				for (int i = 0; i < array.length(); i++) {
					JSONObject jsonItem = array.getJSONObject(i);
					FilterItem item = new FilterItem();
					item.name = jsonItem.getString("name");
					item.description = jsonItem.getString("desc");
					item.iconUrl = jsonItem.getString("icon");
					item.downloadUrl = jsonItem.getString("downloadUrl");
					item.packageDes = jsonItem.getString("package");
					item.state = FilterItem.STATE_NORMAL;
					for (FilterData fd : FiltersManager.getFilterData()) {
						if (item.packageDes.equals(fd.getPackage())) {
							item.state = FilterItem.STATE_DOWNLOADED;
							break;
						}
					}
					mDownloadService.getIcon(item.iconUrl, item.packageDes);
					mFilterItems.add(item);
				}
			} catch (Exception e) {
				Log.v(TAG, "onFilterGet, exception = " + e.toString());
				// ignore and return
				return;
			}
		}
		mDownloadService.checkIconList();
		updateListItems();
	}

	@Override
	public void onFilterIconGet(Bitmap bitmap, String pkgName) {
		for (FilterItem item : mFilterItems) {
			if (item.packageDes.equals(pkgName)) {
				if (bitmap != null)
					Log.d(TAG, "onFilterIconGet " + pkgName);
				item.iconBitmap = bitmap;
				updateListItems();
				break;
			}
		}
	}

	@Override
	public void onFilterPackageGet(String pkgName) {
		Log.d(TAG, "onFilterPackageGet");
		for (FilterItem item : mFilterItems) {
			if (item.packageDes.equals(pkgName)) {
				Log.d(TAG, "onFilterPackageGet " + pkgName);
				item.state = FilterItem.STATE_DOWNLOADED;
				updateListItems();
				break;
			}
		}
		FiltersManager.getFiltersDataFromDB(getContentResolver());
	}

	@Override
	public void onFilterDownloadFailed(final String pkgName) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(FilterStoreActivity.this,
						getString(R.string.download_falied), Toast.LENGTH_SHORT)
						.show();
				for (FilterItem item : mFilterItems) {
					if (item.packageDes.equals(pkgName)) {
						Log.d(TAG, "onFilterPackageGet " + pkgName);
						item.state = FilterItem.STATE_NORMAL;
						updateListItems();
						break;
					}
				}
			}
		});
	}
}
