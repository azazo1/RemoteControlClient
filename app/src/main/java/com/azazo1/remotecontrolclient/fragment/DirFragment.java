package com.azazo1.remotecontrolclient.fragment;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.MyReporter;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.StringJoiner;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.azazo1.remotecontrolclient.downloadhelper.Downloader;
import com.azazo1.remotecontrolclient.downloadhelper.FileDetail;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// todo "远程文件复制与移动" 功能
public class DirFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final List<FileObj> dirList = new Vector<>();
    private final List<FileObj> filteredDirList = new Vector<>();
    private final ItemFilterTextWatcher itemFilter = new ItemFilterTextWatcher();
    private CommandingActivity activity;
    private Thread sendingThread;
    private ProgressBar progressBar;
    private ListView dirShower;
    private ArrayAdapter<FileObj> adapter;
    private String nowPath = "";
    private EditText pathSelector;
    private Button selectButton;
    private CommandingActivity.MyBackPressListener rawPressAction;
    private long sendingStartTime = 0;
    private final View.OnClickListener sendingClickListener = (view) -> {
        if (Tools.getTimeInMilli() - sendingStartTime > Config.waitingTimeForTermination) { // 防止连点触发
            Snackbar s = Snackbar.make(view, R.string.notice_still_sending, Snackbar.LENGTH_SHORT);
            s.setAction(R.string.verify_terminate, (view1) -> {
                sending.set(false);
                if (sendingThread != null && !sendingThread.isInterrupted()) {
                    sendingThread.interrupt();
                }
            });
            s.show();
        }
    };

    public DirFragment() {
    }

    @NonNull
    public static String join(String a, @NonNull String... b) {
        StringJoiner stringJoiner = new StringJoiner(a);
        for (String c : b) {
            if (!c.isEmpty()) {
                stringJoiner.add(c);
            }
        }
        return stringJoiner.toString();
    }

    private final View.OnClickListener createDownloadFileListener(FileObj obj) {
        return (view1) -> {
            // ask store path
            EditText storePathText = new EditText(activity);
            storePathText.setText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString().concat(File.separator).concat(obj.name));
            new AlertDialog.Builder(activity).setTitle(R.string.local_store_path_title)
                    .setCancelable(false).setView(storePathText)
                    .setNegativeButton(R.string.verify_cancel_download, null).setCancelable(false)
                    .setPositiveButton(R.string.verify_ok, (dialog, which) -> {
                        // get path
                        String storePath = "" + storePathText.getText();
                        if (storePath.isEmpty()) {
                            Toast.makeText(activity, R.string.notice_invalid_path, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // create layout
                        ViewGroup layout = (ViewGroup) LayoutInflater.from(activity).inflate(R.layout.alert_downloading, dirShower, false);
                        ProgressBar progressBar = layout.findViewById(R.id.download_progress_bar);
                        TextView progressStateOutput = layout.findViewById(R.id.download_text_view);
                        // define reporter
                        MyReporter mReporter = new MyReporter() {
                            @Override
                            public void report(int now, int total) {
                                activity.handler.post(() -> {
                                    double progress = now * 100.0 / total;
                                    progressStateOutput.setText(
                                            String.format(getString(R.string.download_progress_format), progress, now, total)
                                    );
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        progressBar.setProgress((int) progress, true);
                                    } else {
                                        progressBar.setProgress((int) progress); // 适配低版本安卓
                                    }
                                });
                            }

                            @Override
                            public void reportEnd(int code) {
                                int stringID;
                                int color = getResources().getColor(R.color.failed);
                                switch (code) {
                                    case -1:
                                        stringID = R.string.download_communicating_problem;
                                        break;
                                    case 1:
                                        stringID = R.string.download_successfully;
                                        color = getResources().getColor(R.color.succeed);
                                        break;
                                    case 2:
                                        stringID = R.string.download_interrupted;
                                        break;
                                    case 3:
                                        stringID = R.string.download_sum_error;
                                        break;
                                    case 4:
                                        stringID = R.string.download_io_error;
                                        break;
                                    case 5:
                                        stringID = R.string.download_no_remote_file;
                                        break;
                                    case 6:
                                        stringID = R.string.download_remote_file_too_big;
                                        break;
                                    case 7:
                                        stringID = R.string.download_remote_file_access_failed;
                                        break;
                                    case 0:
                                    default:
                                        stringID = R.string.download_failed;
                                }
                                final int finalStringID = stringID;
                                final int finalColor = color;
                                activity.handler.post(() -> {
                                    progressStateOutput.setText(finalStringID);
                                    progressStateOutput.setTextColor(finalColor);
                                });
                            }
                        };
                        // download thread create
                        sendCommand(obj, storePath, mReporter);
                        // show downloading alert
                        new AlertDialog.Builder(activity).setTitle(R.string.alert_downloading_title)
                                .setView(layout).setNegativeButton(R.string.verify_terminate_or_ok, (dialog1, which1) -> Downloader.stopDownloading())
                                .setCancelable(false).show();
                    }).show();
        };
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CommandingActivity) context;
        activity.fragment = this;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.dir_fragment_title)
        );
        rawPressAction = activity.getBackPressAction();
        activity.setBackPressAction(() -> {
            final boolean result = !(checkIsRootPath());
            sendCommand(new FileObj(nowPath, ".."));
            return result;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activity.setBackPressAction(rawPressAction);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_dir, container, false);
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        dirShower = get.findViewById(R.id.dir_show_list_view);
        pathSelector = get.findViewById(R.id.path_selector);
        selectButton = get.findViewById(R.id.select_path_button);
        adapter = new DirAdapter(filteredDirList);
        initList();
        initView();
        sendCommand(""); // 自动在创建时获取磁盘
        return get;
    }

    private void initList() {
        dirList.clear();
        dirList.add(new FileObj(nowPath, ".."));
        itemFilter.filterUpdate();
    }

    private void initView() {
        pathSelector.setText(nowPath);
        pathSelector.requestFocus();
        pathSelector.setSelection(nowPath.length()); // 光标移动到末尾
        dirShower.setAdapter(adapter);
        AdapterView.OnItemLongClickListener itemLongClick = (listView, itemView, position, id) -> {
            ViewGroup layout = (ViewGroup) LayoutInflater.from(activity).inflate(R.layout.alert_file_info, dirShower, false);
            if (layout != null) {
                FileObj fileObj = filteredDirList.get(position);
                // get icon
                ImageView imageView = itemView.findViewById(R.id.file_icon_view);
                Drawable image = null;
                if (imageView != null) {
                    image = imageView.getDrawable();
                }
                // fill information
                EditText name = layout.findViewById(R.id.filename_edittext);
                EditText path = layout.findViewById(R.id.filepath_edittext);
                EditText size = layout.findViewById(R.id.file_size_edittext);
                name.setText(fileObj.name);
                path.setText(fileObj.getTotalPath());
                size.setText(String.valueOf(fileObj.size));
                // prepare launch and download action
                Button downloadButton = layout.findViewById(R.id.download_button);
                Button launchButton = layout.findViewById(R.id.launch_button);
                if (fileObj.type == FileObj.FileType.FILE) {
                    launchButton.setVisibility(View.VISIBLE);
                    launchButton.setOnClickListener((view) -> sendCommand(() -> startFile(fileObj.getTotalPath())));
                    downloadButton.setVisibility(View.VISIBLE);
                    downloadButton.setOnClickListener(createDownloadFileListener(fileObj));
                } else {
                    downloadButton.setVisibility(View.GONE);
                    launchButton.setVisibility(View.GONE);
                }
                // create alert
                new AlertDialog.Builder(activity).setTitle(getString(R.string.file_info_alert_title))
                        .setView(layout).setIcon(image).setPositiveButton(R.string.verify_ok, null).show();
            }
            return true;
        };
        dirShower.setOnItemClickListener((listView, itemView, position, id) -> {
            FileObj obj = filteredDirList.get(position);
            if (obj.type == FileObj.FileType.FILE) {
                // 如果是文件，单击直接显示信息
                itemLongClick.onItemLongClick(listView, itemView, position, id);
            } else {
                sendCommand(obj);
            }
        });
        // Long click -> alert infomation
        dirShower.setOnItemLongClickListener(itemLongClick);
        selectButton.setOnClickListener((view) -> {
            if (!(pathSelector.getText() + "").isEmpty()) {
                sendCommand(pathSelector.getText() + "");
            }
        });
        progressBar.setVisibility(View.INVISIBLE);
        pathSelector.addTextChangedListener(itemFilter);
    }

    private void startFile(@NonNull String path) {
        String command = String.format(getString(R.string.command_start_file_format),
                JSON.toJSONString(path));
        if (Global.client.sendCommand(command)) {
            CommandResult result = Global.client.readCommand();
            resultAppearancePostOfLaunch(result);
        }
    }

    private void fetchDisks() {
        String command = getString(R.string.command_get_disks_string);
        if (Global.client.sendCommand(command)) {
            CommandResult result = Global.client.readCommand();
            resultAppearancePostOfDisks(result);
        }
    }

    public void explorePath(String path) {
        String command = String.format(getString(R.string.command_dir_format), JSON.toJSONString(path));
        if (Global.client.sendCommand(command)) {
            CommandResult result = Global.client.readCommand();
            resultAppearancePost(result, path);
        }
    }

    /**
     * 包括了 explorePath 和 fetchDisks
     */
    public void genericExplorePath(@NonNull String path) {
        if (!path.isEmpty() && !path.endsWith("/")) { // 防止发送过去的路径是无效的
            path += "/";
        }
        if (checkIsRootPath(path)) { // 退出到了根目录
            fetchDisks();
        } else {
            explorePath(path);
        }
    }

    public void downloadFile(@NonNull FileObj obj, @NonNull String storePath, @NonNull MyReporter mReporter) {
        FileDetail fileDetail;
        fileDetail = Downloader.getFileDetail(obj.getTotalPath());
        if (fileDetail != null && fileDetail.available) {
            boolean ignored = Downloader.plainDownloadFile(fileDetail, storePath, mReporter);
        } else {
            Log.e("download", "no remote file or file is too big.");
            mReporter.reportEnd(7); // 见 Downloader.plainDownloadFile 注释
        }
    }

    public void sendCommand(@NonNull FileObj obj) {
        sendCommand(() -> genericExplorePath(obj.getTotalPath()));
    }

    public void sendCommand(@NonNull String path) {
        sendCommand(() -> genericExplorePath(path));
    }

    public void sendCommand(@NonNull FileObj obj, @NonNull String storePath, @NonNull MyReporter myReporter) {
        sendCommand(() -> downloadFile(obj, storePath, myReporter));
    }

    /**
     * 担当浏览磁盘，浏览文件夹和启动程序的作用
     */
    public void sendCommand(Runnable runnable) {
        if (sending.get()) { // 防止频繁发送
            sendingClickListener.onClick(activity.findViewById(android.R.id.content));
            return;
        }
        sendingThread = new Thread(() -> {
            sending.set(true);
            sendingStartTime = Tools.getTimeInMilli();
            whileSending();
            runnable.run();
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    private boolean checkIsRootPath() {
        return checkIsRootPath(nowPath);
    }

    private boolean checkIsRootPath(String path) {
        return path.equals("..") || path.isEmpty();
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
                } else {
                    initView();
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }

    private void resultAppearancePost(CommandResult result, String newPath) {
        if (!sending.get()) {
            return;
        }
        nowPath = newPath;
        if (!nowPath.isEmpty() && !nowPath.endsWith("/")) {
            nowPath += "/";
        }
        activity.handler.post(() -> {
            if (result != null && result.checkType(CommandResult.ResultType.ARRAY)) {
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
                Collections.sort(dirList);
                itemFilter.filterUpdate();
            } else {
                Toast.makeText(activity, R.string.notice_invalid_path, Toast.LENGTH_SHORT).show();
                sendCommand(new FileObj(nowPath, "..")); // 防止父目录丢失->递归退出
            }
        });
    }

    private void resultAppearancePostOfLaunch(CommandResult result) {
        if (!sending.get()) {
            return;
        }
        activity.handler.post(() -> {
            boolean succeed = false;
            if (result != null && result.checkType(CommandResult.ResultType.INT)) {
                succeed = result.getResultInt() == 1;
            }
            Toast.makeText(activity, succeed ? getString(R.string.succeed) : getString(R.string.failed), Toast.LENGTH_SHORT).show();
        });
    }

    private void resultAppearancePostOfDisks(CommandResult result) {
        if (!sending.get()) {
            return;
        }
        nowPath = "";
        activity.handler.post(() -> {
            if (result != null && result.checkType(CommandResult.ResultType.ARRAY)) {
                initList();
                for (String disk : result.getResultJsonArray().toJavaList(String.class)) {
                    dirList.add(new FileObj(disk));
                }
                itemFilter.filterUpdate();
            }
        });
    }

    static class FileObj implements Comparable<FileObj> {
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

        private static int typeToInt(@NonNull FileType type) {
            switch (type) {
                case DISK:
                    return 0;
                case FOLDER:
                    return 1;
                case FILE:
                    return 2;
            }
            return -1; // 貌似不可能
        }

        public String getTotalPath() {
            String convertedPath = path.replaceAll("\\\\", "/");
            if (type == FileType.DISK) {
                return name + "/";
            }
            if (!convertedPath.isEmpty() && name.equals("..")) {
                String[] strings = convertedPath.split("/");
                strings[strings.length - 1] = "";
                return join("/", strings);
            } else if (convertedPath.isEmpty() && !name.isEmpty()) {
                return name.equals("..") ? "" : name;
            } else if (convertedPath.isEmpty()) {
                return "";
            }
            return (join("/", convertedPath.split("/")) + "/" + name);
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

        @Override
        public int compareTo(@NonNull FileObj o) {
            if (type != o.type) {
                return typeToInt(type) - typeToInt(o.type);
            } else if (name.equals("..")) {
                return -1;
            } else if (o.name.equals("..")) {
                return 1;
// 若要将后缀名相同的文件放在一块：
//            } else if (type == FileType.FILE) { // 此时 o 也是FILE
//                String mExt = name.contains(".") ? name.substring(name.lastIndexOf(".")) : "";
//                String oExt = o.name.contains(".") ? o.name.substring(o.name.lastIndexOf(".")) : "";
//                if (mExt.equals(oExt)) {
//                    return name.compareToIgnoreCase(o.name);
//                } else {
//                    return mExt.compareToIgnoreCase(oExt);
//                }
            } else {
                return name.compareToIgnoreCase(o.name);
            }
        }

        public enum FileType {
            FILE, FOLDER, DISK
        }
    }

    class DirAdapter extends ArrayAdapter<FileObj> {
        final List<FileObj> fileObjs;

        public DirAdapter(List<FileObj> objs) {
            super(activity, R.layout.view_list_dir, objs);
            fileObjs = objs;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view;
            // 利用 convertView 复用减少内存消耗
            if (convertView == null) {
                LayoutInflater inflater = activity.getLayoutInflater();
                view = inflater.inflate(R.layout.view_list_dir, parent, false);
            } else {
                view = convertView;
            }

            if (view != null) {
                FileObj obj = fileObjs.get(position);
                ImageView icon = view.findViewById(R.id.file_icon_view);
                TextView title = view.findViewById(R.id.file_title_view);
                TextView subtitle = view.findViewById(R.id.file_subtitle_view);
                ImageView downloadButton = view.findViewById(R.id.download_button);
                switch (obj.type) {
                    case DISK: {
                        icon.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.disk));
                        title.setText(obj.name);
                        subtitle.setText(null);
                        downloadButton.setVisibility(View.INVISIBLE);
                        break;
                    }
                    case FILE: {
                        icon.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.file));
                        title.setText(obj.name);
                        subtitle.setText(String.format(getString(R.string.file_subtitle_format), obj.size));
                        downloadButton.setVisibility(View.VISIBLE);
                        // download action
                        downloadButton.setOnClickListener(createDownloadFileListener(obj));
                        break;
                    }
                    case FOLDER: {
                        icon.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.folder));
                        title.setText(obj.name);
                        subtitle.setText(null);
                        downloadButton.setVisibility(View.INVISIBLE);
                        break;
                    }
                    default:
                }
                return view;
            }
            return super.getView(position, null, parent);
        }
    }

    public class ItemFilterTextWatcher implements TextWatcher {

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

        /**
         * 通过输入框过滤 dirList 中的元素到 filteredDirList 中
         */
        public void filterUpdate() {
            String input = (pathSelector.getText() + "").replaceAll("\\\\", "/");
            // 找到输入框中最后一层文件名（没被 "/" 或 "\" 包围）
            Matcher matcher = Pattern.compile("/?([^/]+)$").matcher(input);
            String pattern = null;
            if (matcher.find()) {
                pattern = matcher.group(1);
            }
            // 找到输入框中最后一层之前的内容
            String prefix = "";
            if (input.contains("/")) {
                prefix = input.substring(0, input.lastIndexOf("/") + 1);
            }
            filteredDirList.clear();
            for (FileObj i : dirList) {
                if (prefix.equalsIgnoreCase(nowPath.replaceAll("\\\\", "/"))) {
                    if (i.name.equals("..") || pattern == null || i.name.toLowerCase().contains(pattern.toLowerCase())) {
                        filteredDirList.add(i);
                    }
                } else {
                    filteredDirList.add(i);
                }
            }
            adapter.notifyDataSetChanged();
        }
    }
}