package com.azazo1.remotecontrolclient.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;

/**
 * 下部鼠标按键: 短按可实现单击, 长按可模拟鼠标按住不放, 若按键为中键, 滑动可模拟鼠标滚轮(水平和垂直)<br>
 * <br>
 * 中间操控板: 滑动模式, 摇杆模式
 * todo 摇杆模式
 */
public class MouseFragment extends Fragment {
    private final int defaultMovingInterval_ms = 60; // 默认滑动命令发送时间间隔 (ms)
    private int movingInterval_ms = defaultMovingInterval_ms; // 当前滑动命令发送时间间隔 (ms)
    private CommandingActivity activity;
    private int originButtonColor;
    private View touchPad;
    private Thread sendingThread;
    private Button leftButton;
    private SeekBar frequencyAdjustor;
    private TextView frequencyAdjustorText;
    private Button middleButton;
    private Button scrollButton;
    private long lastMidButtonMovingTime = 0; // 上次滚轮滑动时间
    private long lastMouseMovingTime = 0; // 上次鼠标滑动时间
    private Button rightButton;

    public MouseFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CommandingActivity) context;
        activity.fragment = this;
        activity.handler.post(
                () -> activity.getToolbar().setTitle(R.string.mouse_fragment_title)
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View get = inflater.inflate(R.layout.fragment_mouse, container, false);
        touchPad = get.findViewById(R.id.mouse_touch_pad);

        leftButton = get.findViewById(R.id.mouse_left_button);
        middleButton = get.findViewById(R.id.mouse_middle_button);
        rightButton = get.findViewById(R.id.mouse_right_button);
        scrollButton = get.findViewById(R.id.mouse_scroll_button);
        frequencyAdjustor = get.findViewById(R.id.mouse_frequency_adjustor);
        frequencyAdjustorText = get.findViewById(R.id.mouse_frequency_adjustor_text);

        leftButton.setTag(MouseButtons.LEFT.getVal());
        rightButton.setTag(MouseButtons.RIGHT.getVal());
        middleButton.setTag(MouseButtons.MIDDLE.getVal());
        scrollButton.setTag(new int[]{-1, -1});
        /* Button view tag: 按下的按钮(int), 中键为上一次的手指坐标(为了计算位移)(Pair<int,int>)*/
        initView();
        noticeIssue();
        return get;
    }

    /**
     * 提示用户不要在使用鼠标时使用其他功能
     */
    private void noticeIssue() {
        Toast.makeText(activity, R.string.mouse_issue_toast, Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initView() {
        originButtonColor = ContextCompat.getColor(activity, R.color.generic_sending_button_bg);

        leftButton.setBackgroundColor(originButtonColor);
        rightButton.setBackgroundColor(originButtonColor);
        middleButton.setBackgroundColor(originButtonColor);
        scrollButton.setBackgroundColor(originButtonColor);

        leftButton.setOnTouchListener(this::mouseButtonLRMTouch);
        middleButton.setOnTouchListener(this::mouseButtonLRMTouch);
        rightButton.setOnTouchListener(this::mouseButtonLRMTouch);
        scrollButton.setOnTouchListener(this::mouseScrollTouch);

        touchPad.setOnTouchListener(this::mouseMotionTouch);

        frequencyAdjustor.setProgress(defaultMovingInterval_ms);
        frequencyAdjustorText.setText(String.format(getString(R.string.mouse_frequency_adjustor_format), frequencyAdjustor.getProgress()));
        frequencyAdjustor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                frequencyAdjustorText.setText(String.format(getString(R.string.mouse_frequency_adjustor_format), frequencyAdjustor.getProgress()));
                movingInterval_ms = frequencyAdjustor.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    /**
     * 鼠标左中右按键的按下和释放
     */
    public boolean mouseButtonLRMTouch(@NonNull View view, @NonNull MotionEvent event) {
        int button = (int) view.getTag();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                sendCommandClick(MouseAction.PRESS.getVal(), button, 0, 0, 0);
                break;
            }
            case MotionEvent.ACTION_UP: {
                sendCommandClick(MouseAction.RELEASE.getVal(), button, 0, 0, 0);
                break;
            }
        }
        return true;
    }

    /**
     * 鼠标滚轮的滚动
     */
    public boolean mouseScrollTouch(@NonNull View view, @NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                int[] pos = (int[]) view.getTag();
                int deltaX = (int) (pos[0] - event.getRawX());
                int deltaY = (int) (event.getRawY() - pos[1]);
                long nowTime = Tools.getTimeInMilli();
                if (nowTime - lastMidButtonMovingTime > movingInterval_ms) {
                    sendCommandScroll((int) (deltaX * 0.05), (int) (deltaY * 0.05));
                    view.setTag(new int[]{(int) event.getRawX(), (int) event.getRawY()});
                    lastMidButtonMovingTime = nowTime;
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                view.setTag(new int[]{(int) event.getRawX(), (int) event.getRawY()});
                break;
            }
            case MotionEvent.ACTION_UP: {
                view.setTag(new int[]{-1, -1});
                break;
            }
        }
        return true;
    }

    /**
     * 鼠标的移动
     */
    public boolean mouseMotionTouch(View view, @NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                int[] pos = (int[]) view.getTag();
                int deltaX = (int) (event.getRawX() - pos[0]);
                int deltaY = (int) (event.getRawY() - pos[1]);
                long nowTime = Tools.getTimeInMilli();
                if (nowTime - lastMouseMovingTime > movingInterval_ms) {
                    sendCommandMotion(deltaX, deltaY);
                    view.setTag(new int[]{(int) event.getRawX(), (int) event.getRawY()});
                    lastMouseMovingTime = nowTime;
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                view.setTag(new int[]{(int) event.getRawX(), (int) event.getRawY()});
                break;
            }
            case MotionEvent.ACTION_UP: {
                view.setTag(null);
                break;
            }

        }
        return true;
    }

    /**
     * 不用考虑防连点, 鼠标按键按下/释放/单击
     */
    public void sendCommandClick(String action, int button, int clickDuration, int clickInterval, int clickTimes) {
        sendingThread = new Thread(() -> {
            Global.client.sendCommand(String.format(getString(R.string.command_mouse_click_format),
                    JSON.toJSONString(action), button, clickDuration, clickInterval, clickTimes));
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    /**
     * 鼠标滑动
     */
    public void sendCommandMotion(int x, int y) {
        sendingThread = new Thread(() -> {
            Global.client.sendCommand(String.format(getString(R.string.command_mouse_motion_format), x, y));
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    /**
     * 鼠标滚轮
     */
    public void sendCommandScroll(int x, int y) {
        sendingThread = new Thread(() -> {
            Global.client.sendCommand(String.format(getString(R.string.command_mouse_scroll_format), x, y));
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        ((CommandingActivity) activity).reconnect(); // 消耗鼠标产生的返回值
    }

    /**
     * val 为json命令中的值
     */
    public enum MouseAction {
        SCROLL("scroll"), CLICK("click"), RELEASE("release"), PRESS("press"),
        MOVE_BY("moveBy"), MOVE_TO("moveTo");

        private final String val;

        MouseAction(String v) {
            val = v;
        }

        public String getVal() {
            return val;
        }
    }

    /**
     * val 为json命令中的值
     */
    public enum MouseButtons {
        LEFT(0), MIDDLE(1), RIGHT(2);

        private final int val;

        MouseButtons(int v) {
            val = v;
        }

        public int getVal() {
            return val;
        }
    }
}