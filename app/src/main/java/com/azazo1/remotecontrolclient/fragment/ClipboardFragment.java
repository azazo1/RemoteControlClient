package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.CommandResultHandler;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ClipboardFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private long sendingStartTime;
    private CommandingActivity activity;
    private Button clearButton;
    private Button getButton;
    private Button setButton;
    private EditText clipboardText;
    private Thread sendingThread;
    private ProgressBar progressBar;
    private int originSetButtonColor;
    private final CommandResultHandler resultHandler_set = (CommandResult result) -> {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                succeed = result.getResultInt() == 1;
            }
            int color = ContextCompat.getColor(activity, succeed ? R.color.succeed : R.color.failed);
            setButton.setBackgroundColor(color);
            activity.handler.postDelayed(() -> setButton.setBackgroundColor(originSetButtonColor), 3000);
            Toast.makeText(activity, succeed ? R.string.succeed : R.string.failed, Toast.LENGTH_SHORT).show();
        });
    };
    private int originGetButtonColor;
    private final CommandResultHandler resultHandler_get = (CommandResult result) -> {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.JSON_OBJECT)) {
                JSONObject obj = result.getResultJsonObject();
                String content = obj.getString("content");
                if (content != null) {
                    clipboardText.setText(content);
                    succeed = true;
                }
            }
            int color = ContextCompat.getColor(activity, succeed ? R.color.succeed : R.color.failed);
            getButton.setBackgroundColor(color);
            activity.handler.postDelayed(() -> getButton.setBackgroundColor(originGetButtonColor), 3000);
            Toast.makeText(activity, succeed ? R.string.succeed : R.string.failed, Toast.LENGTH_SHORT).show();
        });
    };
    private int originClearButtonColor;
    private final CommandResultHandler resultHandler_clear = (CommandResult result) -> {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                succeed = result.getResultInt() == 1;
            }
            int color = ContextCompat.getColor(activity, succeed ? R.color.succeed : R.color.failed);
            clearButton.setBackgroundColor(color);
            activity.handler.postDelayed(() -> clearButton.setBackgroundColor(originClearButtonColor), 3000);
            Toast.makeText(activity, succeed ? R.string.succeed : R.string.failed, Toast.LENGTH_SHORT).show();
        });
    };

    public ClipboardFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CommandingActivity) context;
        activity.fragment = this;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.clipboard_fragment_title)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_clipboard, container, false);
        setButton = get.findViewById(R.id.clipboard_set_button);
        getButton = get.findViewById(R.id.clipboard_get_button);
        clearButton = get.findViewById(R.id.clipboard_clear_button);
        clipboardText = get.findViewById(R.id.clipboard_text);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return get;
    }

    private void initView() {
        progressBar.setVisibility(View.INVISIBLE);
        originSetButtonColor = ContextCompat.getColor(activity, R.color.clipboard_set_button_bg);
        originGetButtonColor = ContextCompat.getColor(activity, R.color.clipboard_get_button_bg);
        originClearButtonColor = ContextCompat.getColor(activity, R.color.clipboard_clear_button_bg);
        setButton.setBackgroundColor(originSetButtonColor);
        getButton.setBackgroundColor(originGetButtonColor);
        clearButton.setBackgroundColor(originClearButtonColor);
        setButton.setOnClickListener((view) -> sendCommand("set", clipboardText.getText() + "", resultHandler_set));
        getButton.setOnClickListener((view) -> sendCommand("get", "", resultHandler_get));
        clearButton.setOnClickListener((view) -> sendCommand("clear", "", resultHandler_clear));
    }

    private void resetView() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void sendCommand(String action, String content, CommandResultHandler handler) {
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
            whileSending();
            String command = String.format(getString(R.string.command_clipboard_format), JSON.toJSONString(action), JSON.toJSONString(content));
            if (Global.client.sendCommand(command)) {
                CommandResult result = Global.client.readCommand();
                handler.resultAppearancePost(result);
            }
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    private void whileSending() {
        sendingStartTime = Tools.getTimeInMilli();
        AtomicInteger progress = new AtomicInteger();
        activity.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sending.get()) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress.getAndIncrement());
                    progress.compareAndSet(100, 0);
                    activity.handler.postDelayed(this, (long) (1.0 / Config.loopingRate * 1000));
                } else {
                    resetView();
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }
}