package cc.fotoplace.camera.network;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import cc.fotoplace.camera.filters.FilterData;
import cc.fotoplace.camera.filters.FiltersManager;
import cc.fotoplace.camera.filters.FiltersUtil;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/*
 * Service for filter Store in the background thread.
 */
public class FilterDownloadService extends Service{

    private static final String TAG = "dyb_filter_store";

    class ListRetrieveTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            URL uri;
            String resultData = "";
            Log.d(TAG, "trying to connect to " + params[0]);
            try {
                uri = new URL(params[0]);
                HttpURLConnection urlConn = (HttpURLConnection)uri.openConnection();
                Log.d(TAG, "connection to " + uri.toString());
                InputStreamReader in = new InputStreamReader(urlConn.getInputStream());  
                BufferedReader buffer = new BufferedReader(in);  
                String inputLine = null;   
                while (((inputLine = buffer.readLine()) != null)) {
                    resultData += inputLine + "\n";  
                }
                in.close();
                urlConn.disconnect(); 
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } 
            return resultData;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(TAG, "begin get filter list");
            listRetrieveStarted = true;
            mHandler.sendEmptyMessageDelayed(MSG_CHECK_LIST_RETRIEVE, LIST_RETRIEVE_TIMEOUT);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            mListener.onFilterListGet(result);
            mHandler.removeMessages(MSG_CHECK_LIST_RETRIEVE);
        }

    }

    class IconRetrieveTask extends AsyncTask<String, Void, Bitmap> {
        private String pkgName;
        @Override
        protected Bitmap doInBackground(String... params) {
            URL url;
            try {
                url = new URL(params[0]);
            } catch (MalformedURLException e1) {
                e1.printStackTrace();
                return null;
            }
            pkgName = params[1];
            Bitmap ret = null;
            try {
                HttpURLConnection urlConn = (HttpURLConnection)url.openConnection();
                InputStream is = urlConn.getInputStream();
                ret = BitmapFactory.decodeStream(is);
                is.close();
                urlConn.disconnect(); 
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } 
            return ret;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            mListener.onFilterIconGet(result, pkgName);
            if (!iconList.isEmpty())
                iconList.remove(0);
            checkIconList();
        }
    }

    class PackageRetrieveTask extends AsyncTask<String, Void, Boolean> {
        String pkgName;
        @Override
        protected Boolean doInBackground(String... params) {
            Log.d(TAG, "getting package");
            URL url;
            try {
                url = new URL(params[0]);
            } catch (MalformedURLException e1) {
                e1.printStackTrace();
                return false;
            }
            pkgName = params[1];
            String folderName = FiltersUtil.getFilePath() + "/filters/" + pkgName;
            try {
                HttpURLConnection urlConn = (HttpURLConnection)url.openConnection();
                InputStream is = urlConn.getInputStream();
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
                ZipEntry ze;
                
                File folder = new File(folderName);
                folder.mkdir();
                while ((ze = zis.getNextEntry()) != null) {
                    String filename =  folderName + "/" + ze.getName();
                    Log.d(TAG, "zip file " + filename);
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            baos.write(buffer, 0, count);
                        }
                        FileOutputStream fout = new FileOutputStream(filename);
                        byte[] bytes = baos.toByteArray();
                        fout.write(bytes);
                        fout.close();
                    } catch (Exception e) {
                        continue;
                    }
                }
                zis.close();
                is.close();
                urlConn.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            String xmlPath = folderName + "/manifest.xml";
            Log.d(TAG, "xmlPath = " + xmlPath);
            FilterData filterData = FiltersUtil.parse(getResources(), xmlPath);
            if (filterData == null)
                Log.d(TAG, "filter data is null");
            try {
                FiltersManager.insertFilterToDB(getContentResolver(), filterData);
            } catch (Exception e) {
                mListener.onFilterDownloadFailed(pkgName);
                return false;
            }
            return true;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            isDownloadingPackage = true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            Log.d(TAG, "package post execute");
            if (result) {
                Log.d(TAG, "package post execute success");
                mListener.onFilterPackageGet(pkgName);
                if (!packageList.isEmpty())
                    packageList.remove(0);
                packageDownloadFailCount = 0;
            } else {
                packageDownloadFailCount++;
                if (packageDownloadFailCount >= 3) {
                    isDownloadingPackage = false;
                    packageList.clear();
                    return;
                }
            }
            isDownloadingPackage = false;
            checkPackageList();
        }
    }

    class LocalBinder extends Binder {
        public FilterDownloadService getService() {
            return FilterDownloadService.this;
        }
    }

    interface Listener {
        public void onFilterListGet(String str);
        public void onFilterIconGet(Bitmap bitmap, String pkgName);
        public void onFilterPackageGet(String pkgName);
        public void onFilterDownloadFailed(String pkgName);
    }

    class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_CHECK_LIST_RETRIEVE:
                    if (mListRetrieveTask != null && listRetrieveStarted)
                        cancelFilterListRetrieve(true);
                    break;

                default:
                    break;
            }
        }

    }

    private static final int MSG_CHECK_LIST_RETRIEVE = 0;

    private static final int LIST_RETRIEVE_TIMEOUT = 5000;

    private final IBinder mBinder = new LocalBinder();
    private Listener mListener;
    private ListRetrieveTask mListRetrieveTask;
    private IconRetrieveTask mIconRetrieveTask;
    private PackageRetrieveTask mPackageRetrieveTask;
    private boolean listRetrieveStarted = false;
    private List<String[]> iconList = new ArrayList<String[]>();
    private List<String[]> packageList = new ArrayList<String[]>();
    private MyHandler mHandler = new MyHandler();
    
    private int packageDownloadFailCount = 0;

    private boolean isDownloadingPackage = false;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "filter download service on bind");
        return mBinder;
    }

    public void getFilterList(String url) {
        Log.d(TAG, "getFilterList");
        mListRetrieveTask = new ListRetrieveTask();
        String params[] = new String[1];
        params[0] = url;
        mListRetrieveTask.execute(params);
    }

    public void getIcon(String url, String pkgName) {
        String param[] = new String[]{url, pkgName};
        iconList.add(param);
    }

    public void getPackage(String url, String pkgName) {
        String param[] = new String[]{url, pkgName};
        packageList.add(param);
        checkPackageList();
    }

    public void checkIconList() {
        if (!iconList.isEmpty()) {
            String param[] = iconList.get(0);
            mIconRetrieveTask = new IconRetrieveTask();
            mIconRetrieveTask.execute(param);
        }
    }

    private void checkPackageList() {
        if (!packageList.isEmpty() && !isDownloadingPackage) {
            String param[] = packageList.get(0);
            mPackageRetrieveTask = new PackageRetrieveTask();
            mPackageRetrieveTask.execute(param);
        }
    }

    private void cancelFilterListRetrieve(boolean isFail) {
        if (mListRetrieveTask != null)
            mListRetrieveTask.cancel(true);
        listRetrieveStarted = false;
        if (isFail)
            mListener.onFilterListGet(null);
    }

    private void cancelIconListRetrieve() {
        if (mIconRetrieveTask != null)
            mIconRetrieveTask.cancel(true);
        iconList.clear();
    }

    private void cancelPackageListRetrieve() {
        if (mPackageRetrieveTask != null)
            mPackageRetrieveTask.cancel(true);
        packageList.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "service on destroy");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //cancelIconListRetrieve();
        //cancelFilterListRetrieve();
        Log.d(TAG, "service on unbind");
        return super.onUnbind(intent);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void cancelTasks() {
        cancelFilterListRetrieve(false);
        cancelIconListRetrieve();
        cancelPackageListRetrieve();
    }

}
