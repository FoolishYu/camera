package cc.fotoplace.camera.filters;

import android.content.Context;
import android.content.res.Resources;
import android.opengl.GLES20;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FiltersUtil {

    private static final int TUNING_MATRIX_SIZE = 9;

    private static final String TAG_FILTER = "filter";
    private static final String TAG_PACKAGE = "package";
    private static final String TAG_ICON = "icon";
    private static final String TAG_DESCRIPTION = "description";
    private static final String ATTRIBUTE_NAME = "name";
    private static String FILE_PATH = null;
    
    //public static List<FilterData> filterData;
    
    private FiltersUtil() {};

    public static void initialize(Context context) {
        FILE_PATH = context.getFilesDir().getAbsolutePath();
    }
    
    public static boolean isTuningMatrixValid(String matrix) {
        return (matrix != null) && matrix.split(",").length == TUNING_MATRIX_SIZE;
    }

    public static float[] convertTextToFloatArrayt(String matrix) {
        if (matrix == null) {
            Log.e("mk", "The input matrix is null");
            return null;
        }
        String[] sArray = matrix.split(",");
        if (sArray.length != TUNING_MATRIX_SIZE) {
            Log.v("mk", "The length of matrix doesn't equate to 9.");
            return null;
        }
        float[] fArray = new float[TUNING_MATRIX_SIZE];
        for(int i =  0; i < TUNING_MATRIX_SIZE; i++) {
            fArray[i] = Float.valueOf(sArray[i]);
        }
        return fArray;
    }
    
    public static String convertFloatArrayToText(float[] farray) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TUNING_MATRIX_SIZE; i++) {
            sb.append(farray[i]);
            if (i < TUNING_MATRIX_SIZE -1) {
                sb.append(",");
            }
        }
        return sb.toString();  
    }
    
    public static boolean isTuningMatrixValid(float[] farray) {
        return (farray != null && farray.length == TUNING_MATRIX_SIZE);
    }
    
    public static boolean fileExists(String fullPath) {
        File f = new File(fullPath);
        return fileExists(f);
    }

    public static boolean fileExists(File file) {
        return file.exists();
    }
    
    public static void checkError() {
        int error = GLES20.glGetError();
        if (error != 0) {
            Throwable t = new Throwable();
            Log.e("dyb_filter", "GL error: " + error, t);
        }
    }

    public static String[] convertContentToStringArray(String content) {
        return content.split(";");
    }

    public static String convertStringArrayToContent(String[] array) {
        StringBuilder sb = new StringBuilder();
        for (String s : array) {
            sb.append(s).append(";");
        }
        return sb.toString();
    }

    /**
     * @param res
     * @param xmlPath
     * @return
     */
    /**
     * @param res
     * @param xmlPath
     * @return
     */
    public static FilterData parse(Resources res, String xmlPath) {
        FilterData filterData = null;
        String tag = null;
        StringBuilder sb = null;
        InputStream inputStream = null;
        XmlPullParser xmlParser = Xml.newPullParser();
        try {
            File f = new File(xmlPath);
            String parent = f.getParent();
            inputStream = new FileInputStream(f);  
            xmlParser.setInput(inputStream, "utf-8");
            int evtType = xmlParser.getEventType();
            while (evtType != XmlPullParser.END_DOCUMENT) {
                switch (evtType) {
                    case XmlPullParser.START_TAG:
                        tag = xmlParser.getName();
                        if (tag != null) {
                            if (tag.equalsIgnoreCase(TAG_FILTER)) {
                                filterData = new FilterData();
                                filterData.setName(xmlParser.getAttributeValue(null, ATTRIBUTE_NAME));
                            } else if (tag.equalsIgnoreCase(TAG_PACKAGE)) {
                                String pkg = xmlParser.nextText();
                                if (pkg != null && pkg.length() > 0) {
                                    filterData.setPackage(pkg);
                                } else {
                                    Log.e("mk", "The package filed mustn't be null");
                                }
                            } else if (tag.equalsIgnoreCase(TAG_ICON)) {
                                String iconPath = xmlParser.nextText();
                                if (iconPath != null) {
                                    iconPath = parent + "/" + iconPath;
                                    if (FiltersUtil.fileExists(iconPath)) {
                                        filterData.setIconPath(iconPath);
                                    } else {
                                        Log.e("mk", "The filter icon file donesn't exist.");
                                    }
                                } else {
                                    Log.e("mk", "icon path is null");
                                }
                            } else if (tag.equalsIgnoreCase(TAG_DESCRIPTION)) {
                                String description = xmlParser.nextText();
                                if (description != null && description.length() > 0) {
                                    filterData.setDescription(description);
                                } else {
                                    Log.e("mk", "description is empty.");
                                }
                            } else {
                                String key = tag;
                                String value = xmlParser.nextText();
                                if (key != null && value != null) {
                                    if (sb == null) {
                                        sb = new StringBuilder();
                                    } else {
                                        sb.append(";");
                                    }
                                    sb.append(key).append("=").append(value);
                                }
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (sb.length() > 0) {
                            String[] content = sb.toString().split(";");
                            filterData.setContent(content);
                        }
                        break;
                    default:
                        break;
                }
                evtType = xmlParser.next();
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return (filterData != null && filterData.isValid()) ? filterData : null;
    }
    
    public static String[] getKeyValueSet(String str) {
        return str.split("=");
    }
    
    public static String getFilePath() {
        return FILE_PATH;
    }
}
