package de.bugfish.rssreaderadvanced.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import de.bugfish.rssreaderadvanced.R;

/**
 * Privacy screen. The text lives in res/values/strings.xml ({@code privacy_policy})
 * so the developer can edit it without touching this class.
 */
public class PrivacyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}
