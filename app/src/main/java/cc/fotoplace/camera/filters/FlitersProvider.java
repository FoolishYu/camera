package cc.fotoplace.camera.filters;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

public class FlitersProvider extends ContentProvider {

    private static final String TAG = "FlitersProvider";
    private static final String DATABASE_NAME = "filters.db";
    private static final int DATABASE_VERSION = 2;

    private static HashMap<String, String> sFiltersProjectionMap;
    private static final int FILTERS = 1;
    private static final int FILTERS_ID = 2;
    private static final UriMatcher sUriMatcher;
    
    private DatabaseHelper mOpenHelper;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(FiltersList.AUTHORITY, "filters", FILTERS);
        sUriMatcher.addURI(FiltersList.AUTHORITY, "filters/#", FILTERS_ID);

        sFiltersProjectionMap = new HashMap<String, String>();
        sFiltersProjectionMap.put(FiltersList.Filter._ID, FiltersList.Filter._ID);
        sFiltersProjectionMap.put(FiltersList.Filter.COLUMN_NAME_NAME,
                FiltersList.Filter.COLUMN_NAME_NAME);
        sFiltersProjectionMap.put(FiltersList.Filter.COLUMN_NAME_PACKAGE,
                FiltersList.Filter.COLUMN_NAME_PACKAGE);
        sFiltersProjectionMap.put(FiltersList.Filter.COLUMN_NAME_ICON,
                FiltersList.Filter.COLUMN_NAME_ICON);
        sFiltersProjectionMap.put(FiltersList.Filter.COLUMN_NAME_DESCRIPTION,
                FiltersList.Filter.COLUMN_NAME_DESCRIPTION);
        sFiltersProjectionMap.put(FiltersList.Filter.COLUMN_NAME_CONTENT,
                FiltersList.Filter.COLUMN_NAME_CONTENT);
    }
    
    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.v("mk", "insert(), uri = " + uri + ", initialValues = " + values);
        // Validates the incoming URI. Only the full provider URI is allowed for inserts.
        if (sUriMatcher.match(uri) != FILTERS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If the values map is empty, throw RuntimeException.
        if (values == null) {
            throw new RuntimeException("The ContentValue to insert is empty.");
        }

        // If the values map doesn't contain a name, throw RuntimeException.
        if (values.containsKey(FiltersList.Filter.COLUMN_NAME_NAME) == false) {
            throw new RuntimeException("The specific filter should have a name");
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        // Performs the insert and returns the ID of the new note.
        long rowId = db.insert(
            FiltersList.Filter.TABLE_NAME,   // The table to insert into.
            null,                            // A hack, SQLite sets this column value to null
                                             // if values is empty.
            values                           // A map of column names, and the values to insert
                                             // into the columns.
        );

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            // Creates a URI with the note ID pattern and the new row ID appended to it.
            Uri insertedUri = ContentUris.withAppendedId(FiltersList.Filter.CONTENT_ID_URI_BASE, rowId);
            // Notifies observers registered against this provider that the data changed.
            getContext().getContentResolver().notifyChange(insertedUri, null);
            return insertedUri;
        }

        // If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Log.v("mk", "query(), uri = " + uri);
        // Constructs a new query builder and sets its table name
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(FiltersList.Filter.TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
            // If the incoming URI is for notes, chooses the Notes projection
            case FILTERS:
                Log.v("mk", "match FILTERS");
                qb.setProjectionMap(sFiltersProjectionMap);
                break;
            case FILTERS_ID:
                Log.v("mk", "match FILTERS_ID");
                qb.setProjectionMap(sFiltersProjectionMap);
                qb.appendWhere(
                    FiltersList.Filter._ID +    // the name of the ID column
                    "=" +
                    // the position of the note ID itself in the incoming URI
                    uri.getPathSegments().get(FiltersList.Filter.FILTER_ID_PATH_POSITION));
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = FiltersList.Filter.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Opens the database object in "read" mode, since no writes need to be done.
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        Cursor c = qb.query(
            db,            // The database to query
            projection,    // The columns to return from the query
            selection,     // The columns for the where clause
            selectionArgs, // The values for the where clause
            null,          // don't group the rows
            null,          // don't filter by row groups
            orderBy        // The sort order
        );

        // Tells the Cursor what URI to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        Log.v("mk", "update(), uri = " + uri);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;
        switch (sUriMatcher.match(uri)) {
            case FILTERS:
                // Does the update and returns the number of rows updated.
                count = db.update(
                    FiltersList.Filter.TABLE_NAME, // The database table name.
                    values,                        // A map of column names and new values to use.
                    where,                         // The where clause column names.
                    whereArgs                      // The where clause column values to select on.
                );
                break;
            case FILTERS_ID:
                finalWhere =
                        FiltersList.Filter._ID +                         // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming note ID
                            get(FiltersList.Filter.FILTER_ID_PATH_POSITION);
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(
                    FiltersList.Filter.TABLE_NAME, // The database table name.
                    values,                        // A map of column names and new values to use.
                    finalWhere,                    // The final WHERE clause to use
                                                   // place holders for whereArgs
                    whereArgs                      // The where clause column values to select on, or
                                                   // null if the values are in the where argument.
                );
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;
        int count;
        switch (sUriMatcher.match(uri)) {
            case FILTERS:
                count = db.delete(
                    FiltersList.Filter.TABLE_NAME,  // The database table name
                    where,                          // The incoming where clause column names
                    whereArgs                       // The incoming where clause values
                );
                break;
            case FILTERS_ID:
                finalWhere =
                        FiltersList.Filter._ID +                            // The ID column name
                        " = " +                                             // test for equality
                        uri.getPathSegments().                              // the incoming note ID
                            get(FiltersList.Filter.FILTER_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                // Performs the delete.
                count = db.delete(
                    FiltersList.Filter.TABLE_NAME,  // The database table name.
                    finalWhere,                     // The final WHERE clause
                    whereArgs                       // The incoming where clause values.
                );
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + FiltersList.Filter.TABLE_NAME + " ("
                    + FiltersList.Filter._ID + " INTEGER PRIMARY KEY,"
                    + FiltersList.Filter.COLUMN_NAME_NAME + " TEXT,"
                    + FiltersList.Filter.COLUMN_NAME_PACKAGE + " TEXT,"
                    + FiltersList.Filter.COLUMN_NAME_ICON + " TEXT,"
                    + FiltersList.Filter.COLUMN_NAME_DESCRIPTION + " TEXT,"
                    + FiltersList.Filter.COLUMN_NAME_CONTENT + " TEXT"
                    + ");"
                    );
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS notes");
            // Recreates the database with a new version
            onCreate(db);
        }
    }
}
