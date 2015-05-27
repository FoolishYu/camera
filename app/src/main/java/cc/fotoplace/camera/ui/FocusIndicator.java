package cc.fotoplace.camera.ui;

public interface FocusIndicator {
    public void showStart(boolean manual);
    public void showSuccess(boolean timeout);
    public void showFail(boolean timeout);
    public void clear();
}
