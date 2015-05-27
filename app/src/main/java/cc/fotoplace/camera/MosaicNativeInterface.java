
package cc.fotoplace.camera;

import android.graphics.Bitmap;

import java.io.OutputStream;

public class MosaicNativeInterface {
    public static native int nativeInitThumbnail(int width, int height);

    public static native void nativeTransferGPU2CPU();

    public static native int nativeInitializeMosaic(int previewWidth, int previewHeight, int maxWidth);

    public static native int nativeStartMosaicing();

    public static native int nativeSetDirection(int dir);

    public static native int nativeTracking(int offset[]);

    public static native int nativeFinishMosaicing();

    public static native int nativeReleaseMemory();

    public static native int nativeSetPreviewData(byte[] data);

    public static native int nativeCompressResult(int size[], int jpegQuality,
            OutputStream jpegData, byte[] storage);

    public static native int nativeTestSaveYUV(byte imgData[], int jpegQuality,
            OutputStream jpegData, byte[] storage);

    public static native int nativeSetBitmapData(Bitmap bitmap);

    static {
        System.loadLibrary("mosaic_jni");
    }
}
