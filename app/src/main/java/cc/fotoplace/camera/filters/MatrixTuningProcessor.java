package cc.fotoplace.camera.filters;

import java.util.HashMap;
import java.util.List;

import android.opengl.GLES20;
import android.util.Log;
import cc.fotoplace.glrenderer.GLCanvas;
import cc.fotoplace.glrenderer.ShaderParameter;
import cc.fotoplace.glrenderer.UniformShaderParameter;

import com.yunos.camera.ImageProcessNativeInterface;

public class MatrixTuningProcessor extends BaseProcessor{
    
    private float mMatrix[];
    
    public MatrixTuningProcessor() {
        NAME = MATRIX_TUNING_PROCESSOR;
        mMethodString = "uniform mat3 " + TRANSFORM_MATRIX + ";\n"
                      + "mediump vec4 linearTransform(vec4 src, mat3 transform) {\n "
                      + "    src.rgb *= transform;\n"
                      + "    return src;\n"
                      + "}\n";
        mProcessString = "    texel = linearTransform( texel, " + TRANSFORM_MATRIX + "); \n";
        paramMap = new HashMap<String, Integer>();
        mTextureCount = 0;
    }

    @Override
    public void loadProcess(String path, String mProcessString) {
        mMatrix = FiltersUtil.convertTextToFloatArrayt(mProcessString);
    }

    @Override
    public void appendParams(List<ShaderParameter> paramList) {
        // TODO Auto-generated method stub
        int length = paramList.size();
        paramList.add(new UniformShaderParameter(TRANSFORM_MATRIX));
        paramMap.put(TRANSFORM_MATRIX, length);
    }

    @Override
    public void prepareParams(ShaderParameter[] params, GLCanvas canvas) {
        GLES20.glUniformMatrix3fv(params[paramMap.get(TRANSFORM_MATRIX)].handle, 1, false, mMatrix, 0);
    }

    @Override
    public void jpegProcess() {
        // TODO Auto-generated method stub
        Log.d("dyb_filter", NAME + " jpegProcess");
        ImageProcessNativeInterface.filterMatrixTuning(mMatrix);
    }
}
