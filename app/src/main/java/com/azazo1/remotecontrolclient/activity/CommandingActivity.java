package com.azazo1.remotecontrolclient.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import com.azazo1.remotecontrolclient.fragment.BlankFragment;
import com.azazo1.remotecontrolclient.fragment.CaptureFragment;
import com.azazo1.remotecontrolclient.fragment.ClipboardFragment;
import com.azazo1.remotecontrolclient.fragment.CommandLineFragment;
import com.azazo1.remotecontrolclient.fragment.DirFragment;
import com.azazo1.remotecontrolclient.fragment.ExecuteFragment;
import com.azazo1.remotecontrolclient.fragment.LockScreenFragment;
import com.azazo1.remotecontrolclient.fragment.MouseFragment;
import com.azazo1.remotecontrolclient.fragment.ProcessManagerFragment;
import com.azazo1.remotecontrolclient.fragment.ShowTextFragment;
import com.azazo1.remotecontrolclient.fragment.SurfWebsiteFragment;
import com.azazo1.remotecontrolclient.fragment.TestFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class CommandingActivity extends AppCompatActivity {
    public final Handler handler = new Handler();
    protected final AtomicBoolean connectingRunning = new AtomicBoolean(false);
    public Fragment fragment;
    public int lastID = -1; // 上次选择的 Fragment
    public MenuItem lastItem; // 上次选择的 Fragment
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

    public boolean isConnecting() {
        return connectingRunning.get();
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
        changeFragment(null);
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar_commanding, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.toolbar_detach_fragment) {
            if (fragment != null) {
                detachFragment();
            } else {
                finish();
            }
            return true;
        } else if (item.getItemId() == R.id.toolbar_reconnect) {
            reconnect();
            Toast.makeText(this, R.string.reconnecting, Toast.LENGTH_SHORT).show();
        }
        return super.onOptionsItemSelected(item);
    }

    protected void backgroundCheckClient() {
        Snackbar snack = Snackbar.make(addressNotice, R.string.notice_disconnected, Snackbar.LENGTH_INDEFINITE);
        snack.setAction(R.string.verify_reconnect, (view) -> reconnect());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!Global.client.isAvailable() && !connectingRunning.get()) {// 若套接字无效且不是在重连状态
                    if (!snack.isShown()) {
                        addressNotice.setText(String.format(getString(R.string.address_notice_text_holder), ip, port, getString(R.string.connect_state_disconnected)));
                        snack.show();
                    }
                } else {
                    addressNotice.setText(String.format(getString(R.string.address_notice_text_holder), ip, port, getString(R.string.connect_state_connected)));
                }
                if (!CommandingActivity.this.isFinishing()) {
                    handler.postDelayed(this, (long) (1000.0 / Config.loopingRate));
                }

            }
        }, (long) (1000.0 / Config.loopingRate));
    }

    public void reconnect() {
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
                        infoOut.setPadding(20, 20, 20, 20);
                        infoOut.setText(getString(R.string.authenticate_failed));
                        new AlertDialog.Builder(this).setTitle(R.string.authenticate_failed_title)
                                .setPositiveButton(R.string.verify_ok, (d, w) -> finish())
                                .setView(infoOut)
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

    public void changeFragment(Fragment fragmentChange) {
        final Fragment finalFragmentChange = fragmentChange;
        handler.post(() -> {
            if (finalFragmentChange != null) {
                chooseCommandButton.setVisibility(View.INVISIBLE);
            } else {
                chooseCommandButton.setVisibility(View.VISIBLE);
            }
        });
        if (fragmentChange == null) {
            fragmentChange = new BlankFragment();
        }
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_holder, fragmentChange);
        ft.commit();
    }

    public void detachFragment() {
        if (fragment != null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.detach(fragment);
            ft.commit();
        }
        lastItem.setChecked(false);
        changeFragment(null);
    }

    public interface MyBackPressListener {
        // true to consume
        boolean press();
    }

    class NavSelected implements NavigationView.OnNavigationItemSelectedListener {
        protected HashMap<Integer, Fragment> fragments = new HashMap<>();

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            // 添加命令操作： 1.修改menu 2.在下面添加 elif 分支创建 fragment
            Fragment fragmentSelected;
            int id = item.getItemId();
            // 先检测有没有创建过该 ID 对应的 Fragment，且判断是否还有效（没销毁）
            if ((fragmentSelected = fragments.get(id)) != null) {
                if (fragmentSelected.isDetached()) {
                    fragmentSelected = null;
                }
            }
            // 重建 fragment
            if (fragmentSelected == null) {
                if (id == R.id.nav_test) {
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
                } else if (id == R.id.nav_execute) {
                    fragmentSelected = new ExecuteFragment();
                } else if (id == R.id.nav_mouse) {
                    fragmentSelected = new MouseFragment();
                } else if (id == R.id.nav_capture) {
                    fragmentSelected = new CaptureFragment();
                }
            }
            // 保存创建的 Fragment
            if (fragmentSelected != null) {
                fragments.put(id, fragmentSelected);
            }


            // update checked
            if (lastItem != null) {
                lastItem.setChecked(false);
            }
            if (fragmentSelected != null) {
                item.setChecked(true);
            }

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

