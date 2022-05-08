package com.azazo1.remotecontrolclient.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSON;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.CommandResultHandler;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;
import com.azazo1.remotecontrolclient.Tools;
import com.azazo1.remotecontrolclient.activity.CommandingActivity;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 下部鼠标按键:<br>
 * 中间行:短按可实现单击, 长按可模拟鼠标按住不放, 若按键为中键, 滑动可模拟鼠标滚轮(水平和垂直)<br>
 * <br>
 * 中间操控板:
 * todo
 */
public class MouseFragment extends Fragment {
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final long minMovingInterval_ms = 150; // 滑动事件最小时间间隔 (ms)
    private long sendingStartTime;
    private CommandingActivity activity;
    private ProgressBar progressBar;
    private int originButtonColor;
    private View touchPad;
    private Thread sendingThread;
    private Button leftButton;
    private Button middleButton;
    private long lastMidButtonMovingTime = 0; // 上次中键滑动时间
    private long lastMouseMovingTime = 0; // 上次鼠标滑动时间
    private Pair<Integer, Integer> midButtonStartPos; // 中键开始滑动时手指的位置，用于判断用户是否对中键进行滑动
    @SuppressWarnings("FieldCanBeLocal")
    private Pair<Integer, Integer> mouseStartPos; // 鼠标开始滑动时手指的位置
    private volatile boolean midButtonMoving = false; // 用户是否在滑动中键
    private volatile boolean midButtonPressed = false; // 中键是否已被按下, 若是则阻止滑动行为
    private volatile boolean midButtonReleased = true; // 中键是否已被松开
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

        leftButton.setTag(MouseButtons.LEFT.getVal());
        rightButton.setTag(MouseButtons.RIGHT.getVal());
        middleButton.setTag(new int[]{-1, -1});
        /* Button view tag: 按下的按钮(int), 中键为上一次的手指坐标(为了计算位移)(Pair<int,int>)*/
        progressBar = get.findViewById(R.id.getting_command_result_progress_bar);
        initView();
        return get;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initView() {
        progressBar.setVisibility(View.INVISIBLE);
        originButtonColor = ContextCompat.getColor(activity, R.color.generic_sending_button_bg);

        leftButton.setBackgroundColor(originButtonColor);
        rightButton.setBackgroundColor(originButtonColor);
        middleButton.setBackgroundColor(originButtonColor);

        leftButton.setOnTouchListener(this::mouseButtonLRTouch);
        middleButton.setOnTouchListener(this::mouseButtonMTouch);
        rightButton.setOnTouchListener(this::mouseButtonLRTouch);

        touchPad.setOnTouchListener(this::mouseMotionTouch);
    }

    private void resetView() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    /**
     * 鼠标左右按键的按下和释放
     */
    public boolean mouseButtonLRTouch(View view, MotionEvent event) {
        int button = (int) view.getTag();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                sendCommand(MouseAction.PRESS.getVal(), button, 0, 0,
                        0, 0, 0, null);
                break;
            }
            case MotionEvent.ACTION_UP: {
                sendCommand(MouseAction.RELEASE.getVal(), button, 0, 0,
                        0, 0, 0, null);
                break;
            }
        }
        return true;
    }
// 功能被 Touch 覆盖了
//    public void mouseClick(View view) {
//        int button = (int) view.getTag();
//        sendCommand(MouseAction.CLICK.getVal(), button, 0, 0,
//                0, 0, 0, null);
//    }

    /**
     * 鼠标中间的按下释放和滚动
     */
    public boolean mouseButtonMTouch(View view, @NonNull MotionEvent event) {
        int threshold = 5; // 滑动判断阈值
        int pressWait_ms = 100; // 按下中键后等待该时间判断是否滑动
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                int[] pos = (int[]) view.getTag();
                int deltaX = (int) (pos[0] - event.getX());
                int deltaY = (int) (event.getY() - pos[1]);
                long nowTime = Tools.getTimeInMilli();
                if (!midButtonMoving && !midButtonPressed && (Math.abs(deltaX) >= threshold || Math.abs(deltaY) >= threshold)) {
                    // 用户在被判断为 按下 前进行 滑动
                    midButtonMoving = true;
                }
                if (midButtonMoving && !midButtonPressed && (nowTime - lastMidButtonMovingTime > minMovingInterval_ms)) {
                    sendCommand(MouseAction.SCROLL.getVal(), 0, (int) (deltaX * 0.05), (int) (deltaY * 0.05),
                            0, 0, 0, null);
                    view.setTag(new int[]{(int) event.getX(), (int) event.getY()});
                    lastMidButtonMovingTime = nowTime;
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                midButtonStartPos = new Pair<>((int) event.getX(), (int) event.getY());
                view.setTag(new int[]{midButtonStartPos.first, midButtonStartPos.second});
                midButtonMoving = false;
                midButtonPressed = false;
                midButtonReleased = false;
                activity.handler.postDelayed(() -> {
                    if (!midButtonMoving) {
                        if (midButtonReleased) { // 用户单击太短暂, 未处理好"按下", 但已经松开屏幕上的按钮
                            // 补充执行按键点击
                            sendCommand(MouseAction.CLICK.getVal(), MouseButtons.MIDDLE.getVal(), 0, 0,
                                    30, 0, 1, null);
                            view.setTag(new int[]{-1, -1});
                            midButtonMoving = false;
                            midButtonPressed = false;
                            midButtonStartPos = null;
                        } else {
                            sendCommand(MouseAction.PRESS.getVal(), MouseButtons.MIDDLE.getVal(), 0, 0,
                                    0, 0, 0, null);
                            midButtonPressed = true; // 等待一段时间后发现用户没有滑动, 则判断用户想要按下中键
                        }
                    }
                }, pressWait_ms);
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (!midButtonMoving && midButtonPressed) {
                    sendCommand(MouseAction.RELEASE.getVal(), MouseButtons.MIDDLE.getVal(), 0, 0,
                            0, 0, 0, null);
                }
                // 在用户的短暂单击, 不是"滑动", 且"按下"判定还未执行时, 两个变量都为否, 不应处理"释放"
                if (midButtonMoving || midButtonReleased) {
                    view.setTag(new int[]{-1, -1});
                    midButtonMoving = false;
                    midButtonPressed = false;
                    midButtonStartPos = null;
                }
                midButtonReleased = true;
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
                int deltaX = (int) (event.getX() - pos[0]);
                int deltaY = (int) (event.getY() - pos[1]);
                long nowTime = Tools.getTimeInMilli();
                if (nowTime - lastMouseMovingTime > minMovingInterval_ms) {
                    sendCommand(MouseAction.MOVE_BY.getVal(), 0, deltaX, deltaY,
                            0, 0, 0, null);
                    view.setTag(new int[]{(int) event.getX(), (int) event.getY()});
                    lastMouseMovingTime = nowTime;
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                mouseStartPos = new Pair<>((int) event.getX(), (int) event.getY());
                view.setTag(new int[]{mouseStartPos.first, mouseStartPos.second});
                break;
            }
            case MotionEvent.ACTION_UP: {
                mouseStartPos = null;
                view.setTag(null);
                break;
            }

        }
        return true;
    }

    /**
     * 不用考虑防连点
     */
    public void sendCommand(String action, int button, int x, int y,
                            int clickDuration, int clickInterval, int clickTimes,
                            CommandResultHandler handler) {
        sendingThread = new Thread(() -> {
            sending.set(true);
            whileSending();
            if (Global.client.sendCommand(String.format(getString(R.string.command_mouse_format),
                    JSON.toJSONString(action), button, x, y, clickDuration, clickInterval, clickTimes
            ))) {
                CommandResult result = Global.client.readCommand(); // 把命令执行返回值消耗掉
                if (handler != null) {
                    handler.resultAppearancePost(result);
                }
            }
            sending.set(false);
        });
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    private void whileSending() {
        sendingStartTime = Tools.getTimeInMilli();
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
                    resetView();
                }
            }
        }, (long) (1.0 / Config.loopingRate * 1000));
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