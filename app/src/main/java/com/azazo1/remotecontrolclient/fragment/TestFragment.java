package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
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

public class TestFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private CommandingActivity activity;
    private Button sendButton;
    private EditText testText;
    private EditText testOutput;
    private Thread sendingThread;
    private ProgressBar progressBar;
    private Drawable originOutputDrawable;

    public TestFragment() {
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
                () -> activity.getToolbar().setTitle(R.string.test_fragment_title)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_test, container, false);
        sendButton = get.findViewById(R.id.send_command_button);
        testOutput = get.findViewById(R.id.test_output);
        testText = get.findViewById(R.id.test_text);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return get;
    }

    private void initView() {
        sendButton.setOnClickListener((view) -> sendCommand());
        progressBar.setVisibility(View.INVISIBLE);
        originOutputDrawable = testOutput.getBackground();
    }

    private void resetView() {
        sendButton.setOnClickListener((view) -> sendCommand());
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void sendCommand() {
        String text = testText.getText() + "";
        if (text.isEmpty()) {
            return;
        }
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending();
            String command = String.format(getString(R.string.command_test_format_string), JSON.toJSONString(text));
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
        long startTime = Tools.getTimeInMilli();
        AtomicInteger progress = new AtomicInteger();
        activity.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sending.get()) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress.getAndIncrement());
                    progress.compareAndSet(100, 0);
                    activity.handler.postDelayed(this, (long) (1.0 / Config.loopingRate * 1000));
                    sendButton.setOnClickListener((view) -> {
                        if (Tools.getTimeInMilli() - startTime > Config.waitingTimeForTermination) { // 防止连点触发

                            Snackbar s = Snackbar.make(view, R.string.notice_still_sending, Snackbar.LENGTH_SHORT);
                            s.setAction(R.string.verify_terminate, (view1) -> {
                                sending.set(false);
                                if (sendingThread != null && !sendingThread.isInterrupted()) {
                                    sendingThread.interrupt();
                                }
                            });
                            s.show();
                        }
                    });
                } else {
                    resetView();
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }

    private void resultAppearancePost(CommandResult result) {
        activity.handler.post(() -> {
            String show = "failed";
            boolean succeed = false;
            if (result != null && result.type == CommandResult.ResultType.INT) {
                succeed = result.getResultInt() == 1;
                show = succeed ? "succeed" : "failed";
            }
            testOutput.setBackgroundColor(activity.getColor(succeed ? R.color.test_output_succeed_bg : R.color.test_output_failed_bg));
            testOutput.setText(show);
            activity.handler.postDelayed(() -> testOutput.setBackground(originOutputDrawable), 3000);
        });
    }
}