package com.azazo1.remotecontrolclient.fragment;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.CommandResultHandler;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class ProcessManagerFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final List<Process> processes = new Vector<>();
    private Button searchButton;
    private EditText searchInput;
    private TextView headerImageName;
    private TextView headerSessionName;
    private TextView headerPID;
    private TextView headerMemoryUsage;
    private ListView processListView;
    private ProgressBar progressBar;
    private CommandingActivity activity;
    private final CommandResultHandler killResultHandler = (result) -> {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                int get = result.getResultInt();
                Toast.makeText(activity, get == 1 ? R.string.succeed : R.string.failed, Toast.LENGTH_SHORT).show();
            }
        });
    };
    private Thread sendingThread;
    private long sendingStartTime;
    private ProcessManagerListAdapter adapter;
    private final CommandResultHandler queryResultHandler = (result) -> {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            if (result != null && result.checkType(CommandResult.ResultType.ARRAY)) {
                JSONArray jRootArray = result.getResultJsonArray();
                if (jRootArray == null) {
                    return;
                }
                processes.clear();
                for (int i = 0; i < jRootArray.size(); i++) {
                    JSONArray jSonArray = jRootArray.getJSONArray(i);
                    processes.add(new Process(
                            jSonArray.getString(0),
                            jSonArray.getInteger(1),
                            jSonArray.getString(2),
                            jSonArray.getInteger(3)
                    ));
                }
                adapter.notifyDataSetChanged();
            }
        });
    };

    public ProcessManagerFragment() {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CommandingActivity) context;
        activity.fragment = this;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.process_manager_fragment_title)
        );
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    private View.OnClickListener createSorterListener(Comparator<Process> comparator) {
        return (view) -> {
            Collections.sort(processes, comparator);
            adapter.notifyDataSetChanged();
            Toast.makeText(activity, R.string.sorting_succeed, Toast.LENGTH_SHORT).show();
        };
    }

    private void initView() {
        searchButton.setOnClickListener((view) -> queryProcess());
        processListView.setAdapter(adapter);
        progressBar.setVisibility(View.INVISIBLE);
        processListView.setOnItemClickListener(adapter);
        // 排序
        headerImageName.setOnClickListener(
                createSorterListener((o1, o2) -> {
                    int result = o1.imageName.compareTo(o2.imageName);
                    if (result != 0) {
                        return result;
                    } else {
                        return o1.sessionName.compareTo(o2.sessionName); // 二级排序
                    }
                }));
        headerPID.setOnClickListener(
                createSorterListener((o1, o2) -> Integer.compare(o1.pid, o2.pid)));
        headerMemoryUsage.setOnClickListener(
                createSorterListener((o1, o2) -> Integer.compare(o1.memoryUsage, o2.memoryUsage)));
        headerSessionName.setOnClickListener(
                createSorterListener((o1, o2) -> {
                    int result = o1.sessionName.compareTo(o2.sessionName);
                    if (result != 0) {
                        return result;
                    } else {
                        return o1.imageName.compareTo(o2.imageName); // 二级排序
                    }
                }));
    }

    private void resetView() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_process_manager, container, false);
        searchButton = view.findViewById(R.id.process_search_button);
        searchInput = view.findViewById(R.id.process_search_input);
        processListView = view.findViewById(R.id.process_query_list);
        progressBar = view.findViewById(R.id.getting_command_result_progress_bar);
        headerImageName = view.findViewById(R.id.image_name_header);
        headerPID = view.findViewById(R.id.pid_header);
        headerSessionName = view.findViewById(R.id.session_name_header);
        headerMemoryUsage = view.findViewById(R.id.memory_usage_header);
        adapter = new ProcessManagerListAdapter();
        initView();
        return view;
    }

    public void killProcess(int pid) {
        String command = String.format(getString(R.string.command_close_process_pid_format), pid);
        sendCommand(command, killResultHandler);
    }

    public void queryProcess() {
        String input = searchInput.getText() + "";
        String command;
        try { // pid搜索
            command = String.format(getString(R.string.command_query_process_pid_format), Integer.parseInt(input));
        } catch (NumberFormatException e) {
            if (input.isEmpty()) { // 全查询
                command = getString(R.string.command_query_process_all_string);
            } else { // 按映像名称搜索
                command = String.format(getString(R.string.command_query_process_name_format), JSON.toJSONString(input));
            }
        }
        sendCommand(command, queryResultHandler);
    }

    private void sendCommand(String command, CommandResultHandler handler) {
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

    public static class Process {

        public final String imageName;
        public final int pid;
        public final String sessionName;
        public final int memoryUsage;

        public Process(String imageName, int pid, String sessionName, int memoryUsage) {
            this.imageName = imageName;
            this.pid = pid;
            this.sessionName = sessionName;
            this.memoryUsage = memoryUsage;
        }

    }

    public class ProcessManagerListAdapter extends ArrayAdapter<Process> implements AdapterView.OnItemClickListener {

        public ProcessManagerListAdapter() {
            super(activity, android.R.layout.simple_list_item_1, processes); // 中间是默认的Layout
        }


        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LayoutInflater inflater = activity.getLayoutInflater();
            @SuppressLint("ViewHolder")
            ViewGroup rootLayout = (ViewGroup) inflater.inflate(R.layout.view_list_process_query, parent, false);
            if (convertView != null && rootLayout != null) {
                TextView imageNameOutput = rootLayout.findViewById(R.id.image_name_output);
                TextView sessionNameOutput = rootLayout.findViewById(R.id.session_name_output);
                TextView pidOutput = rootLayout.findViewById(R.id.pid_output);
                TextView memoryOutput = rootLayout.findViewById(R.id.memory_usage_output);
                imageNameOutput.setText(processes.get(position).imageName);
                sessionNameOutput.setText(processes.get(position).sessionName);
                pidOutput.setText(String.valueOf(processes.get(position).pid));
                memoryOutput.setText(String.valueOf(processes.get(position).memoryUsage));
                return rootLayout;
            }
            return super.getView(position, convertView, parent);
        }

        /**
         * 显示进程详细信息
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Process p = processes.get(position);
            ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().
                    inflate(R.layout.alert_process_info, parent, false);
            if (rootLayout != null) {
                Button killButton = rootLayout.findViewById(R.id.kill_process_button);
                EditText imageName = rootLayout.findViewById(R.id.image_name_holder);
                EditText pid = rootLayout.findViewById(R.id.pid_holder);
                EditText sessionName = rootLayout.findViewById(R.id.session_name_holder);
                EditText memory = rootLayout.findViewById(R.id.memory_usage_holder);
                imageName.setText(p.imageName);
                pid.setText(String.valueOf(p.pid));
                sessionName.setText(p.sessionName);
                memory.setText(String.format(Locale.getDefault(), "%,d", p.memoryUsage));
                killButton.setOnClickListener((view1) -> killProcess(p.pid));
                new AlertDialog.Builder(activity).setTitle(p.imageName).setView(rootLayout)
                        .setPositiveButton(R.string.verify_ok, null).show();
            }
        }
    }
}