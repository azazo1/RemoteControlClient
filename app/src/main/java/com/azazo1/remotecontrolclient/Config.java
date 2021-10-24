package com.azazo1.remotecontrolclient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Config {
    public static final Charset charset = StandardCharsets.UTF_8;  // 编码
    public static final String name = "RemoteControl"; // 项目名称
    public static final String version = "1.0"; // 版本号
    public static final int loopingRate = 60; // 每秒循环进行次数
    public static final String key = "azazo1Bestbdsrjpgaihbaneprjaerg";  // 密钥
    public static final int longestCommand = (int) Math.pow(2, 15);  // 最长命令长度(字节)
    public static final String algorithm = "AES"; // 加密算法
    public static final String algorithmAll = "AES/ECB/PKCS5Padding"; // 加密算法（细节）
    public static final int serverPort = 2004; // 局域网服务器端口
    public static final int timeout = 1500; // 套接字超时时间
    public static final int ipSearchingThread = 260; // IP搜索线程数
}
