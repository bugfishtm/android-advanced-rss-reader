package de.bugfish.rssreaderadvanced.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.util.Prefs;
import de.bugfish.rssreaderadvanced.work.RefreshScheduler;

public class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;
    private MaterialSwitch storeLocalSwitch;
    private MaterialSwitch showImagesSwitch;
    private MaterialSwitch autoRefreshSwitch;
    private MaterialSwitch pushSwitch;
    private Spinner intervalSpinner;
    private View intervalRow;
    private int[] intervalValues;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    pushSwitch.setChecked(false);
                    prefs.setBackgroundPush(false);
                    Toast.makeText(this, R.string.notif_permission_needed, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        storeLocalSwitch = findViewById(R.id.switch_store_local);
        autoRefreshSwitch = findViewById(R.id.switch_auto_refresh);
        pushSwitch = findViewById(R.id.switch_push);
        intervalSpinner = findViewById(R.id.spinner_interval);
        intervalRow = findViewById(R.id.row_interval);

        storeLocalSwitch.setChecked(prefs.isStoreLocal());
        storeLocalSwitch.setOnCheckedChangeListener((b, checked) -> prefs.setStoreLocal(checked));

        showImagesSwitch = findViewById(R.id.switch_show_images);
        showImagesSwitch.setChecked(prefs.isShowImages());
        showImagesSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                // Enabling remote images can leak the user's IP – confirm first.
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.images_warning_title)
                        .setMessage(R.string.images_warning_message)
                        .setPositiveButton(R.string.images_enable, (d, w) -> prefs.setShowImages(true))
                        .setNegativeButton(R.string.cancel, (d, w) -> b.setChecked(false))
                        .setOnCancelListener(d -> b.setChecked(false))
                        .show();
            } else {
                prefs.setShowImages(false);
            }
        });

        autoRefreshSwitch.setChecked(prefs.isAutoRefresh());
        autoRefreshSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.setAutoRefresh(checked);
            updateAutoRefreshDependents(checked);
            RefreshScheduler.apply(this);
        });

        pushSwitch.setChecked(prefs.isBackgroundPush());
        pushSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.setBackgroundPush(checked);
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        });

        setupIntervalSpinner();
        updateAutoRefreshDependents(prefs.isAutoRefresh());

        findViewById(R.id.row_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
    }

    private void setupIntervalSpinner() {
        intervalValues = getResources().getIntArray(R.array.interval_values);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.interval_labels, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        intervalSpinner.setAdapter(adapter);

        int current = prefs.getRefreshMinutes();
        int index = 2; // default: hourly
        for (int i = 0; i < intervalValues.length; i++) {
            if (intervalValues[i] == current) {
                index = i;
                break;
            }
        }
        intervalSpinner.setSelection(index);
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (first) {
                    first = false;
                    return;
                }
                prefs.setRefreshMinutes(intervalValues[pos]);
                if (prefs.isAutoRefresh()) {
                    RefreshScheduler.apply(SettingsActivity.this);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateAutoRefreshDependents(boolean enabled) {
        intervalRow.setAlpha(enabled ? 1f : 0.4f);
        intervalSpinner.setEnabled(enabled);
        pushSwitch.setEnabled(enabled);
    }
}
