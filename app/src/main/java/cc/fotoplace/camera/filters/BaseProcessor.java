package cc.fotoplace.camera.filters;

import android.opengl.GLES20;
import android.util.Log;

import cc.fotoplace.glrenderer.BasicTexture;
import cc.fotoplace.glrenderer.GLCanvas;
import cc.fotoplace.glrenderer.ShaderParameter;

import java.util.List;
import java.util.Map;

public abstract class BaseProcessor {

    public static final String MATRIX_TUNING_PROCESSOR = "matrix_tuning";
    public static final String COLOR_MAPPING_PROCESSOR = "color_mapping";
    public static final String LOC_MAPPING_PROCESSOR = "loc_mapping";
    public static final String GRADIENT_PROCESSOR = "gradient";
    public static final String OVERLAY_PROCESSOR = "overlay";
    // Basic Shader Parameters
    public static final String POSITION_ATTRIBUTE = "aPosition"; // INDEX_POSITION
    public static final String ALPHA_UNIFORM = "uAlpha"; // INDEX_ALPHA
    public static final String MATRIX_UNIFORM = "uMatrix"; // INDEX_MATRIX
    public static final String TEXTURE_MATRIX_UNIFORM = "uTextureMatrix"; // INDEX_TEXTURE_MATRIX
    public static final String TEXTURE_SAMPLER_UNIFORM = "uTextureSampler"; // INDEX_TEXTURE_SAMPLER

    // Costum Shader Parameters
    public static final String TRANSFORM_MATRIX = "transform_matrix";
    public static final String COLOR_TABLE = "color_table";
    public static final String LOC_TABLE = "loc_table";
    public static final String LOC_CENTER = "loc_center";
    public static final String GRADIENT_PIX_SIZE = "gradient_pix_size";
    public static final String BLOWOUT_TABLE = "blow_out_table";
    public static final String OVERLAY_TABLE = "overlay_table";

    public static final int INDEX_POSITION = 0;
    public static final int INDEX_ALPHA = 1;
    public static final int INDEX_MATRIX = 2;
    public static final int INDEX_TEXTURE_MATRIX = 3;
    public static final int INDEX_TEXTURE_SAMPLER = 4;


    public String mMethodString = null;
    public String mProcessString = null;
    public String NAME = null;
    public int mTextureCount = 0;
    public Map<String, Integer> paramMap = null;
    public int textureIndex;

    public abstract void loadProcess(String path, String mProcessString);
    public abstract void appendParams(List<ShaderParameter> paramList);
    public abstract void prepareParams(ShaderParameter[] params, GLCanvas canvas);
    public abstract void jpegProcess();

    public String getMethodString() { return mMethodString;}
    public String getProcessString(){ return mProcessString;}
    public String getName() { return NAME;}
    public int textureCount() { return mTextureCount;}
    public void setTextureIndex(int index) { textureIndex = index;}
    
    public static BaseProcessor generateProcessor(String type) {
        BaseProcessor ret = null;
        if (type.equals(MATRIX_TUNING_PROCESSOR)) {
            ret = new MatrixTuningProcessor();
        } else if (type.equals(COLOR_MAPPING_PROCESSOR)) {
            ret = new ColorMappingProcessor();
        } else if (type.equals(LOC_MAPPING_PROCESSOR)) {
            ret = new LocMappingProcessor();;
        } else if (type.equals(GRADIENT_PROCESSOR)){
            ret = new GradientProcessor();
        } else if (type.equals(OVERLAY_PROCESSOR)) {
            ret = new OverlayProcessor();
        }
        return ret;
    }

    public void bindTexture(GLCanvas canvas, BasicTexture texture) {
        switch (textureIndex) {
            case 1:
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                break;
            case 2:
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                break;
            case 3:
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                break;
            case 4:
                GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
                break;
            case 5:
                GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
                break;
            default:
                break;
        }

        FiltersUtil.checkError();
        texture.onBind(canvas);
        GLES20.glBindTexture(texture.getTarget(), texture.getId());
        FiltersUtil.checkError();
    }
}
