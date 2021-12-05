package com.azazo1.remotecontrolclient.downloadhelper;

import com.alibaba.fastjson.JSONObject;
import com.azazo1.remotecontrolclient.CommandResult;

public class FileDetail {
    public final boolean available;
    public final String storePath;
    public final String name;
    public final long size;
    public final String md5;
    public final long parts;

    public FileDetail(boolean available, String storePath, String name, long size, String md5, long parts) {
        this.available = available;
        this.storePath = storePath;
        this.name = name;
        this.size = size;
        this.md5 = md5;
        this.parts = parts;
    }

    public FileDetail(CommandResult commandResult) {
        if (commandResult.type == CommandResult.ResultType.JSON_OBJECT) {
            JSONObject result = commandResult.getResultJsonObject();
            this.available = result.getBoolean("available");
            this.storePath = result.getString("path");
            this.name = result.getString("name");
            this.size = result.getLong("size");
            this.md5 = result.getString("md5");
            this.parts = result.getLong("parts");
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return "FileDetail{" +
                "available=" + available +
                ", storePath='" + storePath + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", md5='" + md5 + '\'' +
                ", parts=" + parts +
                '}';
    }
}
