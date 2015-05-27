package cc.fotoplace.glrenderer;

import android.opengl.GLES20;

public class AttributeShaderParameter extends ShaderParameter {
    public AttributeShaderParameter(String name) {
        super(name);
    }

    @Override
    public void loadHandle(int program) {
        handle = GLES20.glGetAttribLocation(program, mName);
    }
}
