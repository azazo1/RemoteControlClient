package com.azazo1.remotecontrolclient.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.azazo1.remotecontrolclient.ClientSocket;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.IPSearcher;
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
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class ConnectingActivity extends AppCompatActivity {
    private static final String cacheName = "IpCache";
    private final AtomicBoolean connectingRunning = new AtomicBoolean(false);
    private final AtomicBoolean searchingRunning = new AtomicBoolean(false);
    protected Toolbar toolbar;
    protected FloatingActionButton connectingFAB;
    protected FloatingActionButton searchFAB;
    protected EditText ipEntry;
    protected EditText portEntry;
    protected ProgressBar searchingProgressBar;
    protected Handler handler = new Handler();
    private final IPSearcher searcher = new IPSearcher((now, total, end) -> handler.post(() -> {
        searchingProgressBar.setMax(total);
        searchingProgressBar.setProgress(now);
        if (end) {
            searchingProgressBar.setVisibility(View.INVISIBLE);
        }
    }));
    private ProgressBar connectingProgressBar;
    private Thread searchingThread;
    private Thread connectingThread;

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
        if (!ip.isEmpty() && !port.isEmpty()) {
            int portInt = Integer.parseInt(port);
            connectingThread = new Thread(() -> {
                connectingRunning.set(true);
                connectingProgressBarShow();
                try {
                    if (Global.client != null) {
                        Global.client.close();
                    }
                    Global.client = new ClientSocket();
                    Global.client.connect(new InetSocketAddress(ip, portInt));
                    if (Global.client.isAvailable()) {
                        onConnected(ip, portInt);
                    } else {
                        onConnectFailed(ip, portInt);
                        onAuthenticateFailed();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    onConnectFailed(ip, portInt);
                }
                connectingRunning.set(false);
            });
            connectingThread.setDaemon(true);
            connectingThread.start();
        } else {
            Toast.makeText(this, R.string.noticeToComplete, Toast.LENGTH_SHORT).show();
        }
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
            Snackbar s = Snackbar.make(connectingFAB, getString(R.string.authenticate_failed), Snackbar.LENGTH_SHORT);
            s.show();
        });
    }

    protected void onConnected(String ip, int port) {
        if (!connectingRunning.get()) { // 被中断
            return;
        }
        saveAddress(ip, port);
        handler.post(() -> {
            Intent intent = new Intent(ConnectingActivity.this, CommandingActivity.class);
            intent.putExtra("ip", ip);
            intent.putExtra("port", port);
            ConnectingActivity.this.startActivity(intent);
        });
    }

    protected void onConnectFailed(String ip, int port) {
        if (!connectingRunning.get()) { // 被中断
            return;
        }
        handler.post(() -> {
                    try {
                        Global.client.close();
                    } catch (NullPointerException ignore) {
                    }
                    Global.client = null;
                    Snackbar s = Snackbar.make(connectingFAB, getString(R.string.connect_fail) + " " + ip + ":" + port, Snackbar.LENGTH_LONG);
                    s.show();
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
        connectingFAB = findViewById(R.id.connecting_fab);
        connectingFAB.setOnClickListener((view) -> connect());
        searchingProgressBar = findViewById(R.id.searching_progress_bar);
        connectingProgressBar = findViewById(R.id.connecting_progress_bar);
        searchFAB = findViewById(R.id.search_fab);
        searchFAB.setOnClickListener((view) -> search());
        Pair<String, Integer> address = readAddress();
        ipEntry.setText(address.first);
        portEntry.setText(String.valueOf(address.second));
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
        }
        searchingThread = new Thread(() -> { // 寻找局域网可用地址
            try {
                searchingRunning.set(true);
                handler.post(() -> searchingProgressBar.setVisibility(View.VISIBLE));
                Vector<String> result = searcher.searchAndReport();
                Log.e("Search", "Result: " + result);
                handler.post(() -> {
                    if (!result.isEmpty()) {
                        ipEntry.setText(result.elementAt(0));
                        portEntry.setText(String.valueOf(Config.serverPort));
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
    private void saveAddress(String ip, int port) {
        File cache = getExternalCacheDir();
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
                printer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取最近使用的IP地址
     */
    private Pair<String, Integer> readAddress() {
        File cache = getExternalCacheDir();
        File cacheFile = new File(cache.getAbsolutePath().concat(File.separator).concat(cacheName));
        String ip = null;
        int port = 2004;
        try {
            if (!cacheFile.exists()) {
                //noinspection unused
                boolean ignore = cacheFile.createNewFile();
            }
            if (cacheFile.canRead()) {
                BufferedReader reader = new BufferedReader(new FileReader(cacheFile));
                ip = reader.readLine();
                port = Integer.parseInt(reader.readLine());
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return new Pair<>(ip, port);
    }
}