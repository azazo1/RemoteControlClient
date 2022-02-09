package com.azazo1.remotecontrolclient.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.azazo1.remotecontrolclient.ClientSocket;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.IPSearcher;
import com.azazo1.remotecontrolclient.MyReporter;
import com.azazo1.remotecontrolclient.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class ConnectingActivity extends AppCompatActivity {
    private static final String cacheName = "IpCache";
    private final AtomicBoolean connectingRunning = new AtomicBoolean(false);
    private final AtomicBoolean searchingRunning = new AtomicBoolean(false);
    private final int reqCode = 1;
    protected Toolbar toolbar;
    protected FloatingActionButton connectingFAB;
    protected FloatingActionButton searchFAB;
    protected EditText ipEntry;
    protected EditText portEntry;
    protected ProgressBar searchingProgressBar;
    private final IPSearcher searcher = new IPSearcher(new MyReporter() {
        @Override
        public void report(int now, int total) {
            searchingProgressBar.setMax(total);
            searchingProgressBar.setProgress(now);
        }

        @Override
        public void reportEnd(int code) {
            searchingProgressBar.setVisibility(View.INVISIBLE);
        }
    }, Config.serverPort);
    protected Handler handler = new Handler();
    private ProgressBar connectingProgressBar;
    private Thread searchingThread;
    private Thread connectingThread;
    private EditText passwordEntry;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Global.client != null) {
            Global.client.close();
        }
    }

    protected void connect() {
        if (connectingRunning.get()) {
            Snackbar s = Snackbar.make(connectingFAB, R.string.notice_connecting_running, Snackbar.LENGTH_SHORT);
            s.setAction(R.string.verify_terminate, (view1) -> {
                connectingRunning.set(false);
                if (connectingThread != null && connectingThread.isAlive()) {
                    connectingThread.interrupt();
                }
            });
            s.show();
            return;
        }
        String ip = ipEntry.getText().toString();
        String port = portEntry.getText().toString();
        String password = passwordEntry.getText().toString();
        if (password.isEmpty()) {
            password = Config.key;
        }
        if (!ip.isEmpty() && !port.isEmpty()) {
            int portInt = Integer.parseInt(port);
            final String finalPassword = password;
            connectingThread = new Thread(() -> {
                connectingRunning.set(true);
                connectingProgressBarShow();
                try {
                    if (Global.client != null) {
                        Global.client.close();
                    }
                    Global.client = new ClientSocket();
                    Global.client.connect(new InetSocketAddress(ip, portInt), finalPassword);
                    if (Global.client.isAvailable()) {
                        onConnected(ip, portInt, finalPassword);
                    } else {
                        onAuthenticateFailed();
                    }
                } catch (SocketTimeoutException e) {
                    onConnectingTimeOut();
                } catch (IOException e) {
                    e.printStackTrace();
                    onConnectFailed(ip, portInt, e.getMessage());
                }
                connectingRunning.set(false);
            });
            connectingThread.setDaemon(true);
            connectingThread.start();
        } else {
            Toast.makeText(this, R.string.notice_to_complete, Toast.LENGTH_SHORT).show();
        }
    }

    private void onConnectingTimeOut() {
        handler.post(() -> {
            try {
                Global.client.close();
            } catch (NullPointerException ignore) {
            }
            Global.client = null;
//            Snackbar s = Snackbar.make(connectingFAB, getString(R.string.connecting_timeout), Snackbar.LENGTH_SHORT);
//            s.show();
            TextView infoOut = new TextView(this);
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setPadding(20, 20, 20, 20);
            linearLayout.addView(infoOut);
            infoOut.setText(getString(R.string.connecting_timeout));
            new AlertDialog.Builder(this).setTitle(R.string.connecting_timeout_title)
                    .setPositiveButton(R.string.verify_ok, null)
                    .setView(linearLayout)
                    .show();
        });
    }

    private void connectingProgressBarShow() {
        AtomicInteger progress = new AtomicInteger();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (connectingRunning.get()) {
                    connectingProgressBar.setVisibility(View.VISIBLE);
                    connectingProgressBar.setProgress(progress.getAndIncrement());
                    progress.compareAndSet(100, 0);
                    handler.postDelayed(this, (long) (1.0 / Config.loopingRate * 1000));
                } else {
                    connectingProgressBar.setVisibility(View.INVISIBLE);
                    progress.set(0);
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }

    private void onAuthenticateFailed() {
        handler.post(() -> {
            try {
                Global.client.close();
            } catch (NullPointerException ignore) {
            }
            Global.client = null;
//            Snackbar s = Snackbar.make(connectingFAB, getString(R.string.authenticate_failed), Snackbar.LENGTH_SHORT);
//            s.show();
            TextView infoOut = new TextView(this);
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setPadding(20, 20, 20, 20);
            linearLayout.addView(infoOut);
            infoOut.setText(getString(R.string.authenticate_failed));
            new AlertDialog.Builder(this).setTitle(R.string.authenticate_failed_title)
                    .setPositiveButton(R.string.verify_ok, null)
                    .setView(linearLayout)
                    .show();
        });
    }

    protected void onConnected(String ip, int port, String password) {
        if (!connectingRunning.get()) { // 被中断
            return;
        }
        saveAddress(ip, port, password);
        handler.post(() -> {
            Config.key = password;
            Intent intent = new Intent(ConnectingActivity.this, CommandingActivity.class);
            intent.putExtra("ip", ip);
            intent.putExtra("port", port);
            ConnectingActivity.this.startActivity(intent);
        });
    }

    protected void onConnectFailed(String ip, int port, String message) {
        if (!connectingRunning.get()) { // 被中断
            return;
        }
        handler.post(() -> {
                    try {
                        Global.client.close();
                    } catch (NullPointerException ignore) {
                    }
                    Global.client = null;
//                    Snackbar s = Snackbar.make(connectingFAB, getString(R.string.connect_fail) + "\n" + message + "\n[" + ip + "]:" + port, Snackbar.LENGTH_LONG);
//                    s.show();
                    TextView infoOut = new TextView(this);
                    LinearLayout linearLayout = new LinearLayout(this);
                    linearLayout.setPadding(20, 20, 20, 20);
                    linearLayout.addView(infoOut);
                    infoOut.setText(String.format(Locale.getDefault(), "%s\n[%s]:%d", message, ip, port));
                    new AlertDialog.Builder(this).setTitle(R.string.connect_fail)
                            .setPositiveButton(R.string.verify_ok, null)
                            .setView(linearLayout)
                            .show();
                }
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connecting);
        Global.activity = this;
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(Config.title);

        ipEntry = findViewById(R.id.ip_entry);
        portEntry = findViewById(R.id.port_entry);
        passwordEntry = findViewById(R.id.password_entry);
        searchFAB = findViewById(R.id.search_fab);
        connectingFAB = findViewById(R.id.connecting_fab);
        connectingFAB.setOnClickListener((view) -> connect());
        searchingProgressBar = findViewById(R.id.searching_progress_bar);
        connectingProgressBar = findViewById(R.id.connecting_progress_bar);
        searchFAB.setOnClickListener((view) -> search());
        portEntry.setText(String.valueOf(Config.serverPort));
        // 请求权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, reqCode);
            }
            return;
        }

        if (loadAddress() != null && savedInstanceState == null) {
            connect(); // 启动应用后自动登录，退出CommandingActivity则不会
        }
    }


    protected void search() {
        // 询问是否中断
        if (searchingRunning.get()) {
            Snackbar s = Snackbar.make(ipEntry, R.string.notice_searching_running, Snackbar.LENGTH_SHORT);
            s.setAction(R.string.verify_terminate, (view) -> {
                if (searchingThread != null && searchingThread.isAlive()) {
                    if (searcher != null) {
                        searcher.stop();
                    }
                    searchingThread.interrupt();
                }
            });
            s.show();
            return;
        }
        // 读取用户输入端口号
        try {
            int port = Integer.parseInt(portEntry.getText() + "");
            if (port >= 0 && port <= 65535) {
                searcher.setTargetPort(port);
            } else {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ignored) {
            Toast.makeText(this, R.string.notice_to_correct_port, Toast.LENGTH_SHORT).show();
            return;
        }
        // 显示进度条
        searchingProgressBar.setVisibility(View.VISIBLE);
        searchingThread = new Thread(() -> { // 寻找局域网可用地址
            try {
                searchingRunning.set(true);
                Vector<String> result = searcher.searchAndReport();
                Log.e("Search", "Result: " + result);
                handler.post(() -> {
                    if (!result.isEmpty()) {
                        ipEntry.setText(result.elementAt(0));
                    } else {
                        Toast.makeText(ConnectingActivity.this, R.string.searchingNoResult, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                searchingRunning.set(false);
            }
        });
        searchingThread.setDaemon(true);
        searchingThread.start();
    }

    /**
     * 保存最近使用的IP地址
     */
    private void saveAddress(String ip, int port, String password) {
        File cache = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File cacheFile = new File(cache.getAbsolutePath().concat(File.separator).concat(cacheName));
        try {
            if (!cacheFile.exists()) {
                //noinspection unused
                boolean ignore = cacheFile.createNewFile();
            }
            if (cacheFile.canWrite()) {
                PrintWriter printer = new PrintWriter(new FileOutputStream(cacheFile));
                printer.println(ip);
                printer.println(port);
                printer.println(password);
                printer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取最近使用的IP地址与密码 读取内容可能为null
     */
    @Nullable
    private Pair<Pair<String, Integer>, String> readAddress() {
        File cache = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File cacheFile = new File(cache.getAbsolutePath().concat(File.separator).concat(cacheName));
        String ip = null;
        String password = null;
        int port = Config.serverPort;
        try {
            if (!cacheFile.exists()) {
                //noinspection unused
                boolean ignore = cacheFile.createNewFile();
            }
            if (cacheFile.canRead()) {
                BufferedReader reader = new BufferedReader(new FileReader(cacheFile));
                ip = reader.readLine();
                port = Integer.parseInt(reader.readLine());
                password = reader.readLine();
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            return null;
        }
        return new Pair<>(new Pair<>(ip, port), password);
    }

    /**
     * 读取并将 address 加载到页面
     */
    @Nullable
    private Pair<Pair<String, Integer>, String> loadAddress() {
        Pair<Pair<String, Integer>, String> lastUsed = readAddress();
        if (lastUsed != null) {
            ipEntry.setText(lastUsed.first.first);
            portEntry.setText(String.valueOf(lastUsed.first.second));
            passwordEntry.setText(lastUsed.second);
        }
        return lastUsed;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == reqCode) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                loadAddress();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, reqCode);
            }
        }
    }
}