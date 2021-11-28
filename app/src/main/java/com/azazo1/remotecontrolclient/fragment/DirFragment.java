package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

// todo "下载" "启动" "复制路径" "同机文件复制与移动" 功能
public class DirFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final List<FileObj> dirList = new Vector<>();
    private CommandingActivity activity;
    private Thread sendingThread;
    private ProgressBar progressBar;
    private ListView dirShower;
    private ArrayAdapter<FileObj> adapter;
    private String nowPath = "";
    private EditText pathSelector;
    private Button selectButton;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_dir, container, false);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        dirShower = get.findViewById(R.id.dir_show_list_view);
        pathSelector = get.findViewById(R.id.path_selector);
        selectButton = get.findViewById(R.id.select_path_button);
        adapter = new DirAdapter(dirList);
        initList();
        initView();
        sendCommand(""); // 自动在创建时获取磁盘
        return get;
    }

    private void initList() {
        dirList.clear();
        dirList.add(new FileObj(nowPath, ".."));
        adapter.notifyDataSetChanged();
    }

    private void initView() {
        pathSelector.setText(nowPath);
        dirShower.setAdapter(adapter);
        dirShower.setOnItemClickListener((listView, itemView, position, id) -> {
            FileObj obj = dirList.get((int) id);
            sendCommand(obj);
        });
        selectButton.setOnClickListener((view) -> {
            if (!(pathSelector.getText() + "").isEmpty()) {
                sendCommand(pathSelector.getText() + "");
            }
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

    public void explorePath(String path) {
        String command = String.format(getString(R.string.command_dir_format_string), JSON.toJSONString(path));
        if (Global.client.sendCommand(command)) {
            CommandResult result = Global.client.readCommand();
            resultAppearancePost(result);
        }
    }

    public void sendCommand(FileObj obj) {
        sendCommand(obj.getTotalPath());
    }

    public void sendCommand(String path) {
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending();

            nowPath = path; // 迭代

            if (nowPath.equals("..") || nowPath.isEmpty()) { // 退出到了根目录
                nowPath = "";
                fetchDisks();
            } else {
                if (!nowPath.endsWith("/")) {
                    nowPath += "/";
                }
                explorePath(nowPath);
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
            } else {
                Toast.makeText(activity, R.string.notice_invalid_path, Toast.LENGTH_SHORT).show();
                sendCommand(new FileObj(nowPath, "..")); // 防止父目录丢失->递归退出
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

    class DirAdapter extends ArrayAdapter<FileObj> {
        List<FileObj> fileObjs;

        public DirAdapter(List<FileObj> objs) {
            super(activity, R.layout.view_list_dir, objs);
            fileObjs = objs;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LayoutInflater inflater = activity.getLayoutInflater();
            View view = inflater.inflate(R.layout.view_list_dir, parent, false);
            if (convertView != null) {
                FileObj obj = fileObjs.get(position);
                ImageView icon = view.findViewById(R.id.file_icon);
                TextView title = view.findViewById(R.id.file_title);
                TextView subtitle = view.findViewById(R.id.file_subtitle);
                switch (obj.type) {
                    case DISK:
                        icon.setImageDrawable(activity.getDrawable(R.drawable.disk));
                        title.setText(obj.name);
                        subtitle.setText(getString(R.string.disk_subtitle_format));
                        break;
                    case FILE:
                        icon.setImageDrawable(activity.getDrawable(R.drawable.file));
                        title.setText(obj.name);
                        subtitle.setText(String.format(getString(R.string.file_subtitle_format), obj.size, obj.path));
                        break;
                    case FOLDER:
                        icon.setImageDrawable(activity.getDrawable(R.drawable.folder));
                        title.setText(obj.name);
                        subtitle.setText(String.format(getString(R.string.folder_subtitle_format), obj.path));
                        break;
                    default:
                }
            }
            return view;
        }
    }
}