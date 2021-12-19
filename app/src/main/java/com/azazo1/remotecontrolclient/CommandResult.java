package com.azazo1.remotecontrolclient;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class CommandResult {
    private final ResultType type;
    private JSONArray jsonArray;
    private JSONObject jsonObject;
    private Integer anInt;

    public CommandResult() {
        type = ResultType.NULL;
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
            case NULL:
            default:
                return null;
        }
    }


    public JSONArray getResultJsonArray() {
        return jsonArray;
    }

    public Integer getResultInt() {
        return anInt;
    }

    public JSONObject getResultJsonObject() {
        return jsonObject;
    }

    public ResultType getType() {
        return type;
    }

    public boolean checkType(ResultType targetType) {
        return type == targetType;
    }

    public boolean checkType(ResultType targetType, boolean _throw) {
        boolean same = checkType(targetType);
        if (_throw && !same) {
            throw new ResultTypeError();
        }
        return same;
    }

    public enum ResultType {
        JSON_OBJECT, INT, ARRAY, NULL
    }

    static class ResultTypeError extends RuntimeException {
    }
}

