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

public class OverlayProcessor extends BaseProcessor{

    private BitmapTexture blowoutTexture = null;
    private BitmapTexture overlayTexture = null;
    private String blowoutBitmapPath;
    private String overlayBitmapPath;


    public OverlayProcessor() {
        NAME = "Overlay";
        mMethodString = ""
                + "uniform sampler2D " + BLOWOUT_TABLE + ";\n"
                + "uniform sampler2D " + OVERLAY_TABLE + ";\n"
                + "mediump vec3 overlayTable(vec4 texel, vec2 textureCoordinate, sampler2D blowout, sampler2D overlay) {\n"
                + "    vec3 bbTexel = texture2D(blowout, textureCoordinate).rgb; \n"
                + "    texel.r = texture2D(overlay, vec2(bbTexel.r, texel.r)).r; \n"
                + "    texel.g = texture2D(overlay, vec2(bbTexel.g, texel.g)).g; \n"
                + "    texel.b = texture2D(overlay, vec2(bbTexel.b, texel.b)).b; \n"
                + "    return texel.rgb;\n"
                + " }\n";
        mProcessString = "    texel.rgb = overlayTable(texel, vTextureCoord, " +  BLOWOUT_TABLE + ", " + OVERLAY_TABLE  + ");\n";
        paramMap = new HashMap<String, Integer>();
        mTextureCount = 2;
    }

    @Override
    public void loadProcess(String path, String mProcessString) {
        // TODO Auto-generated method stub
        String sub[] = mProcessString.split(",");
        blowoutBitmapPath = path+"/"+sub[0];
        overlayBitmapPath = path+"/"+sub[1];
        Bitmap bitmapBlow = BitmapFactory.decodeFile(blowoutBitmapPath);
        blowoutTexture = new BitmapTexture(bitmapBlow);
        Bitmap bitmapOverlay = BitmapFactory.decodeFile(overlayBitmapPath);
        overlayTexture = new BitmapTexture(bitmapOverlay);
    }

    @Override
    public void appendParams(List<ShaderParameter> paramList) {
        // TODO Auto-generated method stub
        int length = paramList.size();
        paramList.add(new UniformShaderParameter(BLOWOUT_TABLE));
        paramMap.put(BLOWOUT_TABLE, length);
        length = paramList.size();
        paramList.add(new UniformShaderParameter(OVERLAY_TABLE));
        paramMap.put(OVERLAY_TABLE, length);
    }

    @Override
    public void prepareParams(ShaderParameter[] params, GLCanvas canvas) {
        // TODO Auto-generated method stub
        bindTexture(canvas, blowoutTexture);
        GLES20.glUniform1i(params[paramMap.get(BLOWOUT_TABLE)].handle, textureIndex);
        FiltersUtil.checkError();
        textureIndex++;
        bindTexture(canvas, overlayTexture);
        GLES20.glUniform1i(params[paramMap.get(OVERLAY_TABLE)].handle, textureIndex);
        textureIndex--;
        FiltersUtil.checkError();
    }

    @Override
    public void jpegProcess() {
        // TODO Auto-generated method stub
        Log.d("dyb_filter", NAME + " jpegProcess");
        Bitmap blowoutBitmap = BitmapFactory.decodeFile(blowoutBitmapPath);
        Bitmap overlayBitmap = BitmapFactory.decodeFile(overlayBitmapPath);
        ImageProcessNativeInterface.filterOverlay(blowoutBitmap, overlayBitmap);
        blowoutBitmap.recycle();
        overlayBitmap.recycle();
    }

}
