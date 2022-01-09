package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class LockScreenFragment extends Fragment {


    private final AtomicBoolean sending = new AtomicBoolean(false);
    private CommandingActivity activity;
    private Button sendButton;
    private EditText lockPasswordInput;
    private EditText lockMaxWrongTimesInput;
    private ProgressBar progressBar;
    private BottomNavigationView bottomSelector;
    private LinearLayout lockLayout;
    private LinearLayout unlockLayout;
    private Thread sendingThread;
    private State nowState = State.lock;
    private int originOutputColor;
    private long sendingStartTime;

    public LockScreenFragment() {
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
                () -> activity.getToolbar().setTitle(R.string.lock_screen_fragment_title)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_lock_screen, container, false);
        sendButton = get.findViewById(R.id.lock_screen_do_button);
        lockPasswordInput = get.findViewById(R.id.lock_password_input_text);
        lockMaxWrongTimesInput = get.findViewById(R.id.lock_max_wrong_times_input_text);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        bottomSelector = get.findViewById(R.id.lock_screen_bottom_selector);
        lockLayout = get.findViewById(R.id.lock_screen_layout);
        unlockLayout = get.findViewById(R.id.unlock_screen_layout);
        initView();
        return get;
    }

    private void initView() {
        originOutputColor = ContextCompat.getColor(activity, R.color.generic_sending_button_bg);
        sendButton.setOnClickListener((view) -> this.sendCommand());
        sendButton.setBackgroundColor(originOutputColor);
        lockPasswordInput.setText("");
        lockMaxWrongTimesInput.setText("");
        progressBar.setVisibility(View.INVISIBLE);
        bottomSelector.setOnNavigationItemSelectedListener(this::bottomSelect);
        bottomSelector.setSelectedItemId(R.id.menu_item_lock_screen);
        lockLayout.setVisibility(View.VISIBLE);
        unlockLayout.setVisibility(View.INVISIBLE);
    }

    private void changeState(State state) {
        nowState = state;
        switch (nowState) {
            case lock:
                lockLayout.setVisibility(View.VISIBLE);
                unlockLayout.setVisibility(View.INVISIBLE);
                break;
            case unlock:
                lockLayout.setVisibility(View.INVISIBLE);
                unlockLayout.setVisibility(View.VISIBLE);
            default:
        }
    }

    private boolean bottomSelect(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_item_lock_screen) {
            changeState(State.lock);
        } else if (id == R.id.menu_item_unlock_screen) {
            changeState(State.unlock);
        }
        return true;
    }

    private void resetView() {
        sendButton.setOnClickListener((view) -> this.sendCommand());
        progressBar.setVisibility(View.INVISIBLE);
    }

    private String getLockCommand() {
        String password = lockPasswordInput.getText() + "";
        String maxWrongTimes = lockMaxWrongTimesInput.getText() + "";
        int maxWrongTimesInt = 0;
        if (maxWrongTimes.matches("^[0-9]+$")) {
            maxWrongTimesInt = Integer.parseInt(maxWrongTimes);
        }
        return String.format(getString(R.string.command_lock_screen_format), JSON.toJSONString(password), maxWrongTimesInt);
    }

    private String getUnlockCommand() {
        return getString(R.string.command_unlock_screen_string);
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
        // evaluate command
        String command = "";
        switch (nowState) {
            case lock:
                command = getLockCommand();
                break;
            case unlock:
                command = getUnlockCommand();
            default:
        }
        if (command.isEmpty()) {
            return;
        }
        // send command
        final String finalCommand = command;
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending();
            if (Global.client.sendCommand(finalCommand)) {
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
            String show = getString(R.string.failed);
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                succeed = result.getResultInt() == 1;
                show = succeed ? getString(R.string.succeed) : show;
            }
            int color = ContextCompat.getColor(activity, succeed ? R.color.test_output_succeed_bg : R.color.test_output_failed_bg);
            sendButton.setBackgroundColor(color);
            activity.handler.postDelayed(() -> sendButton.setBackgroundColor(originOutputColor), 3000);
            Toast.makeText(activity, show, Toast.LENGTH_SHORT).show();
        });
    }

    private enum State {lock, unlock}
}