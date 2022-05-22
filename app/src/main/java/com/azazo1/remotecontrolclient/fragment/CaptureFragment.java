package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.CommandResultHandler;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.MyReporter;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.azazo1.remotecontrolclient.downloadhelper.Downloader;
import com.azazo1.remotecontrolclient.downloadhelper.FileDetail;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CaptureFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private long sendingStartTime;
    private CommandingActivity activity;
    private Button takeButton;
    private Button downloadButton;
    private ImageView captureShower;
    private ToggleButton modeToggle;
    private ToggleButton autoToggle;
    private int originButtonColor;
    private Thread sendingThread;
    private ProgressBar progressBar;
    private final MyReporter mReporter = new MyReporter() {
        @Override
        public void report(int now, int total) {
            progressBar.setProgress((int) (100.0 * now / total));
        }

        @Override
        public void reportEnd(int code) {
            if (code == 1) { // 图像接收成功
                activity.handler.post(() -> {
                    boolean succeed = false;
                    try {
                        captureShower.setImageBitmap(BitmapFactory.decodeFile(getStoreCaptureFile().getAbsolutePath()));
                        succeed = true; // 图像显示成功
                    } catch (IOException ignore) {
                    }
                    // 视觉反馈
                    int color = ContextCompat.getColor(activity, succeed ? R.color.succeed : R.color.failed);
                    downloadButton.setBackgroundColor(color);
                    activity.handler.postDelayed(() -> downloadButton.setBackgroundColor(originButtonColor), 3000);
                    Toast.makeText(activity, succeed ? R.string.succeed : R.string.failed, Toast.LENGTH_SHORT).show();
                });
            }
        }
    };
    private final CommandResultHandler resultHandler_take = (CommandResult result) -> {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                succeed = result.getResultInt() == 1;
                if (autoToggle.isChecked()) { // 如果选择了自动模式则自动下载图片
                    activity.handler.post(this::sendCommandDownloadCapture);
                }
            }
            int color = ContextCompat.getColor(activity, succeed ? R.color.succeed : R.color.failed);
            takeButton.setBackgroundColor(color);
            activity.handler.postDelayed(() -> takeButton.setBackgroundColor(originButtonColor), 3000);
            Toast.makeText(activity, succeed ? R.string.succeed : R.string.failed, Toast.LENGTH_SHORT).show();
        });
    };

    public CaptureFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CommandingActivity) context;
        activity.fragment = this;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.capture_fragment_title)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_capture, container, false);
        takeButton = get.findViewById(R.id.capture_take_button);
        downloadButton = get.findViewById(R.id.capture_download_button);
        modeToggle = get.findViewById(R.id.capture_mode_toggle);
        autoToggle = get.findViewById(R.id.capture_auto_toggle);
        captureShower = get.findViewById(R.id.capture_image);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return get;
    }

    private void initView() {
        progressBar.setVisibility(View.INVISIBLE);
        originButtonColor = ContextCompat.getColor(activity, R.color.generic_sending_button_bg);
        takeButton.setBackgroundColor(originButtonColor);
        downloadButton.setBackgroundColor(originButtonColor);
        takeButton.setOnClickListener((view) -> sendCommand(getAction(), resultHandler_take));
        downloadButton.setOnClickListener((view) -> sendCommandDownloadCapture());
        captureShower.setOnLongClickListener((view) -> {
            openCaptureWith();
            return true;
        });
    }

    private CaptureAction getAction() {
        return modeToggle.isChecked() ? CaptureAction.SCREEN : CaptureAction.CAMERA;
    }

    private void resetView() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void sendCommand(CaptureAction action, CommandResultHandler handler) {
        if (sending.get()) {
            if (Tools.getTimeInMilli() - sendingStartTime > Config.waitingTimeForTermination) { // 防止连点触发
                Snackbar s = Snackbar.make(progressBar, R.string.notice_still_sending, Snackbar.LENGTH_SHORT);
                s.setAction(R.string.verify_terminate, (view1) -> {
                    sending.set(false);
                    if (sendingThread != null && !sendingThread.isInterrupted()) {
                        sendingThread.interrupt();
                    }
                });
                s.show();
            }
            return;
        }
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending(true);
            String command = String.format(getString(R.string.command_capture_format), JSON.toJSONString(action.getActionString()));
            if (Global.client.sendCommand(command)) {
                CommandResult result = Global.client.readCommand();
                handler.resultAppearancePost(result);
            }
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    public void sendCommandDownloadCapture() {
        if (sending.get()) {
            if (Tools.getTimeInMilli() - sendingStartTime > Config.waitingTimeForTermination) { // 防止连点触发
                Snackbar s = Snackbar.make(progressBar, R.string.notice_still_sending, Snackbar.LENGTH_SHORT);
                s.setAction(R.string.verify_terminate, (view1) -> {
                    sending.set(false);
                    if (sendingThread != null && !sendingThread.isInterrupted()) {
                        sendingThread.interrupt();
                    }
                });
                s.show();
            }
            return;
        }
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending(false);
            downloadCapture();
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    public void downloadCapture() {
        try {
            FileDetail fileDetail;
            fileDetail = Downloader.getFileDetail("temp.png");
            if (fileDetail != null && fileDetail.available) {
                File storeFile = getStoreCaptureFile();
                boolean ignored = Downloader.plainDownloadFile(fileDetail, storeFile, mReporter);
            } else {
                Log.e("capture_download", "no remote file or file is too big.");
                mReporter.reportEnd(7); // 见 Downloader.plainDownloadFile 注释
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    private File getStoreCaptureFile() throws IOException {
        File file = new File(activity.getExternalCacheDir() + "/" + "temp.png");
        if (!file.exists()) {
            boolean ignored = file.createNewFile();
        }
        return file;
    }

    private void whileSending(boolean barRunning) {
        sendingStartTime = Tools.getTimeInMilli();
        AtomicInteger progress = new AtomicInteger();
        activity.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sending.get()) {
                    progressBar.setVisibility(View.VISIBLE);
                    if (barRunning) {
                        progressBar.setProgress(progress.getAndIncrement());
                        progress.compareAndSet(100, 0);
                    }
                    activity.handler.postDelayed(this, (long) (1000.0 / Config.loopingRate));
                } else {
                    resetView();
                }
            }
        }, (long) (1000.0 / Config.loopingRate));
    }

    /**
     * 钓起外部应用打开该图片
     */
    public void openCaptureWith() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", getStoreCaptureFile());
                intent.setDataAndType(uri, "image/*");
            } else {
                intent.setDataAndType(Uri.fromFile(getStoreCaptureFile()), "image/*");
            }
            startActivity(intent);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(activity, R.string.open_capture_failed, Toast.LENGTH_SHORT).show();
        }
    }

    public enum CaptureAction {
        CAMERA("photo"), SCREEN("shortcut"); // todo shortcut 改为 capture (rc也要)
        String action;

        CaptureAction(String action) {
            this.action = action;
        }

        public String getActionString() {
            return action;
        }
    }
}