package com.azazo1.remotecontrolclient;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class CommandResult {
    private final ResultType type;
    private String string;
    private JSONArray jsonArray;
    private JSONObject jsonObject;
    private int anInt;

    public CommandResult() {
        type = ResultType.NULL;
    }

    public CommandResult(String string) {
        this.string = string;
        type = ResultType.STRING;
    }

    public CommandResult(JSONArray jsonArray) {
        this.jsonArray = jsonArray;
        type = ResultType.ARRAY;
    }

    public CommandResult(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
        type = ResultType.JSON_OBJECT;
    }

    public CommandResult(int anInt) {
        this.anInt = anInt;
        type = ResultType.INT;
    }

    public Object getResult() {
        switch (type) {
            case INT:
                return getResultInt();
            case JSON_OBJECT:
                return getResultJsonObject();
            case ARRAY:
                return getResultJsonArray();
            case STRING:
                return getResultString();
            case NULL:
            default:
                return null;
        }
    }

    public String getResultString() {
        return string;
    }

    public JSONArray getResultJsonArray() {
        return jsonArray;
    }

    public int getResultInt() {
        return anInt;
    }

    public JSONObject getResultJsonObject() {
        return jsonObject;
    }

    public enum ResultType {
        JSON_OBJECT, INT, ARRAY, STRING, NULL
    }
}
