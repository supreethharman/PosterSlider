package com.by1e.signageplayer.posterslider;

import android.os.Build;
import android.os.Environment;
import android.webkit.WebResourceResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheHelper {
    private static CacheHelper instance;
    private List<String> overridableExtensions = new ArrayList<>(Arrays.asList("mp4","jpg","jpeg","png"));
    private List<String> imageExtensions = new ArrayList<>(Arrays.asList("jpg","jpeg","png"));
    public  static String CacheDirectory = Environment.DIRECTORY_DOWNLOADS+"/cache/";
    public static CacheHelper getInstance(){
        if(instance == null){
            instance = new CacheHelper();
        }
        return instance;
    }

    public List<String> getImageExtensions() { return imageExtensions;    }
    public List<String> getCachedExtensions(){
        return overridableExtensions;
    }

    public String getFileExt(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
    }

    public static WebResourceResponse getWebResourceResponseFromFile(String filePath, String mimeType, String encoding) throws FileNotFoundException {
        File file = new File(filePath);
        FileInputStream fileInputStream = new FileInputStream(file);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int statusCode = 200;
            String reasonPhase = "OK";
            Map<String, String> responseHeaders = new HashMap<String, String>();
            responseHeaders.put("Access-Control-Allow-Origin","*");
            return new WebResourceResponse(mimeType, encoding, statusCode, reasonPhase, responseHeaders, fileInputStream);
        }
        return new WebResourceResponse(mimeType, encoding, fileInputStream);
    }

    public String getLocalFilePath(String url){
        String localFilePath = "";
        String fileNameForUrl = getLocalFileNameForUrl(url);
        if(fileExists(fileNameForUrl)){
            localFilePath = getFileFullPath(fileNameForUrl);
        }
        return localFilePath;
    }

    public boolean fileExists(String fileName){
        String path = Environment
                .getExternalStoragePublicDirectory(CacheDirectory).getAbsolutePath()
                +"/"+ fileName;
        return new File(path).exists();
    }

    public String getFileFullPath(String relativePath){
        return Environment
                .getExternalStoragePublicDirectory(CacheDirectory).getAbsolutePath()
                +"/"+relativePath;
    }



    public String getLocalFileNameForUrl(String url){
        String localFileName = "";
        String[] parts = url.split("/");
        if(parts.length > 0){
            localFileName = parts[parts.length-1];
        }
        return localFileName;
    }

    public String getMimeType(String fileExtension){
        String mimeType = "";
        switch (fileExtension){
            case "css" :
                mimeType = "text/css";
                break;
            case "js" :
                mimeType = "text/javascript";
                break;
            case "mp4" :
                mimeType = "video/mp4";
                break;
            case "png" :
                mimeType = "image/png";
                break;
            case "jpg" :
                mimeType = "image/jpeg";
                break;
            case "ico" :
                mimeType = "image/x-icon";
                break;
            case "woff" :
            case "ttf" :
            case "eot" :
                mimeType = "application/x-font-opentype";
                break;
        }
        return mimeType;
    }

}
