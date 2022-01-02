package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.azazo1.remotecontrolclient.CommandResult;
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
    private Button sendButton;
    private EditText clipboardText;
    private Thread sendingThread;
    private ProgressBar progressBar;
    private Spinner spinner;
    private int originButtonColor;
    private final View.OnClickListener sendListener = (View view) -> {
        String action = (String) spinner.getSelectedItem();
        String[] array = activity.getResources().getStringArray(R.array.clipboard_action_spinner_array);
        if (action.equals(array[0]) || action.equals(array[1])) {
            sendCommand(action, "");
        } else if (action.equals(array[2])) {
            sendCommand(action, clipboardText.getText() + "");
        }
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_clipboard, container, false);
        sendButton = get.findViewById(R.id.send_command_button);
        spinner = get.findViewById(R.id.clipboard_action_spinner);
        clipboardText = get.findViewById(R.id.clipboard_text);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return get;
    }

    private void initView() {
        progressBar.setVisibility(View.INVISIBLE);
        sendButton.setOnClickListener(sendListener);
        originButtonColor = activity.getColor(R.color.generic_sending_button_bg);
        sendButton.setBackgroundColor(originButtonColor);
    }

    private void resetView() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void sendCommand(String action, String content) {
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
                CommandResult result = Global.client.readCommandUntilGet();
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
            boolean succeed = false;
            if (result != null) {
                switch (result.getType()) {
                    case INT:
                        succeed = result.getResultInt() == 1;
                        break;
                    case JSON_OBJECT:
                        JSONObject obj = result.getResultJsonObject();
                        String content = obj.getString("content");
                        if (content != null) {
                            clipboardText.setText(content);
                            succeed = true;
                        }
                        break;
                    default:
                }

            }
            sendButton.setBackgroundColor(activity.getColor(succeed ? R.color.test_output_succeed_bg : R.color.test_output_failed_bg));
            activity.handler.postDelayed(() -> sendButton.setBackgroundColor(originButtonColor), 3000);
        });
    }
}