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
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandLineFragment extends Fragment {
    public EditText commandInputEntry;
    public Button sendCommandButton;
    private EditText commandOutputEntry;
    private CommandingActivity activity;
    private ProgressBar progressBar;
    private AtomicBoolean sending = new AtomicBoolean(false);
    private Thread sendingThread;

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
        sendCommandButton.setOnClickListener((view1) -> sendCommand());
        progressBar = view.findViewById(R.id.getting_command_result_progress_bar);
        return view;
    }


    public void sendCommand() {
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending();
            String command = (commandInputEntry.getText() + "").replace('\n', ' ');
            if (Global.client.sendCommand(command)) {
                CommandResult result = Global.client.readCommand();
                String show = "null";
                if (result.getResult() != null) {
                    show = result.getResult().toString();
                }
                String finalShow = show;
                activity.handler.post(() -> commandOutputEntry.setText(finalShow));
            }
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    private void whileSending() {
        AtomicInteger progress = new AtomicInteger();
        activity.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sending.get()) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress.getAndIncrement());
                    progress.compareAndSet(100, 0);
                    activity.handler.postDelayed(this, (long) (1.0 / Config.loopingRate * 1000));
                    sendCommandButton.setOnClickListener((view) -> {
                        Snackbar s = Snackbar.make(view, R.string.notice_still_sending, Snackbar.LENGTH_SHORT);
                        s.setAction(R.string.verifyTerminate, (view1) -> {
                            sending.set(false);
                            if (sendingThread != null && !sendingThread.isInterrupted()) {
                                sendingThread.interrupt();
                            }
                        });
                        s.show();
                    });
                } else {
                    sendCommandButton.setOnClickListener((view) -> sendCommand());
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }
}