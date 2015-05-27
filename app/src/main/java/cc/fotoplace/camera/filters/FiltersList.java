package cc.fotoplace.camera.filters;

import android.net.Uri;
import android.provider.BaseColumns;

public final class FiltersList {

    public static final String AUTHORITY = "cc.fotoplace.camera.provider.FilterList";

    // This class cannot be instantiated
    private FiltersList() {
    }

    // For filters table
    public static final class Filter implements BaseColumns {
        private Filter() {}
        public static final String TABLE_NAME = "filters";

        private static final String SCHEME = "content://";
        private static final String PATH_FILTERS = "/filters";

        private static final String PATH_FILTER_ID = "/filters/";

        // 0-relative position of a note ID segment in the path part of a note ID URI
        public static final int FILTER_ID_PATH_POSITION = 1;

        // The content:// style URL for this table, same as base Uri.
        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_FILTERS);
        // Base Uri, must append a numeric filter id to this Uri.
        public static final Uri CONTENT_ID_URI_BASE = Uri
                .parse(SCHEME + AUTHORITY + PATH_FILTER_ID);
        // Uri matches for a single filter, specified by its ID, used to match incoming Uri.
        public static final Uri CONTENT_ID_URI_PATTERN
            = Uri.parse(SCHEME + AUTHORITY + PATH_FILTER_ID + "/#");

        // Default sort order
        public static final String DEFAULT_SORT_ORDER = _ID + " ASC";

        // Definition
        public static final String COLUMN_NAME_NAME = "name";
        public static final String COLUMN_NAME_ICON = "icon";
        public static final String COLUMN_NAME_DESCRIPTION = "description";
        public static final String COLUMN_NAME_CONTENT = "content";
        public static final String COLUMN_NAME_PACKAGE = "package";
    }

}
