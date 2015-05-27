package cc.fotoplace.camera.debug;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Camera.Parameters;
import android.view.ViewGroup.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import cc.fotoplace.camera.ui.CameraControls;

import java.util.HashSet;

public class CameraDebug {

    public static final boolean debuggable = true;

    public static String getReadableParmas(Parameters params){
        String paramStr = params.flatten();
        String[] paramItems = paramStr.split(";");
        HashSet<String> set = new HashSet<String>();
        for(String paramItem: paramItems){
            String[] keyValue=paramItem.split("=");
            if(keyValue.length == 2){
                set.add(keyValue[0]);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("========= Camera Parameters ========\n");
        for (String  key : set) {
            sb.append(key).append("=").append(params.get(key)).append("\n");
        }
        sb.append("====================================\n");
        return sb.toString();
    }

    public static void addParametersView(Context context, CameraControls controls) {
        if (debuggable) {
            ScrollView scrooView = new ScrollView(context);
            scrooView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            
            TextView tv = new TextView(context);
            tv.setId(100000);
            tv.setTextSize(5.5f);
            tv.setTextColor(Color.WHITE);
            tv.setMarqueeRepeatLimit(-1);
            tv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            
            scrooView.addView(tv);
            controls.addView(scrooView);
        }
    }
    
    public static void setText(Activity activity, String text) {
        if (debuggable) {
            TextView tv = (TextView) activity.findViewById(100000);
            tv.setText(text);
        }
    }
}
