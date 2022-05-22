package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ShowTextFragment extends Fragment {


    private final AtomicBoolean sending = new AtomicBoolean(false);
    private CommandingActivity activity;
    private Button sendButton;
    private EditText showTextInput;
    private EditText showTextOutput;
    private Thread sendingThread;
    private ProgressBar progressBar;
    private int originOutputColor;
    private EditText showTimeInput;
    private long sendingStartTime;

    public ShowTextFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CommandingActivity) context;
        activity.fragment = this;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.show_text_fragment_title)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_show_text, container, false);
        sendButton = get.findViewById(R.id.send_command_button);
        showTextOutput = get.findViewById(R.id.test_output);
        showTimeInput = get.findViewById(R.id.show_time_text);
        showTextInput = get.findViewById(R.id.test_text);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return get;
    }

    private void initView() {
        showTimeInput.setText(String.valueOf(Config.defaultShowTextTime)); // 设置默认时间值
        sendButton.setOnClickListener((view) -> sendCommand());
        progressBar.setVisibility(View.INVISIBLE);
        originOutputColor = ContextCompat.getColor(activity, R.color.generic_sending_button_bg);
        showTimeInput.requestFocus();
    }

    private void resetView() {
        sendButton.setOnClickListener((view) -> sendCommand());
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void sendCommand() {
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
        String text = showTextInput.getText() + "";
        String timeText = showTimeInput.getText() + "";
        if (text.isEmpty() || timeText.isEmpty()) {
            return;
        }
        double showTime = Double.parseDouble(timeText + "");
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending();
            String command = String.format(getString(R.string.command_show_text_format), JSON.toJSONString(text), showTime);
            if (Global.client.sendCommand(command)) {
                CommandResult result = Global.client.readCommand();
                resultAppearancePost(result);
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

    private void resultAppearancePost(CommandResult result) {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            String show = getString(R.string.failed);
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                succeed = result.getResultInt() == 1;
                show = succeed ? getString(R.string.succeed) : show;
            }
            int color = ContextCompat.getColor(activity,succeed ? R.color.succeed : R.color.failed);
            sendButton.setBackgroundColor(color);
            showTextOutput.setText(show);
            activity.handler.postDelayed(() -> sendButton.setBackgroundColor(originOutputColor), 3000);
        });
    }
}