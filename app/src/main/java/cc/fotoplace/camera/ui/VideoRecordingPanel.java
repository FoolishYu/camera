package cc.fotoplace.camera.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import cc.fotoplace.camera.R;

public class VideoRecordingPanel extends FrameLayout {
    
    private ImageView mIcon;
    private TextView mText;
    private LinearLayout mTextFrame;
    private RelativeLayout mIconFrame;
    
    private Context mContext;
    private Typeface mTypeface;
    private int mTextOrientation;
    
    private int WIDTH;
    private int HEIGHT;
    private int MARGIN;
    private int TOPDOWN_OFFSET;
    private int ICON_WIDTH;
    private int TEXT_MARGIN;
    private int DOWNTOP_OFFSET;
    
    public VideoRecordingPanel(Context context) {
        super(context);
        init(context);
    }
    
    private  VideoRecordingPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private  VideoRecordingPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }
    
    private void init(Context context) {
        mContext = context;
        Resources resources = mContext.getResources();
        WIDTH = resources.getDimensionPixelSize(R.dimen.video_recording_panel_width);
        HEIGHT = resources.getDimensionPixelSize(R.dimen.video_recording_panel_height);
        MARGIN = resources.getDimensionPixelSize(R.dimen.video_recording_panel_margin);
        TOPDOWN_OFFSET = resources.getDimensionPixelSize(R.dimen.video_recording_panel_topdown_offset);
        DOWNTOP_OFFSET = resources.getDimensionPixelSize(R.dimen.video_recording_panel_downtop_offset);
        ICON_WIDTH = resources.getDimensionPixelSize(R.dimen.video_recording_panel_icon_width);
        TEXT_MARGIN = resources.getDimensionPixelSize(R.dimen.video_recording_panel_text_margin);
    }
    
    public void setTextOrientation(int degree) {
        setPanelLayoutParams(degree);
        addTextView(degree);
        mTextOrientation = degree;
    }

    private void setPanelLayoutParams(int degree) { 
        RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) getLayoutParams();
        switch (degree) {
            case 0:
            case 180:
                params.width = WIDTH;
                params.height = HEIGHT;
                params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                break;
            case 90:
                params.width = HEIGHT;
                params.height = WIDTH;
                params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
                break;
            case 270:
                params.width = HEIGHT;
                params.height = WIDTH;
                params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
                break;
            default:
                throw new IllegalArgumentException("degree should be within [0, 90, 180, 270]");
        }
        params.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);
        setLayoutParams(params);
        setBackgroundResource(R.drawable.camera_record_time_bg);
    }

    private void addTextView(int degree) {
        Log.v("mk", "addTextView(), degree = " + degree);
        LinearLayout.LayoutParams textFrameParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(ICON_WIDTH, ICON_WIDTH);
        switch (degree) {
            case 0:
            case 180:
                // Create Icon RelativeLayout
                iconParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
                mIconFrame = new RelativeLayout(mContext);
                addView(mIconFrame);
                mIcon = new ImageView(mContext);
                mIcon.setLayoutParams(iconParams);
                mIcon.setImageResource(R.drawable.ic_camera_recording_indicator);
                mIcon.setScaleType(ScaleType.CENTER);
                mIconFrame.addView(mIcon);
                // Create Time LinearLayout
                mTextFrame = new LinearLayout(mContext);
                mTextFrame.setLayoutParams(textFrameParams);
                mTextFrame.setGravity(Gravity.CENTER);
                addView(mTextFrame);
                // Create TextView
                mText = new TextView(mContext);
                mText.setLayoutParams(textFrameParams);
                mTextFrame.addView(mText);
                mText.setGravity(Gravity.CENTER);
                mText.setLayoutParams(textFrameParams);
                break;
            case 90:
                // Create Time LinearLayout
                mTextFrame = new LinearLayout(mContext);
                mTextFrame.setLayoutParams(textFrameParams);
                mTextFrame.setGravity(Gravity.CENTER);
                addView(mTextFrame);
                // Create TextView 
                mText = new VerticalTextView(mContext, true);
                textFrameParams.bottomMargin = TEXT_MARGIN;
                mTextFrame.addView(mText);
                mText.setGravity(Gravity.CENTER);
                mText.setLayoutParams(textFrameParams);
                // Create Icon RelativeLayout
                iconParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                mIconFrame = new RelativeLayout(mContext);
                addView(mIconFrame);
                mIcon = new ImageView(mContext);
                mIcon.setLayoutParams(iconParams);
                mIcon.setImageResource(R.drawable.ic_camera_recording_indicator);
                mIcon.setScaleType(ScaleType.CENTER);
                mIconFrame.addView(mIcon);
                break;
            case 270:
                // Create Icon RelativeLayout
                iconParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                mIconFrame = new RelativeLayout(mContext);
                addView(mIconFrame);
                mIcon = new ImageView(mContext);
                mIcon.setLayoutParams(iconParams);
                mIcon.setImageResource(R.drawable.ic_camera_recording_indicator);
                mIcon.setScaleType(ScaleType.CENTER);
                mIconFrame.addView(mIcon);
                // Create Text LinearLayout
                mTextFrame = new LinearLayout(mContext);
                mTextFrame.setLayoutParams(textFrameParams);
                mTextFrame.setGravity(Gravity.CENTER);
                addView(mTextFrame);
                // Create TextView
                mText = new VerticalTextView(mContext, false);
                textFrameParams.topMargin = TEXT_MARGIN;
                mText.setGravity(Gravity.CENTER);
                mText.setLayoutParams(textFrameParams);
                mTextFrame.addView(mText);
                break;
            default:
                throw new IllegalArgumentException("degree should be within [0, 90, 180, 270]");
        }
        mTypeface = Typeface.createFromAsset(mContext.getAssets(), "fonts/camera_font.ttf");
        mText.setTypeface(mTypeface);
        mText.setTextSize(15);
        mText.setTextColor(Color.WHITE);
        mText.setText("00:00:00");
    }
    
    public void setText(String text) {
        mText.setText(text);
    }
    
    public void setTextColor(int color) {
        mText.setTextColor(color);
    }
    
    public void setIconVisibility(int visibility) {
        mIcon.setVisibility(visibility);
    }
    
    class VerticalTextView extends TextView { 
        private boolean topDown = false;
        private Path p = new Path();
        private final int ALIGNMENT_OFFSET_TOPDOWN = TOPDOWN_OFFSET;
        private final int ALIGNMENT_OFFSET_DOWNTOP = DOWNTOP_OFFSET;
        
        public VerticalTextView(Context context, boolean topDown) {
            this(context);
            this.topDown = topDown;
        }
     
        public VerticalTextView(Context context, AttributeSet attrs, int defStyle) { 
            super(context, attrs, defStyle); 
        } 
     
        public VerticalTextView(Context context, AttributeSet attrs) { 
            super(context, attrs); 
        } 
     
        public VerticalTextView(Context context) { 
            super(context); 
        }
        
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
         
        @Override 
        protected void onDraw(Canvas canvas) { 
            final ColorStateList csl = getTextColors(); 
            final int color = csl.getDefaultColor(); 
            final int paddingBottom = getPaddingBottom(); 
            final int paddingTop = getPaddingTop(); 
            final int viewWidth = getWidth(); 
            final int viewHeight = getHeight(); 
            final TextPaint paint = getPaint(); 
            paint.setColor(color); 
            final float bottom = viewWidth * 12.0f / 11.0f; 
            p.reset();
            Log.v("mk" , "topDown=" + topDown);
            if (topDown) {
                p.moveTo(bottom-ALIGNMENT_OFFSET_TOPDOWN, viewHeight - paddingBottom - paddingTop); 
                p.lineTo(bottom-ALIGNMENT_OFFSET_TOPDOWN, paddingTop);
            } else {
                p.moveTo(ALIGNMENT_OFFSET_DOWNTOP, paddingTop); 
                p.lineTo(ALIGNMENT_OFFSET_DOWNTOP, viewHeight - paddingBottom - paddingTop);
            }
            canvas.drawTextOnPath(getText().toString(), p, 0, 0, paint); 
        } 
    }
    
    public int getTextOrientation() {
        return mTextOrientation;
    }
}
