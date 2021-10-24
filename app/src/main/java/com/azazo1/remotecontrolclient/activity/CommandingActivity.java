package com.azazo1.remotecontrolclient.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.azazo1.remotecontrolclient.ClientSocket;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.fragment.CommandLineFragment;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;


public class CommandingActivity extends AppCompatActivity {
    public CommandLineFragment fragment;
    public Handler handler = new Handler();
    protected Toolbar toolbar;
    protected String ip;
    protected int port;
    protected TextView addressNotice;
    private AtomicBoolean connectingRunning = new AtomicBoolean(false);
    private Thread connectingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);
        toolbar = findViewById(R.id.toolbar);
        addressNotice = findViewById(R.id.address_notice);
        Intent intent = getIntent();
        ip = intent.getStringExtra("ip");
        port = intent.getIntExtra("port", -1);
        addressNotice.setText(String.format(getString(R.string.address_notice_text_holder), ip, port, getString(R.string.connect_state_connected)));
        backgroundCheckClient();
    }

    protected void backgroundCheckClient() {
        Snackbar finishSnack = Snackbar.make(addressNotice, R.string.notice_disconnected, Snackbar.LENGTH_INDEFINITE);
        finishSnack.setAction(R.string.verify_reconnect, (view) -> reconnect());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!Global.client.isAvailable()) {
                    if (!finishSnack.isShown()) {
                        addressNotice.setText(String.format(getString(R.string.address_notice_text_holder), ip, port, getString(R.string.connect_state_disconnected)));
                        finishSnack.show();
                    }
                } else {
                    addressNotice.setText(String.format(getString(R.string.address_notice_text_holder), ip, port, getString(R.string.connect_state_connected)));
                }
                if (!CommandingActivity.this.isFinishing()) {
                    handler.postDelayed(this, (long) (1.0 / Config.loopingRate * 1000));
                }

            }
        }, (long) (1.0 / Config.loopingRate * 1000));
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    protected void reconnect() {
        if (connectingRunning.get()) {
            return;
        }
        connectingThread = new Thread(() -> {
            connectingRunning.set(true);
            try {
                if (Global.client != null) {
                    Global.client.close();
                }
                Global.client = new ClientSocket();
                Global.client.connect(new InetSocketAddress(ip, port));
            } catch (IOException ignore) {
            }
            connectingRunning.set(false);
        });
        connectingThread.setDaemon(true);
        connectingThread.start();
    }
}