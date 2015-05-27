package cc.fotoplace.glrenderer;

import android.opengl.GLES20;

public class UniformShaderParameter extends ShaderParameter {
    public UniformShaderParameter(String name) {
        super(name);
    }

    @Override
    public void loadHandle(int program) {
        handle = GLES20.glGetUniformLocation(program, mName);
    }
}
