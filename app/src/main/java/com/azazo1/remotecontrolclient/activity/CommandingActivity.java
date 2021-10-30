package com.azazo1.remotecontrolclient.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.azazo1.remotecontrolclient.ClientSocket;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.fragment.CommandLineFragment;
import com.azazo1.remotecontrolclient.fragment.DirFragment;
import com.azazo1.remotecontrolclient.fragment.TestFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;


public class CommandingActivity extends AppCompatActivity {
    protected final AtomicBoolean connectingRunning = new AtomicBoolean(false);
    public Fragment fragment;
    public Handler handler = new Handler();
    protected Toolbar toolbar;
    protected String ip;
    protected int port;
    protected TextView addressNotice;
    protected Thread connectingThread;
    protected DrawerLayout drawer;
    protected NavigationView navigation;
    protected Button chooseCommandButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);
        addressNotice = findViewById(R.id.address_notice);
        Intent intent = getIntent();
        ip = intent.getStringExtra("ip");
        port = intent.getIntExtra("port", -1);
        toolbar = findViewById(R.id.toolbar);
        addressNotice.setText(String.format(getString(R.string.address_notice_text_holder), ip, port, getString(R.string.connect_state_connected)));
        drawer = findViewById(R.id.drawer_layout);
        navigation = findViewById(R.id.nav_view);
        chooseCommandButton = findViewById(R.id.choose_command_button);
        initChooseCommandButton();
        initActionBar();
        initDrawerAndNavigation();
        initFragment();
        backgroundCheckClient();
    }

    private void initChooseCommandButton() {
        chooseCommandButton.setOnClickListener((view) -> {
            drawer.openDrawer(GravityCompat.START);
        });
    }

    private void initActionBar() {
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(getColor(R.color.toolbar_commanding_bg));
    }

    private void initDrawerAndNavigation() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this,
                drawer, toolbar, R.string.nav_open_drawer, R.string.nav_close_drawer);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigation.setNavigationItemSelectedListener(new NavSelected());
    }

    private void initFragment() {
//        changeFragment(new BlankFragment());
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

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void changeFragment(Fragment fragmentChange) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_holder, fragmentChange);
        ft.commit();
        handler.post(() -> {
            if (fragment != null) {
                chooseCommandButton.setVisibility(View.INVISIBLE);
            }
        });
    }

    class NavSelected implements NavigationView.OnNavigationItemSelectedListener {
        public int lastID = -1;
        public MenuItem lastItem;

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            Fragment fragmentSelected;
            int id = item.getItemId();

            // choose fragment
            if (id == lastID) {
                item.setChecked(true);
                return true;
            } else if (id == R.id.nav_test) {
                fragmentSelected = new TestFragment();
            } else if (id == R.id.nav_command_line) {
                fragmentSelected = new CommandLineFragment();
            } else if (id == R.id.nav_dir) {
                fragmentSelected = new DirFragment();
            } else {
                fragmentSelected = new CommandLineFragment();
            }

            // update checked
            if (lastItem != null) {
                lastItem.setChecked(false);
            }
            item.setChecked(true);

            // update last
            lastItem = item;
            lastID = id;

            // appearance
            changeFragment(fragmentSelected);
            drawer.closeDrawer(GravityCompat.START);
            return true;
        }
    }
}

