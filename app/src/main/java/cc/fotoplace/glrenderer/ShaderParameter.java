package cc.fotoplace.glrenderer;

public abstract class ShaderParameter {
    public int handle;
    protected final String mName;

    public ShaderParameter(String name) {
        mName = name;
    }

    public abstract void loadHandle(int program);
}
