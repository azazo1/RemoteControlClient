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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecuteFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    public EditText executeOutputEntry;
    public Button executeButton;
    private EditText executeInputEntry;
    private CommandingActivity activity;
    private ProgressBar progressBar;
    private Thread sendingThread;
    private long sendingStartTime;

    public ExecuteFragment() {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        ((CommandingActivity) context).fragment = this;
        activity = (CommandingActivity) context;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.execute_fragment_title)
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
        View view = inflater.inflate(R.layout.fragment_execute, container, false);
        executeOutputEntry = view.findViewById(R.id.execute_output_entry);
        executeInputEntry = view.findViewById(R.id.execute_input_entry);
        executeButton = view.findViewById(R.id.send_command_button);
        progressBar = view.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return view;
    }

    public void execute() {
        sendCommand(executeInputEntry.getText() + "");
    }

    public void sendCommand(String executeContent) {
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
            if (Global.client.sendCommand(
                    String.format(Locale.getDefault(),
                            getString(R.string.command_execute_format), JSON.toJSONString(executeContent))
            )) {
                CommandResult result = Global.client.readCommandUntilGet();
                resultAppearancePost(result);
            }
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    private void resultAppearancePost(CommandResult result) {
        if (!sending.get()) {
            return;
        }
        String show = "null";
        if (result != null && result.getResult() != null) {
            if (result.checkType(CommandResult.ResultType.JSON_OBJECT)) {
                JSONObject jsonObject = result.getResultJsonObject();
                show = jsonObject.getString("output");
                show = show.concat(jsonObject.getString("error"));
            } else if (result.checkType(CommandResult.ResultType.INT) && result.getResultInt() == -1) {
                activity.handler.post(() ->
                        Toast.makeText(
                                activity,
                                R.string.execute_remote_permission_required,
                                Toast.LENGTH_SHORT
                        ).show());
            }
        }
        String finalShow = show;
        activity.handler.post(() -> executeOutputEntry.setText(finalShow));
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
                    initView();
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }

    private void initView() {
        executeInputEntry.requestFocus();
        executeButton.setOnClickListener((view) -> execute());
        progressBar.setVisibility(View.INVISIBLE);
    }
}