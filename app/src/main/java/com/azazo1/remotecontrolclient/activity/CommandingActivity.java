package com.azazo1.remotecontrolclient.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.azazo1.remotecontrolclient.ClientSocket;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.fragment.ClipboardFragment;
import com.azazo1.remotecontrolclient.fragment.CommandLineFragment;
import com.azazo1.remotecontrolclient.fragment.DirFragment;
import com.azazo1.remotecontrolclient.fragment.LockScreenFragment;
import com.azazo1.remotecontrolclient.fragment.ProcessManagerFragment;
import com.azazo1.remotecontrolclient.fragment.ShowTextFragment;
import com.azazo1.remotecontrolclient.fragment.SurfWebsiteFragment;
import com.azazo1.remotecontrolclient.fragment.TestFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;


public class CommandingActivity extends AppCompatActivity {
    public final Handler handler = new Handler();
    protected final AtomicBoolean connectingRunning = new AtomicBoolean(false);
    public Fragment fragment;
    protected Toolbar toolbar;
    protected String ip;
    protected int port;
    protected TextView addressNotice;
    protected Thread connectingThread;
    protected DrawerLayout drawer;
    protected NavigationView navigation;
    protected Button chooseCommandButton;
    protected MyBackPressListener onBackPressAction = () -> false;

    public MyBackPressListener getBackPressAction() {
        return onBackPressAction;
    }

    public void setBackPressAction(MyBackPressListener action) {
        onBackPressAction = action;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);
        Global.activity = this;
        Global.commandingActivity = this;
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
        int color = ContextCompat.getColor(this, R.color.toolbar_commanding_bg);
        toolbar.setBackgroundColor(color);
    }

    private void initDrawerAndNavigation() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this,
                drawer, toolbar, R.string.nav_open_drawer, R.string.nav_close_drawer) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                navigation.requestFocus(); // 消去输入法
            }
        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigation.setNavigationItemSelectedListener(new NavSelected());
        chooseCommandButton.performClick(); // 自动打开抽屉
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
                if (!Global.client.connect(new InetSocketAddress(ip, port))) {
                    // authenticate 失败
                    handler.post(() -> {
                        TextView infoOut = new TextView(this);
                        LinearLayout linearLayout = new LinearLayout(this);
                        linearLayout.setPadding(20, 20, 20, 20);
                        linearLayout.addView(infoOut);
                        infoOut.setText(getString(R.string.authenticate_failed));
                        new AlertDialog.Builder(this).setTitle(R.string.authenticate_failed_title)
                                .setPositiveButton(R.string.verify_ok, (d, w) -> finish())
                                .setView(linearLayout)
                                .show();
                    });
                }
            } catch (IOException ignore) {
            }
            connectingRunning.set(false);
        });
        connectingThread.setDaemon(true);
        connectingThread.start();
    }

    @Override
    public void onBackPressed() {
        if (onBackPressAction.press()) {
            return;
        }
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

    public interface MyBackPressListener {
        // true to consume
        boolean press();
    }

    class NavSelected implements NavigationView.OnNavigationItemSelectedListener {
        public int lastID = -1;
        public MenuItem lastItem;

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            // 添加命令操作： 1.修改menu 2.在下面添加 elif 分支创建 fragment
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
            } else if (id == R.id.nav_show_text) {
                fragmentSelected = new ShowTextFragment();
            } else if (id == R.id.nav_clipboard) {
                fragmentSelected = new ClipboardFragment();
            } else if (id == R.id.nav_lock_screen) {
                fragmentSelected = new LockScreenFragment();
            } else if (id == R.id.nav_surf_website) {
                fragmentSelected = new SurfWebsiteFragment();
            } else if (id == R.id.nav_process_manager) {
                fragmentSelected = new ProcessManagerFragment();
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

