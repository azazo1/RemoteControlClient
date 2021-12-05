package com.azazo1.remotecontrolclient.downloadhelper;

import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.R;

public class Downloader {
    public static FileDetail getFileDetail(String targetFile) {
        FileDetail detail = null;
        boolean sent = Global.client.sendCommand(
                String.format(
                        Global.activity.getString(R.string.command_file_detail_format_string),
                        JSON.toJSONString(targetFile)
                )
        );
        if (sent) {
            try {
                CommandResult result = Global.client.readCommand();
                detail = new FileDetail(result);
                Log.i("get", detail.toString());
            } catch (IllegalArgumentException ignore) {
            }
        }
        return detail;
    }
}
