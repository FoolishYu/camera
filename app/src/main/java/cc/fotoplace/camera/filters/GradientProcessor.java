package cc.fotoplace.camera.filters;

import java.util.HashMap;
import java.util.List;

import android.opengl.GLES20;
import android.util.Log;
import cc.fotoplace.glrenderer.GLCanvas;
import cc.fotoplace.glrenderer.ShaderParameter;
import cc.fotoplace.glrenderer.UniformShaderParameter;

import com.yunos.camera.ImageProcessNativeInterface;

public class GradientProcessor extends BaseProcessor{
    
    private float gradientPixSize[] = new float[2];
    
    public GradientProcessor() {
        mMethodString = ""
                + "uniform vec2 " + GRADIENT_PIX_SIZE +";\n"
                + "mediump vec4 calculateGradient(samplerExternalOES sampler2d, vec2 _uv, vec2 pixSize) { \n"
                + "    mediump vec4 color = vec4(0.0, 0.0, 0.0, 0.0); \n"
                + "    highp vec4 up;\n"
                + "    highp vec4 down; \n"
                + "    highp vec4 left; \n"
                + "    highp vec4 right; \n"
                + "    highp vec4 temp; \n"
                + "    highp float r, g, b; \n"
                + "    mediump float amount = 0.0; \n"
                + "    mediump float ind = 0.0; \n"
                + "    mediump vec2 offset; \n"
                + "    mediump float pixNum = 1.0; \n"
                + "    highp float gray1 = 0.0; \n"
                + "    highp float gray2 = 0.0; \n"
                + "    highp float gray3 = 0.0; \n"
                + "    highp float maxG = 0.0; \n"
                + "    mediump float x,y; \n"
                + "    left  = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "    right = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "    for( ind = 0.0; ind < pixNum; ind++ ) {\n"
                + "        x = max(0.0, min(1.0, _uv.s-ind*pixSize.s)); \n"
                + "        y = _uv.t; \n"
                + "        left  +=  texture2D(sampler2d,vec2(x,y)); \n"
                + "        x = max(0.0, min(1.0, _uv.s+(ind+2.0)*pixSize.s)); \n"
                + "        y = _uv.t; \n"
                + "        right +=  texture2D(sampler2d,vec2(x,y)); \n"
                + "    } \n"
                + "    temp = left - right; \n"
                + "    if( temp.r < 0.0 ) {\n"
                + "        gray1 += temp.r * temp.r; \n"
                + "    } \n"
                + "    if( temp.g < 0.0 ) {\n"
                + "        gray2 +=temp.g * temp.g; \n"
                + "    } \n"
                + "    if( temp.b < 0.0 ) {\n"
                + "         gray3 +=temp.b * temp.b; \n"
                + "    } \n"
                + "    left  = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "    right = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "    for( ind = 0.0; ind < pixNum; ind++ ) {\n"
                + "        x = max(0.0, min(1.0, _uv.s-(ind*2.0)*pixSize.s)); \n"
                + "        y = _uv.t; \n"
                + "        left  +=  texture2D(sampler2d,vec2(x, y)); \n"
                + "        x = max(0.0, min(1.0, _uv.s+ind*pixSize.s)); \n"
                + "        y = _uv.t; \n"
                + "        right +=  texture2D(sampler2d,vec2(x, y)); \n"
                + "    } \n"
                + "    temp = left - right; \n"
                + "    if( temp.r > 0.0 ) {\n"
                + "         gray1 += temp.r * temp.r ; \n"
                + "    } \n"
                + "    if( temp.g > 0.0 ) {\n"
                + "        gray2 += temp.g * temp.g ; \n"
                + "    } \n"
                + "    if( temp.b > 0.0 ) {\n"
                + "        gray3 += temp.b * temp.b ; \n"
                + "    } \n"
                + "    up   = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "    down = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "    for( ind = 0.0; ind < pixNum; ind++) {\n"
                + "        x = _uv.s; \n"
                + "        y = max(0.0, min(1.0, _uv.t-ind*pixSize.t)); \n"
                + "        up   += texture2D(sampler2d, vec2(x, y)); \n"
                + "        y = max(0.0, min(1.0, _uv.t+(ind+2.0)*pixSize.t)); \n"
                + "        down += texture2D(sampler2d, vec2(x, y)); \n"
                + "     } \n"
                + "     temp = up - down; \n"
                + "     if( temp.r < 0.0 ) {\n"
                + "         gray1 += temp.r * temp.r; \n"
                + "     } \n"
                + "     if( temp.g < 0.0 ) {\n"
                + "         gray2 += temp.g * temp.g; \n"
                + "     } \n"
                + "     if( temp.b < 0.0 ) {\n"
                + "         gray3 += temp.b * temp.b; \n"
                + "     } \n"
                + "     up   = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "     down = vec4( 0.0, 0.0, 0.0, 0.0 ); \n"
                + "     for( ind = 0.0; ind < pixNum; ind++ ) {\n"
                + "         x = _uv.s; \n"
                + "         y = max(0.0, min(1.0, _uv.t - (ind+2.0)*pixSize.t)); \n"
                + "         up   += texture2D(sampler2d, vec2(x, y)); \n"
                + "         y = max(0.0, min(1.0, _uv.t + ind*pixSize.t)); \n"
                + "         down += texture2D(sampler2d, vec2(x, y)); \n"
                + "     } \n"
                + "     temp = up - down; \n"
                + "     if( temp.r > 0.0 ) {\n"
                + "         gray1 += temp.r * temp.r; \n"
                + "     } \n"
                + "     if( temp.g > 0.0 ) {\n"
                + "         gray2 += temp.g *temp.g ; \n"
                + "     } \n"
                + "     if( temp.b > 0.0 ) {\n"
                + "         gray3 += temp.b * temp.b; \n"
                + "     } \n"
                + "     r = texture2D(sampler2d, _uv).r; \n"
                + "     g = texture2D(sampler2d, _uv).g; \n"
                + "     b = texture2D(sampler2d, _uv).b; \n"
                + "     maxG = pixNum; \n"
                + "     if( ( -0.000001 < gray1 / maxG  ) && ( gray1 / maxG  < 0.000001)) {\n"
                + "         gray1  = pow( 0.000001, r); \n"
                + "     } else { \n"
                + "        gray1 = pow( gray1 / maxG, r ); \n"
                + "     } \n"
                + "     if( ( -0.000001 < ( gray2 / maxG) ) && ( ( gray2 / maxG) < 0.000001)) {\n"
                + "         gray2  = pow( 0.000001, g); \n"
                + "     } else { \n"
                + "         gray2 = pow( gray2 / maxG, g ); \n"
                + "     } \n"
                + "     if( ( -0.000001 < gray3 / maxG  ) && ( gray3 / maxG  < 0.000001)) {\n"
                + "         gray3  = pow( 0.000001, b); \n"
                + "     } else {\n"
                + "         gray3 = pow( gray3 / maxG, b ); \n"
                + "     } \n"
                + "     gray1 = 1.0 - gray1; \n"
                + "     gray2 = 1.0 - gray2; \n"
                + "     gray3 = 1.0 - gray3; \n"
                + "     color.r = gray1; \n"
                + "     color.g = gray2; \n"
                + "     color.b = gray3; \n"
                + "     return color;\n"
                + " }\n";
        
        mProcessString = "    texel = calculateGradient(" + TEXTURE_SAMPLER_UNIFORM + ", vTextureCoord," + GRADIENT_PIX_SIZE + ");\n";
        paramMap = new HashMap<String, Integer>();
        mTextureCount = 0;
    }
    
    @Override
    public void loadProcess(String path, String mProcessString) {
        // TODO Auto-generated method stub
        String sub[] = mProcessString.split(",");
        gradientPixSize[0] = Float.valueOf(sub[0]);
        gradientPixSize[1] = Float.valueOf(sub[1]);
    }

    @Override
    public void appendParams(List<ShaderParameter> paramList) {
        // TODO Auto-generated method stub
        int length = paramList.size();
        paramList.add(new UniformShaderParameter(GRADIENT_PIX_SIZE));
        paramMap.put(GRADIENT_PIX_SIZE, length);
    }

    @Override
    public void prepareParams(ShaderParameter[] params, GLCanvas canvas) {
        // TODO Auto-generated method stub
        GLES20.glUniform2f(params[paramMap.get(GRADIENT_PIX_SIZE)].handle, gradientPixSize[0], gradientPixSize[1]);
        FiltersUtil.checkError();
    }

    @Override
    public void jpegProcess() {
        // TODO Auto-generated method stub
        Log.d("dyb_filter", NAME + " jpegProcess");
        ImageProcessNativeInterface.filterGradient(gradientPixSize);
    }

}
