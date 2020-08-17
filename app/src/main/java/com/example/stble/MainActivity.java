package com.example.stble;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button mClientButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mClientButton = findViewById(R.id.client_button);

        mClientButton.setOnClickListener(view -> {
            Intent ClientActivity = new Intent(MainActivity.this, ScanActivity.class);
            startActivity(ClientActivity);
        });
    }


}