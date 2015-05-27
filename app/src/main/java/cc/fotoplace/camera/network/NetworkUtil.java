package cc.fotoplace.camera.network;

public class NetworkUtil {

    //public static final String URL_BARCODE = "http://10.125.198.146/camera/bc.htm";
    public static final String URL_BARCODE = "http://cameraapi.yunos.com/camera/bc.htm";

    private static final String EQUAL = "=";
    private static final String AND = "&";
    private static final String QUESTION = "?";

    private static final String KEY_CONTENT = "scanContent";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_NETWORKTYPE = "networkType";
    private static final String KEY_CODETYPE = "codeType";

    public static String getPostData(String scanResult, double longitude, double latitude, String uuid, String networkType, String codeType) {
        StringBuilder sb = new StringBuilder();
        sb.append(KEY_CONTENT).append(EQUAL).append(scanResult).append(AND);
        sb.append(KEY_LONGITUDE).append(EQUAL).append(longitude).append(AND);
        sb.append(KEY_LATITUDE).append(EQUAL).append(latitude).append(AND);
        sb.append(KEY_UUID).append(EQUAL).append(uuid).append(AND);
        sb.append(KEY_NETWORKTYPE).append(EQUAL).append(networkType).append(AND);
        sb.append(KEY_CODETYPE).append(EQUAL).append(codeType).append(AND);
        return sb.toString();
    }

    public static String getBarcodeGetUrl(String scanResult, double longitude, double latitude, String uuid, String networkType, String codeType) {
        StringBuilder sb = new StringBuilder();
        sb.append(URL_BARCODE).append(QUESTION);
        sb.append(KEY_CONTENT).append(EQUAL).append(scanResult).append(AND);
        sb.append(KEY_LONGITUDE).append(EQUAL).append(longitude).append(AND);
        sb.append(KEY_LATITUDE).append(EQUAL).append(latitude).append(AND);
        sb.append(KEY_UUID).append(EQUAL).append(uuid).append(AND);
        sb.append(KEY_NETWORKTYPE).append(EQUAL).append(networkType).append(AND);
        sb.append(KEY_CODETYPE).append(EQUAL).append(codeType).append(AND);
        return sb.toString();
    }

    public static String getShareUrl(String scanContent) {
        StringBuilder sb = new StringBuilder();
        sb.append(URL_BARCODE).append(QUESTION).append(KEY_CONTENT).append(EQUAL).append(scanContent);
        return sb.toString();
    }
}
