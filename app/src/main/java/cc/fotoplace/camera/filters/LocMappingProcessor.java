package cc.fotoplace.camera.filters;

import java.util.HashMap;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.util.Log;
import cc.fotoplace.glrenderer.BitmapTexture;
import cc.fotoplace.glrenderer.GLCanvas;
import cc.fotoplace.glrenderer.ShaderParameter;
import cc.fotoplace.glrenderer.UniformShaderParameter;

import com.yunos.camera.ImageProcessNativeInterface;

public class LocMappingProcessor extends BaseProcessor{

    private BitmapTexture locTabelTexture = null;
    private String bitmapPath;
    private float locCenter[] = new float[2];
    public LocMappingProcessor() {
        NAME = "LocMapping";
        mMethodString = ""
                + "uniform vec2 " + LOC_CENTER + ";\n"
                + "uniform sampler2D " + LOC_TABLE + ";\n"
                + "mediump vec3 distanceTable(vec4 texel, vec2 coord, vec2 center, sampler2D table) { \n"
                + "    vec2 tc = 2.0 * coord - 1.0; \n"
                + "    mediump float d = dot(tc, tc); \n"
                + "    vec2 lookup = vec2(d, texel.r);\n"
                + "    texel.r = texture2D(table, lookup).r;\n"
                + "    lookup.y = texel.g;\n"
                + "    texel.g = texture2D(table, lookup).g;\n"
                + "    lookup.y = texel.b;\n"
                + "    texel.b    = texture2D(table, lookup).b;\n"
                + "    return texel.rgb; \n"
                + "}\n";
        mProcessString = "    texel.rgb = distanceTable(texel, vTextureCoord, " + LOC_CENTER + ", " + LOC_TABLE + ");\n";
        paramMap = new HashMap<String, Integer>();
        mTextureCount = 1;
    }
    @Override
    public void loadProcess(String path, String mProcessString) {
        // TODO Auto-generated method stub
        String sub[] = mProcessString.split(",");
        bitmapPath = path+"/"+sub[0];
        locCenter[0] = Float.valueOf(sub[1]);
        locCenter[1] = Float.valueOf(sub[2]);
        Bitmap bitmap = BitmapFactory.decodeFile(bitmapPath);
        locTabelTexture = new BitmapTexture(bitmap);
    }

    @Override
    public void appendParams(List<ShaderParameter> paramList) {
        // TODO Auto-generated method stub
        int length = paramList.size();
        paramList.add(new UniformShaderParameter(LOC_TABLE));
        paramMap.put(LOC_TABLE, length);
        length = paramList.size();
        paramList.add(new UniformShaderParameter(LOC_CENTER));
        paramMap.put(LOC_CENTER, length);
    }

    @Override
    public void prepareParams(ShaderParameter[] params, GLCanvas canvas) {
        // TODO Auto-generated method stub
        bindTexture(canvas, locTabelTexture);
        GLES20.glUniform1i(params[paramMap.get(LOC_TABLE)].handle, textureIndex);
        FiltersUtil.checkError();
        GLES20.glUniform2f(params[paramMap.get(LOC_CENTER)].handle, locCenter[0], locCenter[1]);
        FiltersUtil.checkError();
    }
    @Override
    public void jpegProcess() {
        // TODO Auto-generated method stub
        Log.d("dyb_filter", NAME + " jpegProcess");
        Bitmap bitmap = BitmapFactory.decodeFile(bitmapPath);
        ImageProcessNativeInterface.filterLocMapping(bitmap, locCenter);
        bitmap.recycle();
    }

}
