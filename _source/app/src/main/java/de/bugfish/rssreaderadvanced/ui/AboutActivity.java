package de.bugfish.rssreaderadvanced.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import de.bugfish.rssreaderadvanced.R;

/**
 * Simple about screen. The developer's own details live in
 * res/values/strings.xml (about_developer_* and about_app_tagline) and can be
 * edited freely without touching this class.
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView version = findViewById(R.id.version);
        version.setText(getString(R.string.about_version, appVersion()));
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }
}
