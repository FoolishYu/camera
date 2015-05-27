
package cc.fotoplace.camera;

import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/** A {@code ListPreference} where each entry has a corresponding icon. */
public class IconListPreference extends ListPreference {
    private int mSingleIconId;
    private int mIconIds[];
    private int mLargeIconIds[];
    private int mImageIds[];
    private boolean mUseSingleIcon;

    public IconListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.IconListPreference, 0, 0);
        Resources res = context.getResources();
        mSingleIconId = a.getResourceId(
                R.styleable.IconListPreference_singleIcon, 0);
        mIconIds = getIds(res, a.getResourceId(
                R.styleable.IconListPreference_icons, 0));
        mLargeIconIds = getIds(res, a.getResourceId(
                R.styleable.IconListPreference_largeIcons, 0));
        mImageIds = getIds(res, a.getResourceId(
                R.styleable.IconListPreference_images, 0));
        a.recycle();
    }

    public int getSingleIcon() {
        return mSingleIconId;
    }

    public int[] getIconIds() {
        return mIconIds;
    }

    public int[] getLargeIconIds() {
        return mLargeIconIds;
    }

    public int[] getImageIds() {
        return mImageIds;
    }

    public boolean getUseSingleIcon() {
        return mUseSingleIcon;
    }

    public void setIconIds(int[] iconIds) {
        mIconIds = iconIds;
    }

    public void setLargeIconIds(int[] largeIconIds) {
        mLargeIconIds = largeIconIds;
    }

    public void setUseSingleIcon(boolean useSingle) {
        mUseSingleIcon = useSingle;
    }

    private int[] getIds(Resources res, int iconsRes) {
        if (iconsRes == 0)
            return null;
        TypedArray array = res.obtainTypedArray(iconsRes);
        int n = array.length();
        int ids[] = new int[n];
        for (int i = 0; i < n; ++i) {
            ids[i] = array.getResourceId(i, 0);
        }
        array.recycle();
        return ids;
    }

    @Override
    public void filterUnsupported(List<String> supported) {
        CharSequence entryValues[] = getEntryValues();
        IntArray iconIds = new IntArray();
        IntArray largeIconIds = new IntArray();
        IntArray imageIds = new IntArray();

        for (int i = 0, len = entryValues.length; i < len; i++) {
            if (supported.indexOf(entryValues[i].toString()) >= 0) {
                if (mIconIds != null)
                    iconIds.add(mIconIds[i]);
                if (mLargeIconIds != null)
                    largeIconIds.add(mLargeIconIds[i]);
                if (mImageIds != null)
                    imageIds.add(mImageIds[i]);
            }
        }
        if (mIconIds != null)
            mIconIds = iconIds.toArray(new int[iconIds.size()]);
        if (mLargeIconIds != null) {
            mLargeIconIds = largeIconIds.toArray(new int[largeIconIds.size()]);
        }
        if (mImageIds != null)
            mImageIds = imageIds.toArray(new int[imageIds.size()]);
        super.filterUnsupported(supported);
    }
}
