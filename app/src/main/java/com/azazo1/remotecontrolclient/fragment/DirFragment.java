package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSONArray;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.StringJoiner;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// todo 优化显示：显示当前路径 优化文件与文件夹名称显示 失败提醒 路径中途被删的解决
public class DirFragment extends Fragment {
    private CommandingActivity activity;
    private AtomicBoolean sending = new AtomicBoolean(false);
    private Thread sendingThread;
    private ProgressBar progressBar;
    private ListView dirShower;
    private ArrayAdapter<FileObj> adapter;
    private List<FileObj> dirList = new Vector<>();
    private String nowPath = "";

    public DirFragment() {
    }

    public static String join(String a, String... b) {
        StringJoiner stringJoiner = new StringJoiner(a);
        for (String c : b) {
            if (!c.isEmpty()) {
                stringJoiner.add(c);
            }
        }
        return stringJoiner.toString();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CommandingActivity) context;
        activity.fragment = this;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.dir_fragment_title)
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
        View get = inflater.inflate(R.layout.fragment_dir, container, false);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        dirShower = get.findViewById(R.id.dir_show_list_view);
        adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, dirList);
        initList();
        initView();
        return get;
    }

    private void initList() {
        dirList.clear();
        dirList.add(new FileObj(nowPath, ".."));
        adapter.notifyDataSetChanged();
    }

    private void initView() {
        dirShower.setAdapter(adapter);
        dirShower.setOnItemClickListener((listView, itemView, position, id) -> {
            FileObj obj = dirList.get((int) id);
            sendCommand(obj);
        });
        progressBar.setVisibility(View.INVISIBLE);
    }

    private void fetchDisks() {
        String command = getString(R.string.command_get_disks_string);
        if (Global.client.sendCommand(command)) {
            CommandResult result = Global.client.readCommand();
            resultAppearancePostOfDisks(result);
        }
    }

    public void sendCommand(FileObj obj) {
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending();

            nowPath = obj.getTotalPath(); // 迭代

            if (nowPath.equals("..") || nowPath.isEmpty()) { // 退出到了根目录
                nowPath = "";
                fetchDisks();
            } else {
                if (!nowPath.endsWith("/")) {
                    nowPath += "/";
                }
                String command = String.format(getString(R.string.command_dir_format_string), nowPath);
                if (Global.client.sendCommand(command)) {
                    CommandResult result = Global.client.readCommand();
                    resultAppearancePost(result);
                }
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
                    dirShower.setOnItemClickListener((listView, b, c, d) -> {
                        if (Tools.getTimeInMilli() - startTime > Config.waitingTimeForTermination) { // 防止连点触发
                            Snackbar s = Snackbar.make(listView, R.string.notice_still_sending, Snackbar.LENGTH_SHORT);
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
                    initView();
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }

    private void resultAppearancePost(CommandResult result) {
        activity.handler.post(() -> {
            if (result != null && result.type == CommandResult.ResultType.ARRAY) {
                initList();
                for (int i = 0; i < result.getResultJsonArray().size(); i++) {
                    JSONArray subArray = result.getResultJsonArray().getJSONArray(i);
                    String name = subArray.getString(0);
                    Boolean isFolder = subArray.getBoolean(1);
                    Long size = subArray.getLong(2);
                    if (!isFolder) {
                        dirList.add(new FileObj(nowPath, name, size));
                    } else {
                        dirList.add(new FileObj(nowPath, name));
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void resultAppearancePostOfDisks(CommandResult result) {
        activity.handler.post(() -> {
            if (result != null && result.type == CommandResult.ResultType.ARRAY) {
                initList();
                for (String disk : result.getResultJsonArray().toJavaList(String.class)) {
                    dirList.add(new FileObj(disk));
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    static class FileObj {
        // 一个文件夹或文件
        public final String path;
        public final String name;
        public final long size;
        public final FileType type;

        public FileObj(String path, String name) {
            this.path = path;
            this.name = name;
            this.type = FileType.FOLDER;
            this.size = -1;
        }

        public FileObj(String path, String name, long size) {
            this.path = path;
            this.name = name;
            this.size = size;
            if (size == -1) {
                this.type = FileType.FOLDER;
            } else {
                this.type = FileType.FILE;
            }
        }

        public FileObj(String name) {
            this.path = "";
            this.name = name;
            this.size = -1;
            this.type = FileType.DISK;
        }

        public String getTotalPath() {
            if (type == FileType.DISK) {
                return name + "/";
            }
            if (!path.isEmpty() && name.equals("..")) {
                String[] strings = path.split("/");
                strings[strings.length - 1] = "";
                return join("/", strings);
            } else if (path.isEmpty() && !name.isEmpty()) {
                return name;
            } else if (path.isEmpty()) {
                return "";
            }
            return (join("/", path.split("/")) + "/" + name);
        }

        @NonNull
        @Override
        public String toString() {
            return "FileObj{" +
                    "path='" + path + '\'' +
                    ", name='" + name + '\'' +
                    ", size=" + size +
                    ", type=" + type +
                    '}';
        }

        public enum FileType {
            FILE, FOLDER, DISK
        }
    }
}