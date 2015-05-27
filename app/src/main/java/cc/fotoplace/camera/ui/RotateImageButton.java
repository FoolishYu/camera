package cc.fotoplace.camera.ui;

import android.content.Context;
import android.util.AttributeSet;

public class RotateImageButton extends RotateImageView {

    public RotateImageButton(Context context) {
        super(context);
    }

    public RotateImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    protected void drawableStateChanged() {
        if (isPressed()) {
            setImageAlpha(128);
        } else {
            setImageAlpha(255);
        }
        super.drawableStateChanged();
    }

}
