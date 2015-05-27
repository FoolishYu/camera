
package cc.fotoplace.camera;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

import cc.fotoplace.camera.platform.PlatformHelper;
import cc.fotoplace.gallery3d.exif.ExifInterface;


import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

public class Storage {
    private static final String TAG = "CameraStorage";

    public static final String DCIM =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

    public static String DIRECTORY = DCIM + "/Camera";

    // Match the code in MediaProvider.computeBucketValues().
    public static final String BUCKET_ID =
            String.valueOf(DIRECTORY.toLowerCase(Locale.US).hashCode());

    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long LOW_STORAGE_THRESHOLD = 50000000; // 50M

    public static final int FILE_TYPE_PHOTO = 0; // photo
    public static final int FILE_TYPE_VIDEO = 1; // video
    public static final int FILE_TYPE_PANO = 2; // panorama

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void setImageSize(ContentValues values, int width, int height) {
        // The two fields are available since ICS but got published in JB
        if (ApiHelper.HAS_MEDIA_COLUMNS_WIDTH_AND_HEIGHT) {
            values.put(MediaColumns.WIDTH, width);
            values.put(MediaColumns.HEIGHT, height);
        }
    }

    public static void writeFile(String path, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write data", e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
            }
        }
    }

    // Save the image and add it to media store.
    public static Uri addImage(ContentResolver resolver, String title,
            long date, Location location, int orientation, ExifInterface exif,
            byte[] jpeg, int width, int height) {
        // Save the image.
        String path = generateFilepath(title);
        if (exif != null) {
            try {
                exif.writeExif(jpeg, path);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            }
        } else {
            writeFile(path, jpeg);
        }
        return addImage(resolver, title, date, location, orientation,
                jpeg.length, path, width, height);
    }

    // Add the image to media store.
    public static Uri addImage(ContentResolver resolver, String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height) {
        // Insert into MediaStore.
        ContentValues values = new ContentValues(9);
        values.put(ImageColumns.TITLE, title);
        values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
        values.put(ImageColumns.DATE_TAKEN, date);
        values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        // Clockwise rotation in degrees. 0, 90, 180, or 270.
        values.put(ImageColumns.ORIENTATION, orientation);
        values.put(ImageColumns.DATA, path);
        values.put(ImageColumns.SIZE, jpegLength);

        setImageSize(values, width, height);

        if (location != null) {
            values.put(ImageColumns.LATITUDE, location.getLatitude());
            values.put(ImageColumns.LONGITUDE, location.getLongitude());
        }

        Uri uri = null;
        try {
            uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Throwable th) {
            // This can happen when the external volume is already mounted, but
            // MediaScanner has not notify MediaProvider to add that volume.
            // The picture is still safe and MediaScanner will find it and
            // insert it into MediaProvider. The only problem is that the user
            // cannot click the thumbnail to review the picture.
            Log.e(TAG, "Failed to write MediaStore" + th);
        }
        return uri;
    }

    public static void deleteImage(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
        } catch (Throwable th) {
            Log.e(TAG, "Failed to delete image: " + uri);
        }
    }

    public static String generateFilepath(String title) {
        return DIRECTORY + '/' + title + ".jpg";
    }

    public static long getAvailableSpace() {
        String state = Environment.getExternalStorageState();
        Log.d(TAG, "External storage state=" + state);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        File dir = new File(DIRECTORY);
        dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(DIRECTORY);
            return stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatible() {
        File nnnAAAAA = new File(DCIM, "100ANDRO");
        if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
            Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
        }
    }

    public static String getBucketId(String directory) {
        return String.valueOf(DIRECTORY.toLowerCase(Locale.US).hashCode());
    }

    public static String getBucketId(int fileType) {
        return getBucketId(DIRECTORY);
    }

    public static boolean isStorageReady() {
        return true;
    }

    // Added by kongzhe.kz for multi-sd card supports.
    public static String INTERNAL_SDCARD_PATH = null;
    public static String EXTERNAL_SDCARD_PATH = null;
    public static String OTHERS_SDCARD_PATH = null;
    private static StorageManager mStorageManager;
    private static String sMountPoint = null;

    public static String getMountPoint() {
        return sMountPoint;
    }

    public static void updateMountPoint(String mountPoint) {
        // Currently this function is only used for MTK 2 SD cards support.
        // If not mtk platform, avoid using this function
        if (!PlatformHelper.supportExternalSDCard()) return;
        sMountPoint = mountPoint;
        DIRECTORY = sMountPoint + "/DCIM/Camera";
    }

    public static boolean isExternalStorageAvailable() {
        String mounted_point = sMountPoint;
        updateMountPoint(EXTERNAL_SDCARD_PATH);
        if (getAvailableSpace() == Storage.UNAVAILABLE) {
            updateMountPoint(mounted_point);
            return false;
        } else {
            updateMountPoint(mounted_point);
            return true;
        }
    }

    public static boolean isInternalStorageAvailable() {
        String mounted_point = sMountPoint;
        updateMountPoint(INTERNAL_SDCARD_PATH);
        if (getAvailableSpace() == Storage.UNAVAILABLE) {
            updateMountPoint(mounted_point);
            return false;
        } else {
            updateMountPoint(mounted_point);
            return true;
        }
    }

    public static boolean isExternalStorage() {
        if (!PlatformHelper.supportExternalSDCard()) return false;
        return sMountPoint.equals(EXTERNAL_SDCARD_PATH);
    }

    public static void init(Context context){
        // Currently this function is only used for MTK 2 SD cards support.
        // If not mtk platform, avoid using this function
        if (!PlatformHelper.supportExternalSDCard()) return;
        mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        Object[] storageVolumes = StorageManager_getVolumeList(mStorageManager);
        if(storageVolumes == null)
            return;
        int num = 0;
        for (Object sv : storageVolumes) {
            if (num > 2) { // first two volumes.
                break;
            } else if (num > 1) {
                OTHERS_SDCARD_PATH = StorageVolume_getPath(sv);
                Log.v("mk", "OTHERS_SDCARD_PATH = " + OTHERS_SDCARD_PATH);
            } else {
                if (StorageVolume_isRemovable(sv)) {
                    EXTERNAL_SDCARD_PATH = StorageVolume_getPath(sv);
                    Log.v("mk", "EXTERNAL_SDCARD_PATH = " + EXTERNAL_SDCARD_PATH);
                } else {
                    INTERNAL_SDCARD_PATH = StorageVolume_getPath(sv);
                    Log.v("mk", "INTERNAL_SDCARD_PATH = " + INTERNAL_SDCARD_PATH);
                }
            }
            num++;
        }
    }
    
    private static String StorageVolume_getPath(Object storageVolume) {
        String path = null;
        Class<?> clazz = null;
        Method method = null;
        try {
            clazz = storageVolume.getClass();
            method = clazz.getDeclaredMethod("getPath", new Class<?>[]{});
            path = (String) method.invoke(storageVolume);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return path;
    }
    
    private static Object[] StorageManager_getVolumeList(StorageManager storageManager){
        if(mStorageManager == null) return null;
        Object[] storageVolumes = null;
        try{
            storageVolumes = (Object[]) mStorageManager.getClass().getMethod("getVolumeList").invoke(mStorageManager);
            return storageVolumes;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static boolean StorageVolume_isRemovable(Object storageVolume) {
        boolean removable = false;
        Class<?> clazz = null;
        Method method = null;
        try {
            clazz = storageVolume.getClass();
            method = clazz.getDeclaredMethod("isRemovable", new Class<?>[]{});
            removable = (Boolean) method.invoke(storageVolume);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return removable;
    }

}
