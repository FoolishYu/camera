package cc.fotoplace.camera.ui;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cc.fotoplace.camera.R;
import cc.fotoplace.camera.filters.FilterData;
import cc.fotoplace.camera.filters.FiltersManager;

public class EffectPanel extends HorizontalScrollView{

    private Context mContext;
    private LayoutInflater mInflator;
    private LinearLayout mContainer;

    private List<FilterData> mFiltersData;
    private EffectListener mListener;
    private int mSelected = 0;

    private int mSelectedColor;
    private int mUnselectedColor;

    private String mCurrentFilterName;

    public interface EffectListener {
        public void setFilter(int index, FilterData filterData);
        public void onFilterChanged();
        public void launchFilterStore();
    }

    public EffectPanel(Context context) {
        super(context);
        init(context);
    }

    public EffectPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EffectPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mInflator = ((Activity) mContext).getLayoutInflater();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContainer = (LinearLayout) findViewById(R.id.photo_effects_container);
        mSelectedColor = mContext.getResources().getColor(R.color.effect_item_text_selected_color);
        mUnselectedColor = Color.WHITE;
    }

    private void addNormalItem() {
        final LinearLayout normalItem = (LinearLayout) mInflator.inflate(
                R.layout.photo_effect_item, this, false);
        normalItem.setTag(Integer.valueOf(0));
        // Icon
        ImageView normalIcon = (ImageView) normalItem.findViewById(R.id.icon);
        normalIcon.setBackgroundResource(R.drawable.photo_filter_normal);
        // Name
        TextView normalText = (TextView) normalItem.findViewById(R.id.text);
        //if (isZh()) {
            normalText.setText(R.string.filter_normal);
        //}
        normalItem.setOnClickListener(mOnItemClickedListener);
        mContainer.addView(normalItem);
    }

    private void addPresetItems() {
        int length = mFiltersData.size();
        for (int i = 0; i < length; i++) {
            FilterData data = mFiltersData.get(i);
            final LinearLayout item = (LinearLayout) mInflator.inflate(
                    R.layout.photo_effect_item, this, false);
            item.setTag(Integer.valueOf(i+1));
            // Icon
            ImageView icon = (ImageView) item.findViewById(R.id.icon);
            String path = data.getIconPath();
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) {
                continue;
            } else {
                bitmap.recycle();
            }
            BitmapDrawable iconDrawable = new BitmapDrawable(mContext.getResources(), path);
            icon.setBackground(iconDrawable);
            // Name
            TextView text = (TextView) item.findViewById(R.id.text);
            String name = data.getName();
            //if (isZh()) {
                text.setText(name);
            //}
            item.setOnClickListener(mOnItemClickedListener);
            mContainer.addView(item);
        }
    }

    //private boolean isZh() {
    //    Locale locale = getResources().getConfiguration().locale;
    //    String language = locale.getLanguage();
    //    if (language.endsWith("zh"))
    //        return true;
    //    else
    //        return false;
    //}

    private void addFilterStoreItem() {
        final LinearLayout item = (LinearLayout) mInflator.inflate(
                R.layout.photo_effect_item, this, false);
        item.setTag(Integer.valueOf(mFiltersData.size() + 2));
        // Icon
        ImageView icon = (ImageView) item.findViewById(R.id.icon);
        icon.setImageResource(R.drawable.photo_filter_store);
        // Name
        TextView text = (TextView) item.findViewById(R.id.text);
        text.setText(R.string.store_title);
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.launchFilterStore();
            }
        });
        mContainer.addView(item);
    }

    public boolean initializeEffectsPanel(EffectListener listener) {
        mListener = listener;
        mContainer.removeAllViews();
        mFiltersData = FiltersManager.getFilterData();
        if (mFiltersData != null && mFiltersData.size() > 0) {
            // add the "no-filter" item
            addNormalItem();
            // add the "pre-set" items
            addPresetItems();
            // addFilterStoreItem
            addFilterStoreItem();
        } else {
            return false;
        }
        // Recover the selected effect item
        setSelectedEffect(mSelected);
        return true;
    }

    private void setSelectedEffect(int index) {
        LinearLayout item = (LinearLayout) mContainer.getChildAt(mSelected);
        TextView tv = (TextView) item.findViewById(R.id.text);
        tv.setTextColor(mSelectedColor);
        ImageView iv = (ImageView) item.findViewById(R.id.icon);
        iv.setImageResource(R.drawable.photo_filter_focuse);
    }

    public void updateEffectList() {
        mContainer.removeAllViews();

        mFiltersData = FiltersManager.getFilterData();
        if (mFiltersData != null && mFiltersData.size() > 0) {
            // add the "no-filter" item
            addNormalItem();
            // add the "pre-set" items
            addPresetItems();
            // addFilterStoreItem
            addFilterStoreItem();
        }
        LinearLayout item = (LinearLayout) mContainer.getChildAt(mSelected);
        TextView tv = (TextView) item.findViewById(R.id.text);
        tv.setTextColor(mSelectedColor);
        ImageView iv = (ImageView) item.findViewById(R.id.icon);
        iv.setImageResource(R.drawable.photo_filter_focuse);
    }

    public void clearPanel() {
    }

    private View.OnClickListener mOnItemClickedListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            TextView tv = null;
            ImageView iv = null;
            // cancel the previous selection
            LinearLayout item = (LinearLayout) mContainer.getChildAt(mSelected);
            tv = (TextView) item.findViewById(R.id.text);
            tv.setTextColor(mUnselectedColor);
            iv = (ImageView) item.findViewById(R.id.icon);
            iv.setImageBitmap(null);
            // set current view selected
            tv = (TextView) v.findViewById(R.id.text);
            tv.setTextColor(mSelectedColor);
            iv = (ImageView) v.findViewById(R.id.icon);
            iv.setImageResource(R.drawable.photo_filter_focuse);
            mCurrentFilterName = (String) tv.getText();

            mSelected = (Integer) v.getTag();
            if (mSelected > 0) {
                mListener.setFilter(mSelected, mFiltersData.get(mSelected - 1));
            } else {
                mListener.setFilter(0, null);
            }
            mListener.onFilterChanged();
        }
    };

    public boolean inEffectMode() {
        return (mSelected != 0);
    }

    public String getCurrentFilterName() {
        return mCurrentFilterName;
    }

}
