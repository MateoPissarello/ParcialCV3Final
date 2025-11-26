package com.example.parcial;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        Button btnCoins = findViewById(R.id.btnCoins);
        Button btnFace = findViewById(R.id.btnFace);

        btnCoins.setOnClickListener(v -> startActivity(new Intent(this, CoinDetectionActivity.class)));
        btnFace.setOnClickListener(v -> startActivity(new Intent(this, FaceDetectionActivity.class)));
    }
}
