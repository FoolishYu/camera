
package cc.fotoplace.camera;

import android.content.Context;
import android.util.AttributeSet;

import java.util.ArrayList;

/**
 * A collection of <code>CameraPreference</code>s. It may contain other
 * <code>PreferenceGroup</code> and form a tree structure.
 */
public class PreferenceGroup extends CameraPreference {
    private ArrayList<CameraPreference> list =
            new ArrayList<CameraPreference>();

    public PreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void addChild(CameraPreference child) {
        list.add(child);
    }

    public void removePreference(int index) {
        list.remove(index);
    }

    public CameraPreference get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }

    @Override
    public void reloadValue() {
        for (CameraPreference pref : list) {
            pref.reloadValue();
        }
    }

    /**
     * Finds the preference with the given key recursively. Returns
     * <code>null</code> if cannot find.
     */
    public ListPreference findPreference(String key) {
        // Find a leaf preference with the given key. Currently, the base
        // type of all "leaf" preference is "ListPreference". If we add some
        // other types later, we need to change the code.
        for (CameraPreference pref : list) {
            if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                if (listPref.getKey().equals(key))
                    return listPref;
            } else if (pref instanceof PreferenceGroup) {
                ListPreference listPref =
                        ((PreferenceGroup) pref).findPreference(key);
                if (listPref != null)
                    return listPref;
            }
        }
        return null;
    }
}
