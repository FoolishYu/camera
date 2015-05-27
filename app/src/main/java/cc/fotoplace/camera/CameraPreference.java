
package cc.fotoplace.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

/**
 * The base class of all Preferences used in Camera. The preferences can be
 * loaded from XML resource by <code>PreferenceInflater</code>.
 */
public abstract class CameraPreference {

    private String mTitle;
    private SharedPreferences mSharedPreferences;
    private final Context mContext;

    static public interface OnPreferenceChangedListener {
        public void onSharedPreferenceChanged();
        public void onRestorePreferencesClicked();
        public void onOverriddenPreferencesClicked();
        public void onCameraPickerClicked(int cameraId);
        public void onCameraFlashModeClicked(int modeId);
    }

    public CameraPreference(Context context, AttributeSet attrs) {
        mContext = context;
    }

    public String getTitle() {
        mTitle = "";
        return mTitle;
    }

    public SharedPreferences getSharedPreferences() {
        if (mSharedPreferences == null) {
            mSharedPreferences = ComboPreferences.get(mContext);
        }
        return mSharedPreferences;
    }

    public abstract void reloadValue();
}
