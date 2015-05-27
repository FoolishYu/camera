
package cc.fotoplace.camera.ui;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import cc.fotoplace.camera.ApiHelper;
import cc.fotoplace.camera.CameraActivity;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.Util;
import cc.fotoplace.camera.filters.FilterData;
import cc.fotoplace.camera.filters.FilterOperator;
import cc.fotoplace.camera.platform.PlatformHelper;
import cc.fotoplace.glrenderer.BitmapTexture;
import cc.fotoplace.glrenderer.ExtTexture;
import cc.fotoplace.glrenderer.GLES20Canvas;

import com.yunos.camera.ImageProcessNativeInterface;

@SuppressLint("ViewConstructor")
public class CameraSurfaceView extends GLSurfaceView {

    private static final String PERFORMANCE_TAG = "CameraPerformanceTag";
    
    private boolean isFullScreenMode = false;
    private int surfaceWidth = -1;
    private int surfaceHeight = -1;
    private float mScaleX = 1f, mScaleY = 1f;
    // private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    // private int mTextureID;
    // private boolean isPreviewSizeSet = false;
    private SurfaceCallback mSurfaceCallback;
    private int previewWidth = 0;
    private int previewHeight = 0;
    // private boolean isPreviewStarted = false;
    private GLES20Canvas mGL20Canvas;
    // private RawTexture mAnimTexture;
    private BitmapTexture mBitmapTexture;
    private BitmapTexture mReviewBitmapTexture;
    private boolean mInReviewMode = false;
    private boolean mIsReviewingLandscapeImage = false;
    private int mReviewingDisplayHeight;
    private int scanLineY = 0;
    // private TriangleBlurFilterFrame mFrameFilter;
    // private TriangleBlurFilterRender mRenderFilter;
    // private NormalFilter mFilter;
    // private boolean isPostviewGet = true;
    //int[] textures = new int[2];
    //int[] fbo = new int[1];
    //int[] fboDepth = new int[1];
    // private RectF mScreenRect = new RectF();
    private int xOff, yOffUp, yOffDown;
    private int xOffOld, yOffUpOld, yOffDownOld;
    private int xOffNew, yOffUpNew, yOffDownNew;
    private final float[] mTextureTransformMatrix = new float[16];
    private Bitmap mBlurBitmap;
    // private RectF mSurfaceRect = new RectF();
    private long previewStoppedTime;
    private ExtTexture mExtTexture;
    private boolean isSmoothChangePreviewSize = true;
    private int frameNum = 0;
    private int dropFrameCount = 2;
    private boolean isFadingIn;
    private boolean isPreviewStarted = true;
    private boolean pendingScale = false;
    private int mFilterIndex = 0;
    private FilterData mFilterData = null;
    private int mMul = 1;
    private FilterOperator mFilterOperator = new FilterOperator();
    private long startPreviewTime = -1;
    private long onCreateTime = -1;

    public interface SurfaceCallback {
        public void surfaceCreated();
        public void frameAvailable();
    }

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        if (ApiHelper.USE_888_PIXEL_FORMAT) {
            setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        } else {
            setEGLConfigChooser(5, 6, 5, 0, 0, 0);
        }
        if (ApiHelper.USE_888_PIXEL_FORMAT) {
            getHolder().setFormat(PixelFormat.RGB_888);
        } else {
            getHolder().setFormat(PixelFormat.RGB_565);
        }
        mVideoRenderer = new VideoRender(context);
        setRenderer(mVideoRenderer);
        mSurfaceCallback = (CameraActivity) context;
    }

    private VideoRender mVideoRenderer;

    private class VideoRender
    implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
        private String TAG = "VideoRender";

        private static final int FLOAT_SIZE_BYTES = 4;

        // private FloatBuffer mTriangleVertices;

        private final String mVertexShader =
                "uniform mat4 uMVPMatrix;\n" +
                        "uniform mat4 uSTMatrix;\n" +
                        "attribute vec4 aPosition;\n" +
                        "attribute vec4 aTextureCoord;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "void main() {\n" +
                        "  gl_Position = uMVPMatrix * aPosition;\n" +
                        "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                        "}\n";

        // private final String mFragmentShader =
        // "#extension GL_OES_EGL_image_external : require\n" +
        // "precision mediump float;\n" +
        // "mediump vec4 tColor;\n" +
        // "varying vec2 vTextureCoord;\n" +
        // "uniform samplerExternalOES sTexture;\n" +
        // "void main() {\n" +
        // "  tColor = texture2D(sTexture, vTextureCoord);\n" +
        // "  gl_FragColor = tColor;\n" +
        // "}\n";
        //
        // private String mFragmentShader = ShaderCode.TRIANGLE_BLUR_FRAGMENT;

        // private int mBlurHandle;
        // private int mDeltaHandle;

        private SurfaceTexture mSurfaceTexture;
        private boolean updateSurface = false;

        private int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

        // private MediaPlayer mMediaPlayer;

        public VideoRender(Context context) {
        }

        @Override
        public void onDrawFrame(GL10 glUnused) {

            if (mSurfaceTexture == null) {
                mExtTexture = new ExtTexture(mGL20Canvas, GL_TEXTURE_EXTERNAL_OES);
                mExtTexture.setSize(surfaceWidth, surfaceHeight);
                mSurfaceTexture = new SurfaceTexture(mExtTexture.getId());
                mSurfaceTexture.setOnFrameAvailableListener(this);
                mSurfaceCallback.surfaceCreated();
            }

            synchronized (this) {
                if (mSurfaceTexture != null && updateSurface && !isFadingIn && !mSuspended) {
                    mSurfaceTexture.updateTexImage();
                    mSurfaceTexture.getTransformMatrix(mSTMatrix);
                    updateSurface = false;
                }
            }
            // updateMatrix(true);
            if (isPreviewStarted) {
                updateMatrix(!isSmoothChangePreviewSize);
            }
            if (isFadingIn && pendingScale) {
                updateMatrix(false);
            }
            if (isFadingIn && mGL20Canvas.getDrawingAlpha() == 1) {
                pendingScale = true;
            }

            mGL20Canvas.setFilter(mFilterData);

            //if (Util.USE_FILTER && mFilterIndex != 0) {
            //mGL20Canvas.drawFilterTexture(mExtTexture, mSTMatrix, xOff, yOffUp, mExtTexture.getWidth()
            //   - 2 * xOff, mExtTexture.getHeight() - (yOffUp + yOffDown));
            //} else {
            //}
            //设置背景颜色
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (!mInReviewMode) {
                mGL20Canvas.drawTexture(mExtTexture, mSTMatrix, xOff, yOffUp, mExtTexture.getWidth()
                        - 2 * xOff, mExtTexture.getHeight() - (yOffUp + yOffDown));
                if (mBitmapTexture != null) {
                    mGL20Canvas.drawTexture(mBitmapTexture, xOff, yOffUp, mExtTexture.getWidth() - 2
                            * xOff, mExtTexture.getHeight() - (yOffUp + yOffDown));
                }
            } else {
                if (mReviewBitmapTexture != null) {
                    if (mIsReviewingLandscapeImage) {
                        int tempYOffUp = (mExtTexture.getHeight() - mReviewingDisplayHeight - (yOffUp + yOffDown)) / 2 + yOffUp;
                        mGL20Canvas.drawTexture(mReviewBitmapTexture, xOff, tempYOffUp, mExtTexture.getWidth() - 2 * xOff, mReviewingDisplayHeight);
                    } else {
                        mGL20Canvas.drawTexture(mReviewBitmapTexture, xOff, yOffUp, mExtTexture.getWidth() - 2
                                * xOff, mExtTexture.getHeight() - (yOffUp + yOffDown));
                    }
                }
            }

        }

        @Override
        public void onSurfaceChanged(GL10 glUnused, int width, int height) {
            surfaceWidth = width;
            surfaceHeight = height;
            mGL20Canvas.setSize(width, height);
        }

        @Override
        public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
            Log.d("dyb_surface", "camera surface view surface created");
            mGL20Canvas = new GLES20Canvas(mFilterOperator);
            setRenderMode(RENDERMODE_WHEN_DIRTY);
            if (mSurfaceTexture == null) {
                mExtTexture = new ExtTexture(mGL20Canvas, GL_TEXTURE_EXTERNAL_OES);
                mExtTexture.setSize(surfaceWidth, surfaceHeight);
                mSurfaceTexture = new SurfaceTexture(mExtTexture.getId());
                mSurfaceTexture.setOnFrameAvailableListener(this);
                mSurfaceCallback.surfaceCreated();
            }
            // First Time Enter to Initialize
            synchronized (this) {
                updateSurface = false;
            }
        }


        synchronized public void onFrameAvailable(SurfaceTexture surface) {
            updateSurface = true;
            requestRender();
            if (startPreviewTime != -1) {
                long cost = System.currentTimeMillis()-startPreviewTime;
                startPreviewTime = -1;
                Log.d("CameraPerformanceTag", "get first frame time is " + cost + " ms");
                long allTime = System.currentTimeMillis() - onCreateTime;
                onCreateTime = -1;
                Log.d("CameraPerformanceTag", "all time is " + allTime + " ms");
            }
            if (frameNum >= 0 && frameNum < dropFrameCount) {
                frameNum++;
            } else if (frameNum == dropFrameCount) {
                mSurfaceCallback.frameAvailable();
                frameNum = -1;
            }
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            if (shader != 0) {
                GLES20.glShaderSource(shader, source);
                GLES20.glCompileShader(shader);
                int[] compiled = new int[1];
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
                if (compiled[0] == 0) {
                    Log.e(TAG, "Could not compile shader " + shaderType + ":");
                    Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                    GLES20.glDeleteShader(shader);
                    shader = 0;
                }
            }
            return shader;
        }

        public void releaseSurfaceTexture() {
            synchronized (this) {
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.release();
                    mSurfaceTexture = null;
                    updateSurface = false;
                }
            }
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            if (program != 0) {
                GLES20.glAttachShader(program, vertexShader);
                checkGlError("glAttachShader");
                GLES20.glAttachShader(program, pixelShader);
                checkGlError("glAttachShader");
                GLES20.glLinkProgram(program);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] != GLES20.GL_TRUE) {
                    Log.e(TAG, "Could not link program: ");
                    Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                    GLES20.glDeleteProgram(program);
                    program = 0;
                }
            }
            return program;
        }

        private void checkGlError(String op) {
            int error;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        public SurfaceTexture getSurfaceTexture() {
            return mSurfaceTexture;
        }

    } // End of class VideoRender.

    public SurfaceTexture getSurfaceTexture() {
        Log.d("dyb_surface", "get surface texture");
        if (mVideoRenderer != null)
            return mVideoRenderer.getSurfaceTexture();
        else {
            return null;
        }
    }

    public void setScreenSize(int width, int height) {
        Log.v("mk", "CameraSurfaceView -- setScreenSize(), width = " + width + ", height = "
                + height);
        surfaceWidth = width;
        surfaceHeight = height;
    }

    // public void setFullScreenMode(boolean _isFullScreen) {
    // isFullScreenMode = _isFullScreen;
    // if (mExtTexture != null)
    // calculateMatrix();
    // }

    public void setPreviewSize(int width, int height, boolean _isFullScreen) {
        Log.v("mk", "CameraSurfaceView -- setPreviewSize(), width = " + width + ", height = "
                + height + ", _isFullScreen = " + _isFullScreen);
        isFullScreenMode = _isFullScreen;
        previewWidth = width;
        previewHeight = height;
        if (mExtTexture != null) {
            calculateMatrix();
        }
    }

    private void calculateMatrix() {
        Log.v("mk", "calculateMatrix() ---------------");
        float screenRatio = surfaceHeight / (float) surfaceWidth;
        float previewRatio = previewWidth / (float) previewHeight;
        xOffNew = 0;
        yOffUpNew = 0;
        yOffDownNew = 0;

        if (Math.abs(screenRatio - previewRatio) > 0.01) {
            if (isFullScreenMode) {
                if (screenRatio > previewRatio) {
                    xOffNew = -(int) ((screenRatio / previewRatio - 1) * 0.5 * mExtTexture
                            .getWidth());
                } else {
                    // textureScaleY = previewRatio/screenRatio;
                }
            } else {
                if (screenRatio > previewRatio) {
                    yOffUpNew = getResources().getDimensionPixelOffset(
                            R.dimen.full_picture_top_margin);
                    yOffDownNew = (int) (surfaceHeight * (1.0 - previewRatio / screenRatio))
                            - yOffUpNew;
                } else {
                    xOffNew = (int) (surfaceWidth * (1.0f - screenRatio / previewRatio) * 0.5);
                }
            }
        }
    }

    private void updateMatrix(boolean hard) {
        if (hard) {
            xOff = xOffNew;
            yOffUp = yOffUpNew;
            yOffDown = yOffDownNew;
        } else {
            xOff = (xOff + xOffNew) / 2;
            yOffUp = (yOffUp + yOffUpNew) / 2;
            yOffDown = (yOffDown + yOffDownNew) / 2;
        }
    }

    public void getDrawingAreaSize(int size[]) {
        size[0] = surfaceWidth - 2 * xOffNew;
        size[1] = surfaceHeight - (yOffUpNew + yOffDownNew);
        size[2] = yOffUpNew;
    }

    // public void previewStarted() {
    // synchronized (this) {
    // isPreviewStarted = true;
    // mBitmapTexture = null;
    // mCanvas.drawingBlur(false);
    // }
    // Log.d("dyb", "preview started");
    // }

    // public void previewStopped(Bitmap drawingBitmap, byte[] previewData) {
    // synchronized (this) {
    // isPreviewStarted = false;
    // isPostviewGet = false;
    // if (drawingBitmap != null && previewData != null) {
    // previewStoppedTime = System.currentTimeMillis();
    // long start = System.currentTimeMillis();
    // mBlurBitmap = drawingBitmap;//Bitmap.createBitmap(previewHeight,
    // previewWidth, Config.ARGB_8888);
    // ImageProcessNativeInterface.nativeGaussionBlur(mBlurBitmap.getWidth(),
    // mBlurBitmap.getHeight(), previewData, mBlurBitmap);
    // mBitmapTexture = new BitmapTexture(mBlurBitmap);
    // long cost = System.currentTimeMillis() - start;
    // Log.d("dyb", "blur time is " + cost);
    // mCanvas.drawingBlur(true);
    // requestRender();
    // }
    // }
    // }

    // private void copyPreviewTexture(GLCanvas canvas) {
    // int width = mAnimTexture.getWidth();
    // int height = mAnimTexture.getHeight();
    // canvas.beginRenderTarget(mAnimTexture);
    // // Flip preview texture vertically. OpenGL uses bottom left point
    // // as the origin (0, 0).
    // canvas.translate(0, height);
    // canvas.scale(1, -1, 1);
    // getSurfaceTexture().getTransformMatrix(mTextureTransformMatrix);
    // updateTransformMatrix(mTextureTransformMatrix);
    // canvas.drawTexture(mExtTexture, mTextureTransformMatrix, 0, 0, width,
    // height);
    // canvas.endRenderTarget();
    // }

    protected void updateTransformMatrix(float[] matrix) {
        Matrix.translateM(matrix, 0, .5f, .5f, 0);
        Matrix.scaleM(matrix, 0, mScaleX, mScaleY, 1f);
        Matrix.translateM(matrix, 0, -.5f, -.5f, 0);
    }

    public void startFadeIn(int width, int height, byte[] previewData, int isFlip) {
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        Log.v("mk", "-----------startFadeIn()--------------------");
        frameNum = 0;
        isPreviewStarted = false;
        synchronized (this) {
            isFadingIn = true;
            if (previewData == null)
                return;
            // create bitmap
            mMul = Util.findBestRatio(width, 160);
            Bitmap drawingBitmap = Bitmap.createBitmap(height/mMul, width/mMul, Config.ARGB_8888);
            xOff = xOffNew;
            yOffUp = yOffUpNew;
            yOffDown = yOffDownNew;
            xOffOld = xOffNew;

            yOffUpOld = yOffUpNew;
            yOffDownOld = yOffDownNew;
            previewStoppedTime = System.currentTimeMillis();
            long start = System.currentTimeMillis();

            if (mFilterIndex == 0) {
                ImageProcessNativeInterface.nativeGaussionBlur(drawingBitmap.getWidth(),
                        drawingBitmap.getHeight(), previewData, drawingBitmap, isFlip, mMul);
            } else {
                ImageProcessNativeInterface.nativeYuv2Bitmap(drawingBitmap.getWidth(), 
                        drawingBitmap.getHeight(), previewData, drawingBitmap, isFlip, mMul);
                mFilterOperator.processPreviewBitmap(drawingBitmap);
                ImageProcessNativeInterface.nativeBlurBitmap(drawingBitmap);
            }

            mBitmapTexture = new BitmapTexture(drawingBitmap);
            if (mBlurBitmap != null && !mBlurBitmap.isRecycled()) {
                mBlurBitmap.recycle();
                mBlurBitmap = drawingBitmap;
            }
            long cost = System.currentTimeMillis() - start;
            Log.d("dyb", "blur time is " + cost);
            mGL20Canvas.drawFadein(true);
            requestRender();
            pendingScale = false;
        }
    }

    public void startFadeOut() {
        isPreviewStarted = true;
        if (mGL20Canvas != null) {
            mGL20Canvas.drawFadein(false);
            setRenderMode(RENDERMODE_WHEN_DIRTY);
        }
        requestRender();
    }

    public float getSurfaceRatio() {
        return surfaceHeight / (float) surfaceWidth;
    }

    public void setSmoothChange(boolean _isSmooth) {
        isSmoothChangePreviewSize = _isSmooth;
    }

    private boolean mSuspended = false;

    public void setSuspended(boolean suspend) {
        mSuspended = suspend;
    }

    public boolean isSuspended() {
        return mSuspended;
    }
    
    public void onCreate() {
        onCreateTime = System.currentTimeMillis();
    }

    public void startPreview(int cameraId) {
        requestRender();
        dropFrameCount = PlatformHelper.getDropInitialPreviewCount(cameraId);
        frameNum = 0;
        isFadingIn = false;
        startPreviewTime = System.currentTimeMillis();
        long create2start = startPreviewTime - onCreateTime;
        Log.d(PERFORMANCE_TAG, "create to start time is " + create2start + " ms");
    }

    public void setFilter(int index, FilterData filter) {
        mFilterIndex = index;
        mFilterData = filter;
    }

    public byte[] processJpegData(byte jpgeData[], int jpegQuality) {
        if (mFilterIndex == 0) {
            return null;
        } else {
            return mFilterOperator.processCapturedPhoto(jpgeData, jpegQuality);
        }
    }

    public void showReviewBitmap(Bitmap bmp) {
        if (bmp != null && !bmp.isRecycled()) {
            mReviewBitmapTexture = new BitmapTexture(bmp);
            int displayWidth = Util.displayWidth;
            int imageWidth = bmp.getWidth();
            int imageHeight = bmp.getHeight();
            mReviewingDisplayHeight = displayWidth * imageHeight / imageWidth;
            if (imageWidth > imageHeight) {
                mIsReviewingLandscapeImage = true;
            } else {
                mIsReviewingLandscapeImage = false;
            }
            mGL20Canvas.setDrawingReviewBitmap(true);
            mInReviewMode = true;
        }
    }

    public void hideReviewBitmap() {
        if (mReviewBitmapTexture != null) {
            mReviewBitmapTexture.recycle();
            mReviewBitmapTexture = null;
            mGL20Canvas.setDrawingReviewBitmap(false);
            mInReviewMode = false;
        }
    }

    public void setReviewMode() {
        mInReviewMode = true;
        mGL20Canvas.setDrawingReviewBitmap(true);
    }

    public void releaseSurfaceTexture() {
        if (mVideoRenderer != null)
            mVideoRenderer.releaseSurfaceTexture();
    }

    public void takePicture() {
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }


} // End of class VideoSurfaceView.
