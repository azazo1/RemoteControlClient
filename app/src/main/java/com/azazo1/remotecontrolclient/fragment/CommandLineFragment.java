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
import androidx.fragment.app.Fragment;

import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandLineFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    public EditText commandInputEntry;
    public Button sendCommandButton;
    private EditText commandOutputEntry;
    private CommandingActivity activity;
    private ProgressBar progressBar;
    private Thread sendingThread;
    private long sendingStartTime;

    public CommandLineFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        ((CommandingActivity) context).fragment = this;
        activity = (CommandingActivity) context;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.command_line_fragment_title)
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
        View view = inflater.inflate(R.layout.fragment_command_line, container, false);
        commandInputEntry = view.findViewById(R.id.command_input_entry);
        commandOutputEntry = view.findViewById(R.id.command_output_entry);
        sendCommandButton = view.findViewById(R.id.send_command_button);
        progressBar = view.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return view;
    }


    public void sendCommand() {
        sendCommand(commandInputEntry.getText() + "");
    }

    public void sendCommand(String command) {
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
            String commandFinal = (command).replace('\n', ' ');
            if (Global.client.sendCommand(commandFinal)) {
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
            show = result.getResult().toString();
        }
        String finalShow = show;
        activity.handler.post(() -> commandOutputEntry.setText(finalShow));
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
        sendCommandButton.setOnClickListener((view) -> sendCommand());
        progressBar.setVisibility(View.INVISIBLE);
    }
}