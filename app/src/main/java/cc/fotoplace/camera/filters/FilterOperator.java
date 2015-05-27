package cc.fotoplace.camera.filters;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.util.Log;
import cc.fotoplace.glrenderer.AttributeShaderParameter;
import cc.fotoplace.glrenderer.GLCanvas;
import cc.fotoplace.glrenderer.ShaderParameter;
import cc.fotoplace.glrenderer.UniformShaderParameter;

import com.yunos.camera.ImageProcessNativeInterface;

public class FilterOperator {
    private static final String TAG = "dyb_filter";
    public static final String BASE_VERTEXT_SHADER = ""
            + "uniform mat4 " + BaseProcessor.MATRIX_UNIFORM + ";\n"
            + "uniform mat4 " + BaseProcessor.TEXTURE_MATRIX_UNIFORM + ";\n"
            + "attribute vec2 " + BaseProcessor.POSITION_ATTRIBUTE + ";\n"
            + "varying vec2 vTextureCoord;\n"
            + "void main() {\n"
            + "  vec4 pos = vec4(" + BaseProcessor.POSITION_ATTRIBUTE + ", 0.0, 1.0);\n"
            + "  gl_Position = " + BaseProcessor.MATRIX_UNIFORM + " * pos;\n"
            + "  vTextureCoord = (" + BaseProcessor.TEXTURE_MATRIX_UNIFORM + " * pos).xy;\n"
            + "}\n";

    public static final String BASE_FRAGMENT_SHADER_PREFIX = ""
            + "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "varying vec2 vTextureCoord;\n"
            + "vec4 texel;\n"
            + "uniform samplerExternalOES " + BaseProcessor.TEXTURE_SAMPLER_UNIFORM + ";\n"
            + "uniform float " + BaseProcessor.ALPHA_UNIFORM + ";\n";

    public static final String BASE_FRAGMENT_SHADER_MAIN = ""
            + "void main() {\n"
            + "    texel = texture2D(" + BaseProcessor.TEXTURE_SAMPLER_UNIFORM + ", vTextureCoord);\n";

    public static final String BASE_FRAGMENT_SHADER_POSTFIX = ""
            + "    gl_FragColor = texel * " + BaseProcessor.ALPHA_UNIFORM + ";\n"
            + "}\n";



    private static final int FLOAT_SIZE = Float.SIZE / Byte.SIZE;

    private static final int COORDS_PER_VERTEX = 2;
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * FLOAT_SIZE;
    private static final int MAX_TEXTURE = 4;
    private String mFragmentShaderString;

    private int program;
    private int mVertexShader;
    private int mFragmentShader;

    //private BitmapTexture mColorTexture;
    //private BitmapTexture mLocTexture;

    private List<BaseProcessor> mProcessors;
    private List<ShaderParameter> mParameterList;
    private ShaderParameter[] mParameters;

    public int textureCount = 0;

    private FilterData mFilterData;

    public FilterOperator() {
        mProcessors = new ArrayList<BaseProcessor>();
        mParameterList = new ArrayList<ShaderParameter>();
    }

    public void initialize() {
        clear();
        mParameterList.add(new AttributeShaderParameter(BaseProcessor.POSITION_ATTRIBUTE));
        mParameterList.add(new UniformShaderParameter(BaseProcessor.ALPHA_UNIFORM));
        mParameterList.add(new UniformShaderParameter(BaseProcessor.MATRIX_UNIFORM));
        mParameterList.add(new UniformShaderParameter(BaseProcessor.TEXTURE_MATRIX_UNIFORM));
        mParameterList.add(new UniformShaderParameter(BaseProcessor.TEXTURE_SAMPLER_UNIFORM));
        textureCount = 1;
        for (String content : mFilterData.getContent()) {
            String keyValueSet[] = FiltersUtil.getKeyValueSet(content);
            loadProcessor(keyValueSet[0], keyValueSet[1]);
        }
        //loadProcessor(BaseProcessor.COLOR_MAPPING_PROCESSOR, "/sdcard/kelvinMap.png");
        //loadProcessor(BaseProcessor.MATRIX_TUNING_PROCESSOR, "0.299f, 0.587f, 0.114f, 0.299f, 0.587f, 0.114f, 0.299f, 0.587f, 0.114f");
    }

    public void loadProcessor(String processorName, String processorContent) {
        Log.d("dyb_filter", "name=" + processorName + " params=" + processorContent);
        BaseProcessor processor = BaseProcessor.generateProcessor(processorName);
        if (processor == null)
            return;

        String filterPath = FiltersUtil.getFilePath() + "/filters/" + mFilterData.getPackage();
        processor.loadProcess(filterPath, processorContent);
        processor.appendParams(mParameterList);
        if (processor.textureCount() > 0) {
            if (textureCount < MAX_TEXTURE) {
                processor.setTextureIndex(textureCount);
                textureCount += processor.textureCount();
            } else {
                return;
            }
        }
        mProcessors.add(processor);
    }

    public boolean assembleShaders() {
        Log.d(TAG, "begin assemble shaders");
        // vertext shader
        mVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, BASE_VERTEXT_SHADER);
        // fragment shader
        mFragmentShaderString = BASE_FRAGMENT_SHADER_PREFIX;
        for (BaseProcessor processor : mProcessors) {
            mFragmentShaderString += processor.getMethodString();
        }
        mFragmentShaderString += BASE_FRAGMENT_SHADER_MAIN;
        for (BaseProcessor processor : mProcessors) {
            mFragmentShaderString += processor.getProcessString();
        }
        mFragmentShaderString += BASE_FRAGMENT_SHADER_POSTFIX;
        Log.d(TAG, "============================ shader code ============================");
        Log.d(TAG, mFragmentShaderString);
        Log.d(TAG, "============================ shader code ============================");
        mFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, mFragmentShaderString);

        return true;
    }

    public ShaderParameter[] getParameters() {
        return mParameters;
    }

    public void prepareParameters(GLCanvas canvas) {
        //Log.d(TAG, "prepareParameters");
        // basic params
        GLES20.glUniform1i(mParameters[BaseProcessor.INDEX_TEXTURE_SAMPLER].handle, 0);
        FiltersUtil.checkError();
        GLES20.glUniform1f(mParameters[BaseProcessor.INDEX_ALPHA].handle, 1.0f);
        FiltersUtil.checkError();
        // processor params
        for (BaseProcessor processor : mProcessors) {
            processor.prepareParams(mParameters, canvas);
        }
    }

    public void setPosition(int mBoxCoordinates, int offset, float matrix[]) {
        //Log.d(TAG, "setPosition");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBoxCoordinates);
        FiltersUtil.checkError();
        GLES20.glVertexAttribPointer(mParameters[BaseProcessor.INDEX_POSITION].handle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, VERTEX_STRIDE, offset * VERTEX_STRIDE);
        FiltersUtil.checkError();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        FiltersUtil.checkError();
        GLES20.glUniformMatrix4fv(mParameters[BaseProcessor.INDEX_TEXTURE_MATRIX].handle, 1, false, matrix, 0);
        FiltersUtil.checkError();
    }

    public void getParameterHandles(int program) {
        mParameters = new ShaderParameter[mParameterList.size()];
        for (int i = 0; i < mParameterList.size(); i++) {
            mParameters[i] = mParameterList.get(i);
            mParameters[i].loadHandle(program);
            FiltersUtil.checkError();
            Log.d(TAG, "load handle " + i);
        }
    }

    public void setMatrix(float matrix[], int matrixSize) {
        GLES20.glUniformMatrix4fv(mParameters[BaseProcessor.INDEX_MATRIX].handle, 1, false, matrix, matrixSize);
        FiltersUtil.checkError();
    }

    public int getPositionHandle() {
        return mParameters[BaseProcessor.INDEX_POSITION].handle;
    }

    public int getVertextShader() {
        return mVertexShader;
    }

    public int getFragmentShader() {
        return mFragmentShader;
    }

    private static int loadShader(int type, String shaderCode) {
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        FiltersUtil.checkError();
        GLES20.glCompileShader(shader);
        FiltersUtil.checkError();

        return shader;
    }

    public void clear() {
        mProcessors.clear();
        mParameterList.clear();
    }

    public void setFilter(FilterData filter) {
        mFilterData = filter;
    }

    public void updateProgram() {
        assembleShaders();
        program = GLES20.glCreateProgram();
        FiltersUtil.checkError();
        if (program == 0) {
            throw new RuntimeException("Cannot create GL program: " + GLES20.glGetError());
        }
        GLES20.glAttachShader(program, mVertexShader);
        FiltersUtil.checkError();
        GLES20.glAttachShader(program, mFragmentShader);
        FiltersUtil.checkError();
        GLES20.glLinkProgram(program);
        FiltersUtil.checkError();
        int[] mLinkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, mLinkStatus, 0);
        if (mLinkStatus[0] != GLES20.GL_TRUE) {
            Log.e("dyb_filter", "Could not link program: ");
            Log.e("dyb_filter", GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        getParameterHandles(program);
    }

    public int getProgram() {
        return program;
    }

    public FilterData getFilter() {
        return mFilterData;
    }

    private void prepareProcessBitmap(Bitmap bitmap) {
        ImageProcessNativeInterface.filterSetBitmapToProcess(bitmap);
    }

    private ByteArrayOutputStream finishProcessBitmap(Bitmap bitmap, int jpegQuality) {
        ByteArrayOutputStream jpegData = new ByteArrayOutputStream();
        int ret = ImageProcessNativeInterface.filterGetProcessedJpegData(bitmap, jpegData, jpegQuality);
        if (ret != 0)
            return null;
        else
            return jpegData;
    }

    public byte[] processCapturedPhoto(byte jpegData[], int jpegQuality) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        prepareProcessBitmap(bitmap);
        for (BaseProcessor processor : mProcessors) {
            processor.jpegProcess();
        }
        ByteArrayOutputStream outputStream = finishProcessBitmap(bitmap, jpegQuality);
        bitmap.recycle();
        if (outputStream != null)
            return outputStream.toByteArray();
        else
            return null;
    }
    
    public void processPreviewBitmap(Bitmap bitmap) {
        prepareProcessBitmap(bitmap);
        for (BaseProcessor processor : mProcessors) {
            processor.jpegProcess();
        }
        ImageProcessNativeInterface.filterGetBitmap(bitmap);
    }
}
