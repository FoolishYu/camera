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

/*
 * back up texture translate algorithm
 * 
 * for (int i = 0; i < 256; i++) {
 *       ((unsigned char *)(outImage->imageData))[3*i+2] = ((unsigned char *)(inImage->imageData)+inImage->widthStep*2)[3*i];
 *       ((unsigned char *)(outImage->imageData))[3*i+1] = ((unsigned char *)(inImage->imageData+inImage->widthStep))[3*i+1];
 *       ((unsigned char *)(outImage->imageData))[3*i] = ((unsigned char *)(inImage->imageData))[3*i+2];
 *   }
 *
 * 
 * */
public class ColorMappingProcessor extends BaseProcessor{

    private BitmapTexture colorTabelTexture = null;
    private String bitmapPath;
    public ColorMappingProcessor() {
        NAME = "ColorMapping";
        mMethodString = ""
                      + "uniform sampler2D " + COLOR_TABLE + ";\n"
                      + "mediump vec3 colorTable(vec4 texel, sampler2D table) {\n"
                      + "    vec2 lookup; \n"
                      + "    lookup.y = .5;\n"
                      + "    lookup.x = texel.r;\n"
                      + "    texel.r = texture2D(table, lookup).r;\n"
                      + "    lookup.x = texel.g;\n"
                      + "    texel.g = texture2D(table, lookup).g;\n"
                      + "    lookup.x = texel.b;\n"
                      + "    texel.b = texture2D(table, lookup).b;\n"
                      + "    return texel.rgb;\n"
                      + " }\n";
        mProcessString = "    texel.rgb = colorTable(texel," +  COLOR_TABLE + ");\n";
        paramMap = new HashMap<String, Integer>();
        mTextureCount = 1;
    }

    @Override
    public void loadProcess(String path, String mProcessString) {
        bitmapPath = path+"/"+mProcessString;
        Bitmap bitmap = BitmapFactory.decodeFile(bitmapPath);
        colorTabelTexture = new BitmapTexture(bitmap);
    }

    @Override
    public void appendParams(List<ShaderParameter> paramList) {
        // TODO Auto-generated method stub
        int length = paramList.size();
        paramList.add(new UniformShaderParameter(COLOR_TABLE));
        paramMap.put(COLOR_TABLE, length);
    }

    @Override
    public void prepareParams(ShaderParameter[] params, GLCanvas canvas) {
        // TODO Auto-generated method stub
        bindTexture(canvas, colorTabelTexture);
        GLES20.glUniform1i(params[paramMap.get(COLOR_TABLE)].handle, textureIndex);
        FiltersUtil.checkError();
    }

    @Override
    public void jpegProcess() {
        // TODO Auto-generated method stub
        Log.d("dyb_filter", NAME + " jpegProcess");
        Bitmap bitmap = BitmapFactory.decodeFile(bitmapPath);
        ImageProcessNativeInterface.filterColorMapping(bitmap);
        bitmap.recycle();
    }

}
