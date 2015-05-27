package cc.fotoplace.camera.filters;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FiltersManager {
    
    private static List<FilterData> sFiltersList = new CopyOnWriteArrayList<FilterData>();
    
    public static final String ASSET_FILTERS_DIRECTORY = "filters";

    // Use /data/data/{package-name}/files as its parent directory
    public static final String REAL_FILTERS_DIRECTORY = "/filters/";
    private static final String FILTER_MANIFEST_NAME = "/manifest.xml";
    private static boolean isLoadingXML = false;

    private FiltersManager() {}

    public static List<FilterData> getFiltersDataFromDB(ContentResolver resolver) {
        if (isLoadingXML)
            return sFiltersList;
        sFiltersList.clear();
        Cursor cursor = resolver.query(FiltersList.Filter.CONTENT_URI, null, null, null, null);
        if (cursor == null || cursor.getCount() == 0) {
            return null;
        }
        // prepare temp values
        FilterData data = null;
        int column_name = cursor.getColumnIndexOrThrow(FiltersList.Filter.COLUMN_NAME_NAME);
        int column_pkg = cursor.getColumnIndexOrThrow(FiltersList.Filter.COLUMN_NAME_PACKAGE);
        int column_icon = cursor.getColumnIndexOrThrow(FiltersList.Filter.COLUMN_NAME_ICON);
        int column_desc = cursor.getColumnIndexOrThrow(FiltersList.Filter.COLUMN_NAME_DESCRIPTION);
        int column_content = cursor.getColumnIndexOrThrow(FiltersList.Filter.COLUMN_NAME_CONTENT);
        // iterate the cursor
        cursor.moveToFirst();
        do {
            data = new FilterData();
            String value = null;
            value = cursor.getString(column_name);
            if (value != null) {
                data.setName(value);
            }
            value = cursor.getString(column_pkg);
            if (value != null) {
                data.setPackage(value);
            }
            value = cursor.getString(column_icon);
            if (value != null) {
                data.setIconPath(value);
            }
            value = cursor.getString(column_desc);
            if (value != null) {
                data.setDescription(value);
            }
            value = cursor.getString(column_content);
            if (value != null) {
                data.setContent(FiltersUtil.convertContentToStringArray(value));
            }
            sFiltersList.add(data);
        } while (cursor.moveToNext());
        cursor.close();
        Log.d("dyb_filter", "get filter from db count = " + sFiltersList.size());
        return sFiltersList;
    }

    public static List<FilterData> getFiltersDataFromXML(Resources res, String path) {
        sFiltersList.clear();
        File rootDirectory = new File(path);
        String[] names = rootDirectory.list();
        for (String name : names) {
            String manifest = path + name + FILTER_MANIFEST_NAME;
            Log.d("dyb_filter", "parse xml : " + manifest);
            FilterData f = FiltersUtil.parse(res, manifest);
            if (f != null && f.isValid()) {
                sFiltersList.add(f);
            }
        }
        return sFiltersList;
    }

    public static void insertFilterToDB(ContentResolver resolver, List<FilterData> filters) {
        isLoadingXML = true;
        int index = 0;
        Log.d("dyb_filter", "filter count " + filters.size());
        for (FilterData f : filters) {
            Log.d("dyb_filter", "inserting " + index++);
            insertFilterToDB(resolver, f);
            Log.d("dyb_filter", "inserted");
        }
        isLoadingXML = false;
    }
    
    public static List<FilterData> getFilterData() {
        return sFiltersList;
    }

    public static Uri insertFilterToDB(ContentResolver resolver, FilterData data) {
        if (data == null || !data.isValid()) {
            throw new RuntimeException("The filter is empty or invalid");
        }
        ContentValues values = new ContentValues();
        values.put(FiltersList.Filter.COLUMN_NAME_NAME, data.getName());
        values.put(FiltersList.Filter.COLUMN_NAME_PACKAGE, data.getPackage());
        values.put(FiltersList.Filter.COLUMN_NAME_ICON, data.getIconPath());
        values.put(FiltersList.Filter.COLUMN_NAME_DESCRIPTION, data.getDescription());
        Log.v("mk", "content = " + FiltersUtil.convertStringArrayToContent(data.getContent()));
        values.put(FiltersList.Filter.COLUMN_NAME_CONTENT,
                FiltersUtil.convertStringArrayToContent(data.getContent()));
        resolver.insert(FiltersList.Filter.CONTENT_URI, values);
        return null;
    }
}
