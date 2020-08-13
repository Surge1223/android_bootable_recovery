/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.systemupdatersample.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.UpdateEngine;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.android.systemupdatersample.R;
import com.example.android.systemupdatersample.UpdateConfig;
import com.example.android.systemupdatersample.UpdateManager;
import com.example.android.systemupdatersample.UpdaterState;
import com.example.android.systemupdatersample.util.UpdateConfigs;
import com.example.android.systemupdatersample.util.UpdateEngineErrorCodes;
import com.example.android.systemupdatersample.util.UpdateEngineStatuses;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * UI for SystemUpdaterSample app.
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private TextView mTextViewBuild;
    private Spinner mSpinnerConfigs;
    private TextView mTextViewConfigsDirHint;
    private Button mButtonReload;
    private Button mButtonApplyConfig;
    private Button mButtonStop;
    private Button mButtonReset;
    private Button mButtonSuspend;
    private Button mButtonResume;
    private ProgressBar mProgressBar;
    private TextView mTextViewUpdaterState;
    private TextView mTextViewEngineStatus;
    private TextView mTextViewEngineErrorCode;
    private TextView mTextViewUpdateInfo;
    private Button mButtonSwitchSlot;
    private Context mContext;
    private List<UpdateConfig> mConfigs;
    private final UpdateManager mUpdateManager =
            new UpdateManager(new UpdateEngine(), new Handler());
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.mTextViewBuild = findViewById(R.id.textViewBuild);
        this.mSpinnerConfigs = findViewById(R.id.spinnerConfigs);
        TextView mTextViewConfigsDirHint = findViewById(R.id.textViewConfigsDirHint);
        this.mButtonReload = findViewById(R.id.buttonReload);
        this.mButtonApplyConfig = findViewById(R.id.buttonApplyConfig);
        this.mButtonStop = findViewById(R.id.buttonStop);
        this.mButtonReset = findViewById(R.id.buttonReset);
        this.mButtonSuspend = findViewById(R.id.buttonSuspend);
        this.mButtonResume = findViewById(R.id.buttonResume);
        this.mProgressBar = findViewById(R.id.progressBar);
        this.mTextViewUpdaterState = findViewById(R.id.textViewUpdaterState);
        this.mTextViewEngineStatus = findViewById(R.id.textViewEngineStatus);
        this.mTextViewEngineErrorCode = findViewById(R.id.textViewEngineErrorCode);
        this.mTextViewUpdateInfo = findViewById(R.id.textViewUpdateInfo);
        this.mButtonSwitchSlot = findViewById(R.id.buttonSwitchSlot);
        mTextViewConfigsDirHint.setText(UpdateConfigs.getConfigsRoot(this));
        try {
            FileTest();
        } catch (IOException e) {
            e.printStackTrace();
        }
        uiResetWidgets();
        loadUpdateConfigs();
        this.mUpdateManager.setOnStateChangeCallback(this::onUpdaterStateChange);
        this.mUpdateManager.setOnEngineStatusUpdateCallback(this::onEngineStatusUpdate);
        this.mUpdateManager.setOnEngineCompleteCallback(this::onEnginePayloadApplicationComplete);
        this.mUpdateManager.setOnProgressUpdateCallback(this::onProgressUpdate);
    }
    @Override
    protected void onDestroy() {
        this.mUpdateManager.setOnEngineStatusUpdateCallback(null);
        this.mUpdateManager.setOnProgressUpdateCallback(null);
        this.mUpdateManager.setOnEngineCompleteCallback(null);
        super.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Binding to UpdateEngine invokes onStatusUpdate callback,
        // persisted updater state has to be loaded and prepared beforehand.
        if (mUpdateManager.getUpdaterState() >= 2) {
            this.mUpdateManager.bind();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mUpdateManager.getUpdaterState() < 2) {
            this.mUpdateManager.unbind();
        }
    }

    /**
     * reload button is clicked
     */
    public void onReloadClick(View view) {
        loadUpdateConfigs();
    }
    /**
     * view config button is clicked
     */
    public void onViewConfigClick(View view) {
        UpdateConfig config = mConfigs.get(mSpinnerConfigs.getSelectedItemPosition());
        if (config.getName() != null) {
            new AlertDialog.Builder(this)
                    .setTitle(config.getName())
                    .setMessage(config.getRawJson())
                    .setPositiveButton(R.string.close, (dialog, id) -> dialog.dismiss())
                    .show();
        }
    }

    /**
     * apply config button is clicked
     */
    public void onApplyConfigClick(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Apply Update")
                .setMessage("Do you really want to apply this update?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    uiResetWidgets();
                    uiResetEngineText();
                    applyUpdate(getSelectedConfig());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    private void applyUpdate(UpdateConfig config) {
        try {
            mUpdateManager.applyUpdate(this, config);
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to apply update " + config.getName(), e);
        }
    }
    /**
     * stop button clicked
     */
    public void onStopClick(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Stop Update")
                .setMessage("Do you really want to cancel running update?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    cancelRunningUpdate();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
    private void cancelRunningUpdate() {
        try {
            mUpdateManager.cancelRunningUpdate();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to cancel running update", e);
        }
    }
    /**
     * reset button clicked
     */
    public void onResetClick(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Reset Update")
                .setMessage("Do you really want to cancel running update"
                        + " and restore old version?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    resetUpdate();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
    private void resetUpdate() {
        try {
            mUpdateManager.resetUpdate();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to reset update", e);
        }
    }
    /**
     * suspend button clicked
     */
    public void onSuspendClick(View view) {
        try {
            mUpdateManager.suspend();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to suspend running update", e);
        }
    }
    /**
     * resume button clicked
     */
    public void onResumeClick(View view) {
        try {
            uiResetWidgets();
            uiResetEngineText();
            mUpdateManager.resume();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to resume running update", e);
        }
    }
    /**
     * switch slot button clicked
     */
    public void onSwitchSlotClick(View view) {
        uiResetWidgets();
        mUpdateManager.setSwitchSlotOnReboot();
    }
    /**
     * Invoked when SystemUpdaterSample app state changes.
     * Value of {@code state} will be one of the
     * values from {@link UpdaterState}.
     */
    private void onUpdaterStateChange(int state) {
        Log.i(TAG, "UpdaterStateChange state="
                + UpdaterState.getStateText(state)
                + "/" + state);
        runOnUiThread(() -> {
            setUiUpdaterState(state);
            if (state == UpdaterState.IDLE) {
                uiStateIdle();
            } else if (state == UpdaterState.RUNNING) {
                uiStateRunning();
            } else if (state == UpdaterState.PAUSED) {
                uiStatePaused();
            } else if (state == UpdaterState.ERROR) {
                uiStateError();
            } else if (state == UpdaterState.SLOT_SWITCH_REQUIRED) {
                uiStateSlotSwitchRequired();
            } else if (state == UpdaterState.REBOOT_REQUIRED) {
                uiStateRebootRequired();
            }
        });
    }

    public boolean dirExists(String dirPath) {
        final File dir = new File(dirPath);
        return dir.exists() && dir.isDirectory();
    }

    public void createDirIfNotExists(String dirPath) {
        if (!dirExists(dirPath)) {
            File dir = new File(dirPath);
            try {
                dir.mkdir();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public void ensureDirectoriesExist(final File file, final boolean b) throws IOException {
        final LinkedList<File> list = new LinkedList<File>();
        File parentFile = file;
        do {
            list.addFirst(parentFile);
        } while ((parentFile = parentFile.getParentFile()) != null);
        for (final File file2 : list) {
            if (!file2.exists()) {
                if (!file2.mkdir()) {
                    throw new IOException("Unable to create directory: " + file);
                }
                if (!b) {
                    continue;
                }
                makeDirectoryWorldAccessible(file2);
            } else {
                if (!file2.isDirectory()) {
                    throw new IOException(file2 + " exists but is not a directory");
                }
            }
        }
    }

    public  void makeDirectoryWorldAccessible(final File file) throws IOException {
        if (!file.isDirectory()) {
            throw new IOException(file + " must be a directory");
        }
        makeWorldReadable(file);
        if (!file.setExecutable(true, false)) {
            throw new IOException("Unable to make " + file + " world-executable");
        }
    }

    @SuppressLint("SetWorldReadable")
    public void makeWorldReadable(final File file) throws IOException {
        if (!file.setReadable(true, false)) {
            throw new IOException("Unable to make " + file + " world-readable");
        }
    }

    public void FileTest() throws IOException {
        File filesdir = new File(getFilesDir().getAbsolutePath());
        File configsRoot = new File(UpdateConfigs.getConfigsRoot(this));
        try {
            if (!filesdir.exists()) {
                ensureDirectoriesExist(filesdir, true);
                createDirIfNotExists(filesdir.getAbsolutePath());
            }
            if (!configsRoot.exists()) {
                createDirIfNotExists(configsRoot.getAbsolutePath());
            }
            if (!placeJson(this)) {
                Log.d("UpdaterSample", "Failed to prepare files...");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SetWorldReadable")
    private boolean copyJson(Context context, String filename) throws IOException {
        InputStream input;
        File configsRoot = new File(context.getFilesDir().getAbsolutePath() + "/configs/");
        File output = new File(configsRoot, filename);
        FileOutputStream outputStream;
        if (filename.endsWith(".json")) {
            input = context.getAssets().open("raw/" + filename);
        } else {
            input = context.getAssets().open("raw/" + filename);
        }
        outputStream = new FileOutputStream(output);
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                try {
                    outputStream.write(buffer, 0, read);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return configsRoot.setReadable(true, false) &&
                    configsRoot.setExecutable(true, false) &&
                    output.setReadable(true, false) &&
                    output.setExecutable(true, false);

        } finally {
            input.close();
            outputStream.flush();
            outputStream.getFD().sync();
            outputStream.close();
        }
    }

    public boolean placeJson(Context mContext) throws IOException {
        return copyJson(mContext, "du_ota.json");
    }

    public void copyFileOrDir(String path) {
        File filesdir = new File(getFilesDir().getAbsolutePath());
        File configsdir = new File(filesdir.getAbsolutePath() + "/configs/");
        AssetManager assetManager = this.getAssets();
        String assets[] = null;
        try {
            assets = assetManager.list(path);
            if (assets.length == 0) {
                copyFile(path);
            } else {
                String fullPath =  configsdir.getAbsolutePath() + path;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdir();
                for (String asset : assets) {
                    copyFileOrDir(path + "/" + asset);
                }
            }
        } catch (IOException ex) {
            Log.e("tag", "I/O Exception", ex);
        }
    }

    private void copyFile(String filename) {
        AssetManager assetManager = this.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(filename);
            String newFileName = Environment.getDataDirectory()	+ "/data" + this.getPackageName() + "/" + filename;
            out = new FileOutputStream(newFileName);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }

    }

    /**
     * Invoked when {@link UpdateEngine} status changes. Value of {@code status} will
     * be one of the values from {@link UpdateEngine.UpdateStatusConstants}.
     */
    private void onEngineStatusUpdate(int status) {
        Log.i(TAG, "StatusUpdate - status="
                + UpdateEngineStatuses.getStatusText(status)
                + "/" + status);
        runOnUiThread(() -> {
            setUiEngineStatus(status);
        });
    }
    /**
     * Invoked when the payload has been applied, whether successfully or
     * unsuccessfully. The value of {@code errorCode} will be one of the
     * values from {@link UpdateEngine.ErrorCodeConstants}.
     */
    private void onEnginePayloadApplicationComplete(int errorCode) {
        final String completionState = UpdateEngineErrorCodes.isUpdateSucceeded(errorCode)
                ? "SUCCESS"
                : "FAILURE";
        Log.i(TAG,
                "PayloadApplicationCompleted - errorCode="
                        + UpdateEngineErrorCodes.getCodeName(errorCode) + "/" + errorCode
                        + " " + completionState);
        runOnUiThread(() -> {
            setUiEngineErrorCode(errorCode);
        });
    }
    /**
     * Invoked when update progress changes.
     */
    private void onProgressUpdate(double progress) {
        mProgressBar.setProgress((int) (100 * progress));
    }
    /** resets ui */
    private void uiResetWidgets() {
        mTextViewBuild.setText(Build.DISPLAY);
        mSpinnerConfigs.setEnabled(false);
        mButtonReload.setEnabled(false);
        mButtonApplyConfig.setEnabled(false);
        mButtonStop.setEnabled(false);
        mButtonReset.setEnabled(false);
        mButtonSuspend.setEnabled(false);
        mButtonResume.setEnabled(false);
        mProgressBar.setEnabled(false);
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);
        mButtonSwitchSlot.setEnabled(false);
        mTextViewUpdateInfo.setTextColor(Color.parseColor("#aaaaaa"));
    }
    private void uiResetEngineText() {
        mTextViewEngineStatus.setText(R.string.unknown);
        mTextViewEngineErrorCode.setText(R.string.unknown);
        // Note: Do not reset mTextViewUpdaterState; UpdateManager notifies updater state properly.
    }
    private void uiStateIdle() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mSpinnerConfigs.setEnabled(true);
        mButtonReload.setEnabled(true);
        mButtonApplyConfig.setEnabled(true);
        mProgressBar.setProgress(0);
    }
    private void uiStateRunning() {
        uiResetWidgets();
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mButtonStop.setEnabled(true);
        mButtonSuspend.setEnabled(true);
    }
    private void uiStatePaused() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mButtonResume.setEnabled(true);
    }
    private void uiStateSlotSwitchRequired() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mButtonSwitchSlot.setEnabled(true);
        mTextViewUpdateInfo.setTextColor(Color.parseColor("#777777"));
    }
    private void uiStateError() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
    }
    private void uiStateRebootRequired() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
    }


    /**
     * loads json configurations from configs dir that is defined in {@link UpdateConfigs}.
     */
    private void loadUpdateConfigs() {
        mConfigs = UpdateConfigs.getUpdateConfigs(this);
        loadConfigsToSpinner(mConfigs);
    }
    /**
     * @param status update engine status code
     */
    private void setUiEngineStatus(int status) {
        String statusText = UpdateEngineStatuses.getStatusText(status);
        mTextViewEngineStatus.setText(statusText + "/" + status);
    }
    /**
     * @param errorCode update engine error code
     */
    private void setUiEngineErrorCode(int errorCode) {
        String errorText = UpdateEngineErrorCodes.getCodeName(errorCode);
        mTextViewEngineErrorCode.setText(errorText + "/" + errorCode);
    }
    /**
     * @param state updater sample state
     */
    private void setUiUpdaterState(int state) {
        String stateText = UpdaterState.getStateText(state);
        mTextViewUpdaterState.setText(stateText + "/" + state);
    }
    private void loadConfigsToSpinner(List<UpdateConfig> configs) {
        String[] spinnerArray = UpdateConfigs.configsToNames(configs);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                spinnerArray);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout
                .simple_spinner_dropdown_item);
        mSpinnerConfigs.setAdapter(spinnerArrayAdapter);
    }
    private UpdateConfig getSelectedConfig() {
        return mConfigs.get(mSpinnerConfigs.getSelectedItemPosition());
    }
}
