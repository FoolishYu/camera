
package cc.fotoplace.camera;

import android.util.Log;

import cc.fotoplace.gallery3d.exif.ExifInterface;

import java.io.IOException;

public class Exif {
    private static final String TAG = "CameraExif";

    public static ExifInterface getExif(byte[] jpegData) {
        ExifInterface exif = new ExifInterface();
        try {
            exif.readExif(jpegData);
        } catch (IOException e) {
            Log.w(TAG, "Failed to read EXIF data", e);
        }
        return exif;
    }

    // Returns the degrees in clockwise. Values are 0, 90, 180, or 270.
    public static int getOrientation(ExifInterface exif) {
        Integer val = exif.getTagIntValue(ExifInterface.TAG_ORIENTATION);
        if (val == null) {
            return 0;
        } else {
            return ExifInterface.getRotationForOrientationValue(val.shortValue());
        }
    }

    public static int getOrientation(byte[] jpegData) {
        if (jpegData == null)
            return 0;

        ExifInterface exif = getExif(jpegData);
        return getOrientation(exif);
    }
}
