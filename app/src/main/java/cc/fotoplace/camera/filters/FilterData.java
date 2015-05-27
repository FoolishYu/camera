package cc.fotoplace.camera.filters;

public class FilterData {

    private String mName;
    private String mPackage;
    private String mIconPath;
    private String mDescription;
    private String[] mContent;

    public FilterData() {}

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getPackage() {
        return mPackage;
    }

    public void setPackage(String pkg) {
        mPackage = pkg;
    }

    public String getIconPath() {
        return mIconPath;
    }

    public void setIconPath(String path) {
        mIconPath = path;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String descrition) {
        mDescription = descrition;
    }

    public String[] getContent() {
        return mContent;
    }
    
    public void setContent(String[] content) {
        mContent = content;
    }

    public boolean isValid() {
        return mName != null && mIconPath != null && mDescription != null 
                && mPackage !=null && mPackage.length() > 0 
                && mContent != null && mContent.length > 0;
    }

    @Override
    public String toString() {
        if (!isValid()) {
            return "Filter data is invalid";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("======== FilterData =========\n");
        sb.append("Name=").append(mName).append(";\n");
        sb.append("IconPath=").append(mIconPath).append(";\n");
        sb.append("Description=").append(mDescription).append(";\n");
        for (int i = 0; i < mContent.length; i++) {
            sb.append("step " + (i+1) + ": ").append(mContent[i]).append("\n");
        }
        //sb.append("Content=").append(FiltersUtil.convertStringArrayToContent(mContent))
        //        .append("\n");
        sb.append("=============================\n");
        return sb.toString();
    }
}
