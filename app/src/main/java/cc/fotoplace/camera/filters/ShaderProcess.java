package cc.fotoplace.camera.filters;

import android.opengl.GLES20;
import android.util.Log;


public class ShaderProcess {

    private static final String ALPHA_UNIFORM = "uAlpha";

    private static final String TEXTURE_SAMPLER_UNIFORM = "uTextureSampler";

    private static final String POSITION_ATTRIBUTE = "aPosition";
    private static final String MATRIX_UNIFORM = "uMatrix";
    private static final String TEXTURE_MATRIX_UNIFORM = "uTextureMatrix";

    ShaderParameter[] mFilterParameters = {
            new AttributeShaderParameter(POSITION_ATTRIBUTE), // INDEX_POSITION
            new UniformShaderParameter(MATRIX_UNIFORM), // INDEX_MATRIX
            new UniformShaderParameter(TEXTURE_MATRIX_UNIFORM), // INDEX_TEXTURE_MATRIX
            new UniformShaderParameter(TEXTURE_SAMPLER_UNIFORM), // INDEX_TEXTURE_SAMPLER
            new UniformShaderParameter(ALPHA_UNIFORM), // INDEX_ALPHA
    // new UniformShaderParameter("delta"), //INDEX_DELTA
    };

    private static final String FILTER_FRAGMENT_SHADER = ""
            + "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "varying vec2 vTextureCoord;\n"
            + "uniform float " + ALPHA_UNIFORM + ";\n"
            + "uniform samplerExternalOES " + TEXTURE_SAMPLER_UNIFORM + ";\n"
            
//            + "uniform uFilterMatrix;\n"
            
            + "void main() {\n"
            + "  vec3 gMonoMult1 = vec3(0.299, 0.587, 0.114);"
            + "  gl_FragColor = texture2D(" + TEXTURE_SAMPLER_UNIFORM + ", vTextureCoord);\n"
            + "  gl_FragColor *= " + ALPHA_UNIFORM + ";\n"
            + "  gl_FragColor.r = dot(gMonoMult1, gl_FragColor.rgb);\n"
//            + "  gl_FragColor.r = gl_FragColor.r * 0.299 + gl_FragColor.g * 0.587 + gl_FragColor.b * 0.114;\n"
            + "  gl_FragColor.g = gl_FragColor.r;\n"
            + "  gl_FragColor.b = gl_FragColor.r;\n"
            + "}\n";

    private static final String TAG = null;

    private abstract static class ShaderParameter {
        public int handle;
        protected final String mName;

        public ShaderParameter(String name) {
            mName = name;
        }

        public abstract void loadHandle(int program);
    }

    private static class UniformShaderParameter extends ShaderParameter {
        public UniformShaderParameter(String name) {
            super(name);
        }

        @Override
        public void loadHandle(int program) {
            handle = GLES20.glGetUniformLocation(program, mName);
            checkError();
        }
    }

    private static class AttributeShaderParameter extends ShaderParameter {
        public AttributeShaderParameter(String name) {
            super(name);
        }

        @Override
        public void loadHandle(int program) {
            handle = GLES20.glGetAttribLocation(program, mName);
            checkError();
        }
    }

    public static void checkError() {
        int error = GLES20.glGetError();
        if (error != 0) {
            Throwable t = new Throwable();
            Log.e(TAG, "GL error: " + error, t);
        }
    }

}
