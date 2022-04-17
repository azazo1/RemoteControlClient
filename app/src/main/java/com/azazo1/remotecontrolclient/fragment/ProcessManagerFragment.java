package com.azazo1.remotecontrolclient.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.ToggleButton;

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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


public class ProcessManagerFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final List<Process> processes = new Vector<>();
    private final List<Process> filteredProcesses = new Vector<>();
    private final FilterTextWatcher filter = new FilterTextWatcher();
    private Button searchButton;
    private ToggleButton regexButton;
    private EditText searchInput;
    private TextView headerImageName;
    //    private TextView headerSessionName;
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
            }
            filter.filterUpdate();
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
            Collections.sort(filteredProcesses, comparator);
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
                    return o1.imageName.compareToIgnoreCase(o2.imageName);
//                    if (result != 0) {
//                        return result;
//                    } else {
//                        return o1.sessionName.compareTo(o2.sessionName); // 二级排序
//                    }
                }));
        headerPID.setOnClickListener(
                createSorterListener((o1, o2) -> Integer.compare(o1.pid, o2.pid)));
        headerMemoryUsage.setOnClickListener(
                createSorterListener((o1, o2) -> Integer.compare(o1.memoryUsage, o2.memoryUsage)));
//        headerSessionName.setOnClickListener(
//                createSorterListener((o1, o2) -> {
//                    int result = o1.sessionName.compareTo(o2.sessionName);
//                    if (result != 0) {
//                        return result;
//                    } else {
//                        return o1.imageName.compareTo(o2.imageName); // 二级排序
//                    }
//                }));
        regexButton.setOnCheckedChangeListener((view, checked) -> {
            filter.setRegex(checked);
            filter.filterUpdate();
        });
        searchInput.addTextChangedListener(filter);
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
//        headerSessionName = view.findViewById(R.id.session_name_header);
        headerMemoryUsage = view.findViewById(R.id.memory_usage_header);
        regexButton = view.findViewById(R.id.regex_toggle_button);
        adapter = new ProcessManagerListAdapter();
        initView();
        return view;
    }

    public void killProcess(int pid) {
        String command = String.format(getString(R.string.command_kill_process_pid_format), pid);
        sendCommand(command, killResultHandler);
    }

    public void killProcess(String imageName) {
        String command = String.format(getString(R.string.command_kill_process_image_name_format), JSON.toJSONString(imageName));
        sendCommand(command, killResultHandler);
    }

    public void queryProcess() { // 现在默认使用全查询加上本地过滤，使用效率更高
//        String input = searchInput.getText() + "";
        String command;
//        try { // pid搜索
//            command = String.format(getString(R.string.command_query_process_pid_format), Integer.parseInt(input));
//        } catch (NumberFormatException e) {
//            if (input.isEmpty()) { // 全查询
//                command = getString(R.string.command_query_process_all_string);
//            } else { // 按映像名称搜索
//                command = String.format(getString(R.string.command_query_process_name_format), JSON.toJSONString(input));
//            }
//        }
        command = getString(R.string.command_query_process_all_string);
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

    public class FilterTextWatcher implements TextWatcher {
        private boolean regex = false; // 正则匹配模式(true)与普通包含查询模式(false)

        public FilterTextWatcher() {
        }

        public void setRegex(boolean val) {
            regex = val;
        }

        /**
         * 筛选 processes 的值到 filteredProcesses 中，并进行视图更新
         */
        public void filterUpdate() {
            filteredProcesses.clear();
            String pattern = "" + searchInput.getText();
            for (Process i : processes) {
                if (pattern.isEmpty()) {
                    filteredProcesses.add(i);
                    continue;
                }
                // 比较 sessionName
                if (regex) {
                    try {
                        if (Pattern.compile(pattern).matcher(i.imageName).find()) {
                            filteredProcesses.add(i);
                        }
                    } catch (PatternSyntaxException ignore) {
                    }
                } else {
                    if (i.imageName.toLowerCase().contains(pattern.toLowerCase())) {
                        filteredProcesses.add(i);
                    }
                }
                // 比较 pid
                if (("" + i.pid).contains(pattern)) {
                    filteredProcesses.add(i);
                }
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            filterUpdate();
        }
    }

    public class ProcessManagerListAdapter extends ArrayAdapter<Process> implements AdapterView.OnItemClickListener {

        public ProcessManagerListAdapter() {
            super(activity, android.R.layout.simple_list_item_1, filteredProcesses); // 中间是默认的Layout
        }


        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewGroup rootLayout;
            // 利用 convertView 复用减少内存消耗
            if (convertView == null) {
                LayoutInflater inflater = activity.getLayoutInflater();
                rootLayout = (ViewGroup) inflater.inflate(R.layout.view_list_process_query, parent, false);
            } else {
                rootLayout = (ViewGroup) convertView;
            }

            if (rootLayout != null) {
                TextView imageNameOutput = rootLayout.findViewById(R.id.image_name_output);
//                TextView sessionNameOutput = rootLayout.findViewById(R.id.session_name_output);
                TextView pidOutput = rootLayout.findViewById(R.id.pid_output);
                TextView memoryOutput = rootLayout.findViewById(R.id.memory_usage_output);
                imageNameOutput.setText(filteredProcesses.get(position).imageName);
//                sessionNameOutput.setText(filteredProcesses.get(position).sessionName);
                pidOutput.setText(String.valueOf(filteredProcesses.get(position).pid));
                memoryOutput.setText(String.valueOf(filteredProcesses.get(position).memoryUsage));
                return rootLayout;
            }
            return super.getView(position, null, parent);
        }

        /**
         * 显示进程详细信息
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Process p = filteredProcesses.get(position);
            ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().
                    inflate(R.layout.alert_process_info, parent, false);
            if (rootLayout != null) {
                Button killPIDButton = rootLayout.findViewById(R.id.kill_pid_process_button);
                Button killINameButton = rootLayout.findViewById(R.id.kill_image_name_process_button);
                EditText imageName = rootLayout.findViewById(R.id.image_name_holder);
                EditText pid = rootLayout.findViewById(R.id.pid_holder);
                EditText sessionName = rootLayout.findViewById(R.id.session_name_holder);
                EditText memory = rootLayout.findViewById(R.id.memory_usage_holder);
                imageName.setText(p.imageName);
                pid.setText(String.valueOf(p.pid));
                sessionName.setText(p.sessionName);
                memory.setText(String.format(Locale.getDefault(), "%,d", p.memoryUsage));
                killPIDButton.setOnClickListener((view1) -> killProcess(p.pid));
                killINameButton.setOnClickListener((view1) -> killProcess(p.imageName));
                new AlertDialog.Builder(activity).setTitle(p.imageName).setView(rootLayout)
                        .setPositiveButton(R.string.verify_ok, null).show();
            }
        }
    }
}