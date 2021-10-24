package com.azazo1.remotecontrolclient.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectingActivity extends AppCompatActivity {
    protected Toolbar toolbar;
    protected FloatingActionButton connectingFAB;
    protected FloatingActionButton searchFAB;
    protected EditText ipEntry;
    protected EditText portEntry;
    protected ProgressBar searchingProgressBar;
    protected Handler handler = new Handler();
    protected AtomicBoolean connectingRunning = new AtomicBoolean(false);
    private ProgressBar connectingProgressBar;
    private Thread connectingThread;
    private Thread searchingThread;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Global.client.close();
    }

    protected void connect() {
        if (connectingRunning.get()) {
            Snackbar s = Snackbar.make(connectingFAB, R.string.noticeConnectingRunning, Snackbar.LENGTH_SHORT);
            s.setAction(R.string.verifyTerminate, (view1) -> {
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

    protected void onConnected(String ip, int port) {
        if (!connectingRunning.get()) { // 被中断
            return;
        }
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
                    Snackbar s = Snackbar.make(connectingFAB, getString(R.string.connect_fail) + " " + ip + ":" + port, Snackbar.LENGTH_INDEFINITE);
                    s.show();
                }
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connecting);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getTitle());

        ipEntry = findViewById(R.id.ip_entry);
        portEntry = findViewById(R.id.port_entry);
        connectingFAB = findViewById(R.id.connecting_fab);
        connectingFAB.setOnClickListener((view) -> connect());
        searchingProgressBar = findViewById(R.id.searching_progress_bar);
        connectingProgressBar = findViewById(R.id.connecting_progress_bar);
        searchFAB = findViewById(R.id.search_fab);
        searchFAB.setOnClickListener((view) -> search());
    }

    protected void search() {
        if (searchingThread != null && searchingThread.isAlive()) {
            searchingThread.interrupt();
        }
        searchingThread = new Thread(() -> { // 寻找局域网可用地址
            try {
                handler.post(() -> searchingProgressBar.setVisibility(View.VISIBLE));
                IPSearcher searcher = new IPSearcher((now, total, end) -> {
                    handler.post(() -> {
                        searchingProgressBar.setMax(total);
                        searchingProgressBar.setProgress(now);
                        if (end) {
                            searchingProgressBar.setVisibility(View.INVISIBLE);
                        }
                    });
                });
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
            }
        });
        searchingThread.setDaemon(true);
        searchingThread.start();
    }
}