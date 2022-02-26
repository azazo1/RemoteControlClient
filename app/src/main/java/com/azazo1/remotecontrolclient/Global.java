package com.azazo1.remotecontrolclient;

import android.app.Activity;

import com.azazo1.remotecontrolclient.activity.CommandingActivity;
import com.azazo1.remotecontrolclient.activity.ConnectingActivity;

public class Global {
    public static ClientSocket client;
    public static Activity activity;
    public static CommandingActivity commandingActivity;
    public static ConnectingActivity connectingActivity;
}
