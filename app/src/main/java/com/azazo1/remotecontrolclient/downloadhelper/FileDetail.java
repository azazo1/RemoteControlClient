package com.azazo1.remotecontrolclient.downloadhelper;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.azazo1.remotecontrolclient.CommandResult;

import java.io.File;

public class FileDetail {
    public final boolean available;
    public final String remoteStorePath;
    public final String filename;
    public final int size;
    public final String md5;
    public final int parts;

    public FileDetail(boolean available, String remoteStorePath, String filename, int size, String md5, int parts) {
        this.available = available;
        this.remoteStorePath = remoteStorePath;
        this.filename = filename;
        this.size = size;
        this.md5 = md5;
        this.parts = parts;
    }

    public FileDetail(@NonNull CommandResult commandResult) {
        if (commandResult.checkType(CommandResult.ResultType.JSON_OBJECT)) {
            JSONObject result = commandResult.getResultJsonObject();
            this.available = result.getBoolean("available");
            this.remoteStorePath = result.getString("path");
            this.filename = result.getString("name");
            this.size = result.getInteger("size");
            this.md5 = result.getString("md5");
            this.parts = result.getInteger("parts");
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return "FileDetail{" +
                "available=" + available +
                ", remoteStorePath='" + remoteStorePath + '\'' +
                ", filename='" + filename + '\'' +
                ", size=" + size +
                ", md5='" + md5 + '\'' +
                ", parts=" + parts +
                '}';
    }

    public String fullPath() {
        return remoteStorePath + File.separator + filename;
    }
}
