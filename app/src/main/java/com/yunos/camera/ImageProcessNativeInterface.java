
package com.yunos.camera;

import java.io.OutputStream;

import android.graphics.Bitmap;

public class ImageProcessNativeInterface {

    public static native void nativeGaussionBlur(int width, int height, byte[] data, Bitmap bitmap,
            int isFlip, int mul);
    public static native void nativeYuv2Bitmap(int width, int height, byte[] data, Bitmap bitmap,
            int isFlip, int mul);
    public static native void nativeBlurBitmap(Bitmap bitmap);

    public static native int faceEnhancementInit(int width, int height);
    public static native int faceEnhancementProcessYUV(byte[] imgData, int faceLoc[], int isFlip, Bitmap bitmap, int jpegQuality,
            OutputStream jpegData, byte[] storage);
    public static native int faceEnhancementProcessBitmap(Bitmap bitmap, int params[]);
    public static native int faceEnhancementRelease();  

    public static native int filterSetBitmapToProcess(Bitmap bitmap);
    public static native int filterGetProcessedJpegData(Bitmap bitmap, OutputStream jpegData, int jpegQuality);
    public static native void filterMatrixTuning(float matrix[]);
    public static native void filterColorMapping(Bitmap bitmap);
    public static native void filterLocMapping(Bitmap bitmap, float center[]);
    public static native void filterGradient(float pixSize[]);
    public static native void filterOverlay(Bitmap blowoutBitmap, Bitmap overlayBitmap);
    public static native void filterGetBitmap(Bitmap bitmap);

    static {
        System.loadLibrary("imageprocess_jni");
    }
}
