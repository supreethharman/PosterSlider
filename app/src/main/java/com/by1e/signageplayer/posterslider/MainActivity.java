package com.by1e.signageplayer.posterslider;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.asura.library.posters.Poster;
import com.asura.library.posters.RemoteImage;
import com.asura.library.posters.RemoteVideo;
import com.asura.library.views.PosterSlider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    String targetDir = Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_DOWNLOADS;
    BroadcastReceiver onNotificationClick = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            Toast.makeText(ctxt, "Ummmm...hi!", Toast.LENGTH_LONG).show();
        }
    };
    ScheduledExecutorService scheduleTaskExecutor;
    Timer timerAsync;
    private PosterSlider posterSlider;
    private TextView emptyTextView;
    private DownloadManager mgr = null;
    private ProgressDialog mProgressDialog;
    private long downloadId;
    private CacheHelper ch = CacheHelper.getInstance();
    private String ServerAddress;
    private String HardwareKey;
    private String LatestAssetFile;
    private boolean LOADED_FILES = false;
    private boolean IS_DOWNLOADING = false;
    BroadcastReceiver onComplete = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            IS_DOWNLOADING = false;
            try {
                unzip(new File(Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_DOWNLOADS + "/cache/" + LatestAssetFile), new File(targetDir + "/gallery/"));
                loadFiles();
                restartApp();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Handler scheduleHandler;
    private Runnable apiRunnable;

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[1024];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isStoragePermissionGranted();

        scheduleHandler = new Handler();
        mgr = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        posterSlider = findViewById(R.id.poster_slider);
        emptyTextView = findViewById(R.id.emptyTextView);
        emptyTextView.setText(R.string.no_files);

        //Removed default slider initialization
        /*List<Poster> posters = new ArrayList<>();
        posters.add(new DrawableImage(R.drawable.logo));
        posterSlider.setPosters(posters);*/
        scheduleTaskExecutor = Executors.newScheduledThreadPool(5);
        timerAsync = new Timer();

        if (isNetworkAvailable()) {
            SharedPreferences prefs = getSharedPreferences(getString(R.string.sharedPrefsFile), MODE_PRIVATE);
            ServerAddress = prefs.getString(getString(R.string.serverAddress), "http://13.232.40.50");
            checkForUpdates();
        } else {
            loadFiles();
        }

    }

    private void checkForUpdates() {
        //TODO: keep timer async task as backup
        /*TimerTask timerTaskAsync = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Log.d("repeat","after each 10 sec");
                    }
                });
            }
        };
        timerAsync.schedule(timerTaskAsync, 0, 10000);*/
        apiRunnable = new Runnable() {
            @Override
            public void run() {
                if (isNetworkAvailable()) {
                    Log.d("@@@checkForUpdates@@@", "Network will be made");
                    MainActivity.DownloadAssets runner = new MainActivity.DownloadAssets();
                    runner.execute(ServerAddress + "/lf");
                } else {
                    Log.d("@@@checkForUpdates@@@", "No Network");
                }
            }
        };

        scheduleTaskExecutor.scheduleAtFixedRate(apiRunnable, 0, 60, TimeUnit.SECONDS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(onComplete,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        registerReceiver(onNotificationClick,
                new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        Log.i("MainActivity_Signage", "Activity destroyed");
        unregisterReceiver(onComplete);
        unregisterReceiver(onNotificationClick);
        super.onDestroy();
    }

    protected boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }

    public void startDownload(String url) {
        Uri uri = Uri.parse(url);

        Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .mkdirs();

        downloadId = mgr.enqueue(new DownloadManager.Request(uri)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI |
                        DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setTitle(CacheHelper.getInstance().getLocalFileNameForUrl(url))
                .setDestinationInExternalPublicDir(CacheHelper.CacheDirectory,
                        CacheHelper.getInstance().getLocalFileNameForUrl(url)));
        IS_DOWNLOADING = true;
        File targetFile = new File(targetDir + "/gallery/");
        File sourceZip = new File(Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_DOWNLOADS + "/cache/" + LatestAssetFile);
        deleteDir(targetFile);
        deleteFile(sourceZip);
        deleteFilesWithPrefix(new File(targetDir + "/cache/"), HardwareKey);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Downloading latest image assets");
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(true);
        mProgressDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean downloading = true;
                while (downloading) {
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(downloadId);
                    Cursor cursor = mgr.query(q);
                    cursor.moveToFirst();
                    final int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                    }
//                    final double dlProgress = (int) ((bytesDownloaded * 100l) / bytesTotal);
                    final String msg = "Downloading data:\n\n" + ((double) bytesDownloaded) / 1000000.0 + " MB of " + +((double) bytesTotal) / 1000000.0 + " MB";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mProgressDialog.setMessage(msg);
                        }
                    });
                    cursor.close();

                }
            }
        }).start();
    }

    private void restartApp() {
        Intent mStartActivity = new Intent(getApplicationContext(), ControllerActivity.class);
        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(getApplicationContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }

    private String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(path));
        try {
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        } catch (NullPointerException e) {
            Log.e("isNetworkAvailable()", e.getLocalizedMessage());
            return false;
        }
    }

    private void loadFiles() {
        String path = targetDir + "/gallery/";
        File directory = new File(path);
//        if (directory.isDirectory()) {
//            Toast.makeText(getApplicationContext(),"Directory does not exist, please turn on the internet and download again!", Toast.LENGTH_LONG).show();
//            return ;
//        }
        File[] files = directory.listFiles();
        if (files == null) {
            Toast.makeText(getApplicationContext(), "No files", Toast.LENGTH_LONG).show();
            posterSlider.setVisibility(View.GONE);
            return;
        } else {
            Arrays.sort(files);
            List<Poster> posters = new ArrayList<>();
            for (int i = 0; i < files.length; i++) {
                String extension = ch.getFileExt(files[i].getName());
                if (ch.getImageExtensions().contains(extension)) {
                    posters.add(new RemoteImage("file:///" + files[i].getAbsolutePath()));
                }
                if (extension.equals("mp4")) {
                    System.out.println("file:///" + files[i].getAbsolutePath());
                    posters.add(new RemoteVideo(Uri.parse("file:///" + files[i].getAbsolutePath())));
                }

            }
            posterSlider.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);
            posterSlider.setPosters(posters);
        }
        LOADED_FILES = true;
    }


    public boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    public boolean deleteFile(File file) {
        if (file.exists())
            return file.delete();
        return false;
    }

    public void deleteFilesWithPrefix(File dir, String prefix) {
        for (File f : dir.listFiles()) {
            if (f.getName().startsWith(prefix)) {
                deleteFile(f);
            }
        }
    }

    @Override
    protected void onStop() {
        scheduleHandler.removeCallbacks(apiRunnable);
        super.onStop();
    }

    private class DownloadAssets extends AsyncTask<String, String, String> {

        private String resp;
//        ProgressDialog progressDialog;


        @Override
        protected String doInBackground(String... params) {
            publishProgress("Sleeping..."); // Calls onProgressUpdate()

            try {
                String url = params[0];

                SharedPreferences prefs = getSharedPreferences(getString(R.string.sharedPrefsFile), MODE_PRIVATE);
                String ServerKey = prefs.getString(getString(R.string.serverKey), null);
                String StoreID = prefs.getString((getString(R.string.storeId)), null);
                HardwareKey = prefs.getString(getString(R.string.hardwareKey), null);
                String uniqueIdentifier = "android" + "-" + StoreID;

                JSONObject requestData = new JSONObject();
                requestData.put("serverKey", ServerKey);
                requestData.put("hardwareKey", HardwareKey);
                requestData.put("displayName", uniqueIdentifier);
                requestData.put("clientType", "AndroidDisplay");
                requestData.put("clientVersion", Integer.toString(BuildConfig.VERSION_CODE));
                requestData.put("clientCode", 1);
                requestData.put("operatingSystem", "v7.1.1");
                requestData.put("serverAddress", ServerAddress);


                if (makePostRequest(url, requestData.toString())) {
                    resp = "Successful";
                } else {
                    resp = "Failed";
                }

            } catch (Exception e) {
                e.printStackTrace();
                resp = e.getMessage();
            }
            return resp;
        }

        public boolean makePostRequest(String stringUrl, String payload) throws IOException {
            URL url = new URL(stringUrl);
            HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            System.out.println(payload);
            uc.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            uc.setRequestMethod("POST");
            uc.setDoInput(true);
            uc.setInstanceFollowRedirects(false);
            uc.connect();
            OutputStreamWriter writer = new OutputStreamWriter(uc.getOutputStream(), "UTF-8");
            writer.write(payload);
            writer.close();
            try {
                if (uc.getResponseCode() == 200) {
                    SharedPreferences sharedPref = getSharedPreferences(getString(R.string.sharedPrefsFile), Context.MODE_PRIVATE);
                    LatestAssetFile = sharedPref.getString(getString(R.string.latestAssetFile), null);
                    BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
                    final String NewAssetFile = br.readLine();
                    br.close();
                    //System.out.println("File is new "+LatestAssetFile.equals(NewAssetFile));
                    if (LatestAssetFile == null) {
                        LatestAssetFile = NewAssetFile;
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putString(getString(R.string.latestAssetFile), NewAssetFile);
                        editor.apply();
                        //startDownload(ServerAddress+"/"+LatestAssetFile);
                        System.out.println("first else");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                System.out.println(NewAssetFile);
                                startDownload("http://13.232.40.50:8080/" + LatestAssetFile);
                            }
                        });
                    } else if (NewAssetFile != null && NewAssetFile != "" && NewAssetFile.length() > 0 && !LatestAssetFile.equals(NewAssetFile)) {
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putString(getString(R.string.latestAssetFile), NewAssetFile);
                        LatestAssetFile = NewAssetFile;
                        editor.apply();
                        System.out.println("second else");
                        //startDownload(ServerAddress+"/"+LatestAssetFile);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                startDownload("http://13.232.40.50:8080/" + LatestAssetFile);
                            }
                        });


                    } else if (!LOADED_FILES && !IS_DOWNLOADING) {
                        //unzip(new File(Environment.getExternalStorageDirectory()+File.separator+Environment.DIRECTORY_DOWNLOADS+"/cache/"+LatestAssetFile),new File(targetDir+"/gallery/"));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //Toast.makeText(getApplicationContext(), "Loading files", Toast.LENGTH_LONG).show();
                                loadFiles();

                            }
                        });
                    }

                    uc.disconnect();
                    return true;
                }


            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                uc.disconnect();
            }
            return false;
        }


        @Override
        protected void onPostExecute(String result) {
            // execution of result of Long time consuming operation
//            progressDialog.dismiss();
//            Toast.makeText(getApplicationContext(), result , Toast.LENGTH_LONG).show();
        }


        @Override
        protected void onPreExecute() {
//            progressDialog = ProgressDialog.show(MainActivity.this,
//                    "Registering the device",
//                    "Please wait for few seconds");
        }


        @Override
        protected void onProgressUpdate(String... text) {
//            Toast.makeText(getApplicationContext(), "In Progress!", Toast.LENGTH_LONG).show();

        }
    }


}
