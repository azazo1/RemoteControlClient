package com.azazo1.remotecontrolclient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Config {
    public static final Charset charset = StandardCharsets.UTF_8;  // 编码
    public static final String name = "RemoteControl"; // 项目名称
    public static final String version = "1.0.20220522"; // 版本号
    // 记得更新Module gradle文件中的版本号
    public static final String title = name + " " + version;
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
    public static String key = "as437pdjpa97fdsa5ytfjhzfwa";  // 默认密钥
}
