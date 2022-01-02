package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

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

import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SurfWebsiteFragment extends Fragment {

    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final Vector<String> browsers = new Vector<>();
    private BrowsersChooserAdapter adapter;
    private CommandingActivity activity;
    private int originOutputColor;
    private Button sendButton;
    private EditText contentInput;
    private Spinner browserChooser;
    private CheckBox modeToggle;
    private Thread sendingThread;
    private boolean searchMode = false; // true to "search"; false to "url"
    private ProgressBar progressBar;
    private long sendingStartTime;

    public SurfWebsiteFragment() {
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
                () -> activity.getToolbar().setTitle(R.string.surf_website_fragment_title)
        );
        originOutputColor = activity.getColor(R.color.generic_sending_button_bg);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_surf_website, container, false);
        sendButton = get.findViewById(R.id.send_command_button);
        contentInput = get.findViewById(R.id.content_input);
        modeToggle = get.findViewById(R.id.surf_website_mode_toggle);
        browserChooser = get.findViewById(R.id.browser_chooser);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        adapter = new BrowsersChooserAdapter();
        initView();
        initBrowsers();
        return get;
    }

    private void initBrowsers() {
        sendCommand(getString(R.string.command_get_browsers_string), true);
    }


    private void initView() {
        modeToggle.setOnClickListener((view) -> {
            searchMode = !searchMode;
            modeToggle.setChecked(searchMode);
            if (searchMode) {
                modeToggle.setText(R.string.surf_website_mode_toggle_search_text);
                contentInput.setHint(R.string.hint_for_surf_website_search);
            } else {
                modeToggle.setText(R.string.surf_website_mode_toggle_url_text);
                contentInput.setHint(R.string.hint_for_surf_website_url);
            }
        });
        browserChooser.setAdapter(adapter);
        browserChooser.setLongClickable(true);
        browserChooser.setOnLongClickListener((view) -> {
            initBrowsers();
            return true;
        });
        searchMode = !searchMode; // 防止改变了初始searchMode
        modeToggle.callOnClick();
        sendButton.setOnClickListener((view) -> sendCommand());
        sendButton.setBackgroundColor(originOutputColor);
        progressBar.setVisibility(View.INVISIBLE);
        contentInput.requestFocus();
    }

    private void resetView() {
        sendButton.setOnClickListener((view) -> sendCommand());
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void sendCommand(String command, boolean isGetBrowsers) {
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
            if (Global.client.sendCommand(command)) {
                CommandResult result = Global.client.readCommandUntilGet();
                if (isGetBrowsers) {
                    resultAppearancePostOnBrowsers(result);
                } else {
                    resultAppearancePost(result);
                }
            }
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    public void sendCommand() {
        String content = contentInput.getText() + "";
        String using = "";
        try {
            using = browsers.get(browserChooser.getSelectedItemPosition()) + "";
        } catch (ArrayIndexOutOfBoundsException e) {
            Toast.makeText(activity, R.string.notice_invalid_browser_chosen, Toast.LENGTH_SHORT).show();
        }

        if (content.isEmpty()) {
            return;
        }
        String command;
        if (searchMode) {
            command = String.format(getString(R.string.command_surf_website_search_format), JSON.toJSONString(content), JSON.toJSONString(using));
        } else {
            command = String.format(getString(R.string.command_surf_website_url_format), JSON.toJSONString(content), JSON.toJSONString(using));
        }
        sendCommand(command, false);
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

    private void resultAppearancePostOnBrowsers(CommandResult result) {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.ARRAY)) {
                browsers.clear();
                browsers.addAll(result.getResultJsonArray().toJavaList(String.class));
                adapter.notifyDataSetChanged();
                if (browsers.isEmpty()) {
                    Snackbar s = Snackbar.make(activity.findViewById(android.R.id.content), R.string.notice_get_browsers_retry, Snackbar.LENGTH_INDEFINITE);
                    s.setAction(R.string.verify_ok, (view) -> initBrowsers());
                    s.show();
                } else {
                    succeed = true;
                }
            }
            Toast.makeText(activity, String.format(getString(R.string.get_browsers_bool_format), (succeed ? getString(R.string.succeed) : getString(R.string.failed))), Toast.LENGTH_SHORT).show();
        });
    }

    private void resultAppearancePost(CommandResult result) {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                succeed = result.getResultInt() == 1;
            }
            sendButton.setBackgroundColor(activity.getColor(succeed ? R.color.test_output_succeed_bg : R.color.test_output_failed_bg));
            activity.handler.postDelayed(() -> sendButton.setBackgroundColor(originOutputColor), 3000);
            Toast.makeText(activity, succeed ? "succeed" : "failed", Toast.LENGTH_SHORT).show();
        });
    }

    private class BrowsersChooserAdapter extends ArrayAdapter<String> {
        public BrowsersChooserAdapter() {
            super(activity, android.R.layout.simple_dropdown_item_1line, browsers);
        }
    }
}