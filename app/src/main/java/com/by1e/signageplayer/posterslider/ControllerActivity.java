package com.by1e.signageplayer.posterslider;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class ControllerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent activityIntent;

        // go straight to main if a token is stored
        if (isLoggedIn()) {
            activityIntent = new Intent(this, MainActivity.class);
        } else {
            activityIntent = new Intent(this, LoginActivity.class);
        }

        startActivity(activityIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
    private boolean isLoggedIn(){
        SharedPreferences prefs = getSharedPreferences(getString(R.string.sharedPrefsFile), MODE_PRIVATE);
        boolean loginStatus = prefs.getBoolean("isLoggedIn", false);//"No name defined" is the default value.
        return loginStatus;
    }
}

