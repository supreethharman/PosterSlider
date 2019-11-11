package com.by1e.signageplayer.posterslider;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String serverKey = "qwerty";
    private static final int MY_PERMISSIONS_REQUEST = 9;
    static String storeId;
    private static String android_id;
    private String serverAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);
        /*btnConnect = (Button)findViewById(R.id.btn_connect);
        storeId = (EditText) findViewById(R.id.input_storeId);
        serverAddress = (EditText) findViewById(R.id.input_serverAddress);
        serverKey = (EditText) findViewById(R.id.input_serverKey);*/


        if (isAllPermissionGranted()) {
            doRegisterOfUser();
        }
    }

    private void doRegisterOfUser() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            storeId = tm.getDeviceId();
            serverAddress = getString(R.string.serverIpAddress);
            AsyncTaskRunner runner = new AsyncTaskRunner();
            runner.execute(storeId, serverAddress, serverKey);
        } catch (SecurityException | NullPointerException ex) {
            Log.e(LoginActivity.class.getName(), ex.getLocalizedMessage());
        }
    }

    public boolean isAllPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                    isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) {
                return true;
            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE
                }, MY_PERMISSIONS_REQUEST);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }

    @RequiresApi (api = Build.VERSION_CODES.M)
    private boolean isPermissionGranted(String permission) {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doRegisterOfUser();
            } else {
                Toast.makeText(this, "Permissions are not provided", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }


    private class AsyncTaskRunner extends AsyncTask<String, String, String> {

        ProgressDialog progressDialog;
        String storeIdVal;
        String serverAddressVal;
        String serverKeyVal;
        private String resp;

        @Override
        protected String doInBackground(String... params) {
            publishProgress("Sleeping..."); // Calls onProgressUpdate()

            try {
                storeIdVal = params[0];
                serverAddressVal = params[1];
                serverKeyVal = params[2];
                String uniqueIdentifier = "android" + "-" + storeIdVal;

                JSONObject requestData = new JSONObject();
                requestData.put("serverKey", serverKeyVal);
                requestData.put("hardwareKey", uniqueIdentifier);
                requestData.put("displayName", uniqueIdentifier);
                requestData.put("clientType", "AndroidDisplay");
                requestData.put("clientVersion", Integer.toString(BuildConfig.VERSION_CODE));
                requestData.put("clientCode", 1);
                requestData.put("operatingSystem", "v7.1.1");

                requestData.put("serverAddress", serverAddressVal);

                SharedPreferences sharedPref = getSharedPreferences(getString(R.string.sharedPrefsFile), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(getString(R.string.hardwareKey), uniqueIdentifier);
                editor.putString(getString(R.string.displayName), uniqueIdentifier);
                editor.putString(getString(R.string.storeId), storeIdVal);
                editor.putString(getString(R.string.serverAddress), serverAddressVal);
                editor.putString(getString(R.string.serverKey), serverKeyVal);
                editor.apply();

                if (makePostRequest(serverAddressVal, requestData.toString())) {
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
            URL url = new URL(stringUrl + "/rd");
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
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean(getString(R.string.isLoggedIn), true);
                    editor.apply();
                    uc.disconnect();
                    return true;
                }
//                BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
//                while((line = br.readLine()) != null){
//                    jsonString.append(line);
//                }

//                br.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            uc.disconnect();

            return false;
        }


        @Override
        protected void onPostExecute(String result) {
            // execution of result of Long time consuming operation
            progressDialog.dismiss();
            if (result.equalsIgnoreCase("Successful")) {
                Intent activityIntent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(activityIntent);
                finish();
            } else  {
                Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
            }
        }


        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(LoginActivity.this,
                    "Registering the device",
                    "Please wait for few seconds");
        }


        @Override
        protected void onProgressUpdate(String... text) {
            Toast.makeText(getApplicationContext(), "In Progress!", Toast.LENGTH_LONG).show();

        }
    }
}