package com.azazo1.remotecontrolclient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class Config {
    public static final Charset charset = StandardCharsets.UTF_8;  // 编码
    public static final String name = "RemoteControl"; // 项目名称
    // 记得更新Module gradle文件中的版本号
    public static final int loopingRate = 60; // 每秒循环进行次数
    public static final int longestCommand = 1048576;  // 最长命令长度(字节)
    public static final String algorithm = "AES"; // 加密算法
    public static final String algorithmAll = "AES/ECB/PKCS5Padding"; // 加密算法（细节）
    public static final int serverPort = 2004; // 局域网服务器端口
    public static final int timeout = 30000; // 套接字超时时间（ms）
    public static final int searchTimeout = 3000; // 搜索线程连接超时时间（ms）（似乎不准确）
    public static final int ipSearchingThreadNum = 30; // IP搜索线程数
    public static final long waitingTimeForTermination = 3000; // 中断提醒时间间隔（距离按钮被点击）
    public static final long defaultShowTextTime = 3000; // showText命令默认时间长度
    public static final int commandInfoMaxLength = 300; // 套接字接收到命令后报告内容的长度最大值(最好为双数)
    private static final String key = "as437pdjpa97fdsa5ytfjhzfwa";  // 默认密钥
    private static final String version = "1.0.20220829"; // 版本号
    private static String changedKey = null;  // 修改后的密钥
    private static String modifiedVersion = null; // 版本号

    public static String getVersion() {
        if (modifiedVersion != null) {
            return modifiedVersion;
        } else {
            return version;
        }
    }

    public static void modifyVersion(String versionCode) { // 传入 null 或 空字符串 来使用默认值
        if (versionCode == null || versionCode.isEmpty()) {
            modifiedVersion = version;
        } else {
            modifiedVersion = versionCode;
        }
    }

    public static String getKey() {
        if (changedKey != null) {
            return changedKey;
        } else {
            return key;
        }
    }

    public static void setKey(String key) { // 传入 null 或 空字符串 来使用默认值
        if (key == null || key.isEmpty()) {
            changedKey = Config.key;
        } else {
            changedKey = key;
        }
    }

    /**
     * 返回默认 key
     */
    public static String getKey(String placeholder) {
        return key;
    }
}
