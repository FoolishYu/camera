
package cc.fotoplace.camera;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Thumbnail {
    public static Bitmap createVideoThumbnailBitmap(FileDescriptor fd, int targetWidth) {
        return createVideoThumbnailBitmap(null, fd, targetWidth);
    }

    public static Bitmap createVideoThumbnailBitmap(String filePath, int targetWidth) {
        return createVideoThumbnailBitmap(filePath, null, targetWidth);
    }

    private static Bitmap createVideoThumbnailBitmap(String filePath, FileDescriptor fd,
            int targetWidth) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (filePath != null) {
                retriever.setDataSource(filePath);
            } else {
                retriever.setDataSource(fd);
            }
            bitmap = retriever.getFrameAtTime(-1);
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        if (bitmap == null)
            return null;

        // Scale down the bitmap if it is bigger than we need.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > targetWidth) {
            float scale = (float) targetWidth / width;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

    private static final String TAG = "Thumbnail";

    public static final String LAST_THUMB_FILENAME = "last_thumb";
    private static final int BUFSIZE = 4096;
    public static final String DCIM =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

//    public static final String DIRECTORY_VIDEO = DCIM + "/Camera";
//    public static final String DIRECTORY_IMAGE = DCIM + "/Camera";

    private Uri mUri;
    private Bitmap mBitmap;
    // whether this thumbnail is read from file
    private boolean mFromFile = false;

    public Thumbnail(Uri uri, Bitmap bitmap, int orientation) {
        mUri = uri;
        if (bitmap == null) {
            mBitmap = null;
        } else {
            mBitmap = rotateImage(bitmap, orientation);
        }
    }

    public Uri getUri() {
        return mUri;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setFromFile(boolean fromFile) {
        mFromFile = fromFile;
    }

    public boolean fromFile() {
        return mFromFile;
    }

    private static Bitmap rotateImage(Bitmap bitmap, int orientation) {
        if (orientation != 0) {
            // We only rotate the thumbnail once even if we get OOM.
            Matrix m = new Matrix();
            m.setRotate(orientation, bitmap.getWidth() * 0.5f,
                    bitmap.getHeight() * 0.5f);

            try {
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), m, true);
                // If the rotated bitmap is the original bitmap, then it
                // should not be recycled.
                if (rotated != bitmap)
                    bitmap.recycle();
                return rotated;
            } catch (Throwable t) {
                Log.w(TAG, "Failed to rotate thumbnail", t);
            }
        }
        return bitmap;
    }

    // Stores the bitmap to the specified file.
    public void saveTo(File file) {
        FileOutputStream f = null;
        BufferedOutputStream b = null;
        DataOutputStream d = null;
        try {
            f = new FileOutputStream(file);
            b = new BufferedOutputStream(f, BUFSIZE);
            d = new DataOutputStream(b);
            d.writeUTF(mUri.toString());
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, d);
            d.close();
        } catch (IOException e) {
            Log.e(TAG, "Fail to store bitmap. path=" + file.getPath(), e);
        } finally {
            Util.closeSilently(f);
            Util.closeSilently(b);
            Util.closeSilently(d);
        }
    }

    // Loads the data from the specified file.
    // Returns null if failure.
    public static Thumbnail loadFrom(File file) {
        Uri uri = null;
        Bitmap bitmap = null;
        FileInputStream f = null;
        BufferedInputStream b = null;
        DataInputStream d = null;
        try {
            f = new FileInputStream(file);
            b = new BufferedInputStream(f, BUFSIZE);
            d = new DataInputStream(b);
            uri = Uri.parse(d.readUTF());
            bitmap = BitmapFactory.decodeStream(d);
            d.close();
        } catch (IOException e) {
            Log.i(TAG, "Fail to load bitmap. " + e);
            return null;
        } finally {
            Util.closeSilently(f);
            Util.closeSilently(b);
            Util.closeSilently(d);
        }
        Thumbnail thumbnail = createThumbnail(uri, bitmap, 0);
        if (thumbnail != null)
            thumbnail.setFromFile(true);
        return thumbnail;
    }

    public static Thumbnail getLastThumbnail(ContentResolver resolver, boolean isImage) {
        /*
         * Media image = getLastImageThumbnail(resolver); Media video =
         * getLastVideoThumbnail(resolver); Log.e("testthum",
         * "get last thumbnail called"); if (image == null && video == null)
         * return null; if (video == null) Log.e("testthum", "video is null");
         * Bitmap bitmap = null; Media lastMedia; // If there is only image or
         * video, get its thumbnail. If both exist, // get the thumbnail of the
         * one that is newer. if (image != null && (video == null ||
         * image.dateTaken >= video.dateTaken)) { Log.e("testthum",
         * "get thumbnail image"); bitmap =
         * Images.Thumbnails.getThumbnail(resolver, image.id,
         * Images.Thumbnails.MINI_KIND, null); lastMedia = image; } else {
         * Log.e("testthum", "get thumbnail video"); bitmap =
         * Video.Thumbnails.getThumbnail(resolver, video.id,
         * Video.Thumbnails.MINI_KIND, null); lastMedia = video; }
         */

        Media media = isImage ? getLastImageThumbnail(resolver) :
                getLastVideoThumbnail(resolver);
        if (media == null) {
            Log.d("dyb", "media is null");
            return null;
        }
        Bitmap bitmap = null;
        // If there is only image or video, get its thumbnail. If both exist,
        // get the thumbnail of the one that is newer.
        if (isImage) {
            File dir = new File(Storage.DIRECTORY);
            dir.mkdir();
            if (!dir.isDirectory() || !dir.canWrite()) {
                Log.d("dyb", "dir error");
                return null;
            }
            if (dir.listFiles().length == 0) {
                Log.d("mk", "dir.listFiles().length = " + dir.listFiles().length);
                return null;
            }
            bitmap = Images.Thumbnails.getThumbnail(resolver, media.id,
                    Images.Thumbnails.MINI_KIND, null);
        } else {
            File dir = new File(Storage.DIRECTORY);
            dir.mkdir();
            if (!dir.isDirectory() || !dir.canWrite()) {
                Log.d("dyb", "dir error");
                return null;
            }
            if (dir.listFiles().length == 0) {
                Log.d("mk", "dir.listFiles().length = " + dir.listFiles().length);
                return null;
            }
            bitmap = Video.Thumbnails.getThumbnail(resolver, media.id,
                    Video.Thumbnails.MINI_KIND, null);
        }

        // Ensure database and storage are in sync.
        if (Util.isUriValid(media.uri, resolver)) {
            return createThumbnail(media.uri, bitmap, media.orientation);
        }
        return null;
    }

    private static class Media {
        public Media(long id, int orientation, long dateTaken, Uri uri) {
            this.id = id;
            this.orientation = orientation;
            // this.dateTaken = dateTaken;
            this.uri = uri;
        }

        public final long id;
        public final int orientation;
        // public final long dateTaken;
        public final Uri uri;
    }

    private static Media getLastImageThumbnail(ContentResolver resolver) {
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
        Log.d("mk", "getLastImageThumbnail() = " + baseUri.toString());
        Log.d("mk", "DCIM = " + Storage.DCIM.toString());
        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[] {
                ImageColumns._ID, ImageColumns.ORIENTATION,
                ImageColumns.DATE_TAKEN
        };
        String selection = ImageColumns.MIME_TYPE + "='image/jpeg'" + "AND " +
                ImageColumns.BUCKET_ID + "=" + Storage.getBucketId(Storage.FILE_TYPE_PHOTO);
        // ImageColumns.BUCKET_DISPLAY_NAME + "='Photo'";
        String order = ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = resolver.query(query, projection, selection, null, order);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return new Media(id, cursor.getInt(1), cursor.getLong(2),
                        ContentUris.withAppendedId(baseUri, id));
            }
            Log.d("dyb", "cursor null");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static Media getLastVideoThumbnail(ContentResolver resolver) {
        Uri baseUri = Video.Media.EXTERNAL_CONTENT_URI;
        Log.d("mk", "getLastVideoThumbnail() = " + baseUri.toString());
        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[] {
                VideoColumns._ID, MediaColumns.DATA,
                VideoColumns.DATE_TAKEN
        };
        // String selection = ImageColumns.BUCKET_DISPLAY_NAME + "='Video'";
        String selection = "(" + ImageColumns.MIME_TYPE + "='video/3gpp'"
                + "OR " + ImageColumns.MIME_TYPE + "='video/mp4'" + ")"
                + "AND " + ImageColumns.BUCKET_ID + "="
                + Storage.getBucketId(Storage.FILE_TYPE_VIDEO);

        String order = VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = resolver.query(query, projection, selection, null, order);
            // Log.e("testthum", "cursor number is " + cursor.getCount());
            // if (cursor == null)

            if (cursor != null && cursor.moveToFirst()) {
                // Log.e("testthum", "getLastVideoThumbnail: " +
                // cursor.getString(1));
                long id = cursor.getLong(0);
                Log.d("dyb", "return new media");
                return new Media(id, 0, cursor.getLong(2),
                        ContentUris.withAppendedId(baseUri, id));
            }
            Log.d("dyb", "cursor is null");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.e("testthum", "return null");
        return null;
    }

    public static Thumbnail createThumbnail(byte[] jpeg, int orientation, int inSampleSize,
            Uri uri) {
        // Create the thumbnail.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = inSampleSize;

        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        Log.e("dyb", "thumbnail size is (" + bitmap.getWidth() + "," + bitmap.getHeight() + ")");
        return createThumbnail(uri, bitmap, orientation);
    }

    public static Bitmap createVideoThumbnail(FileDescriptor fd, int targetWidth) {
        return createVideoThumbnail(null, fd, targetWidth);
    }

    public static Bitmap createVideoThumbnail(String filePath, int targetWidth) {
        return createVideoThumbnail(filePath, null, targetWidth);
    }

    private static Bitmap createVideoThumbnail(String filePath, FileDescriptor fd, int targetWidth) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (filePath != null) {
                retriever.setDataSource(filePath);
            } else {
                retriever.setDataSource(fd);
            }
            bitmap = retriever.getFrameAtTime(-1);
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        if (bitmap == null)
            return null;

        // Scale down the bitmap if it is bigger than we need.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > targetWidth) {
            float scale = (float) targetWidth / width;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

    private static Thumbnail createThumbnail(Uri uri, Bitmap bitmap, int orientation) {
        if (bitmap == null) {
            Log.e("dyb_thumbnail", "Failed to create thumbnail from null bitmap");
            return null;
        }
        try {
            return new Thumbnail(uri, bitmap, orientation);
        } catch (IllegalArgumentException e) {
            Log.e("dyb_thumbnail", "Failed to construct thumbnail", e);
            return null;
        }
    }
}
