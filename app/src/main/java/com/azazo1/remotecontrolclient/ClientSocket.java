package com.azazo1.remotecontrolclient;

import static com.azazo1.remotecontrolclient.Encryptor.AESBase64Encode;
import static com.azazo1.remotecontrolclient.Encryptor.Base64AESDecode;

import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;


class MBufferedReader extends BufferedReader {
    private boolean alive = true;

    public MBufferedReader(Reader in) {
        super(in);
    }

    public boolean isAlive() {
        return alive;
    }

    public String mReadLine() throws IOException {
        String get;
        try {
            get = super.readLine();
        } catch (IOException e) {
            try {
                close();
            } catch (IOException ignore) {
            }
            throw e;
        }
        if (get == null) {
            try {
                close();
            } catch (IOException ignore) {
            }
            return null;
        } else {
            return get;
        }
    }

    @Override
    public void close() throws IOException {
        alive = false;
        super.close();
    }
}

public class ClientSocket {
    private Socket client;
    private MBufferedReader input;
    private PrintWriter output;
    private Boolean authenticated = null;
    private boolean alive = true;

    public ClientSocket() throws IOException {
        init();
    }

    public boolean isAvailable() {
        if (authenticated == null) {
            return false;
        }
        return alive && authenticated && !client.isClosed();
    }

    public void init() throws IOException {
        if (client != null) {
            client.close();
        }
        client = new Socket();
        client.setSoTimeout(Config.timeout);
    }

    public boolean authenticate() {
        return authenticate(Config.getKey());
    }

    public boolean authenticate(String key) {
        if (authenticated == null) {
            long stamp = Tools.getTimeInMilli();
            String concat = Config.name + Config.getVersion() + key + stamp;
            String encoded = Encryptor.md5(concat.getBytes(Config.charset));
            JSONObject obj = new JSONObject();
            obj.put("name", Config.name);
            obj.put("version", Config.getVersion());
            obj.put("stamp", stamp);
            obj.put("md5", encoded);
            sendLine(obj.toJSONString());

            String response = readLine();
            if (response != null) {
                int responseCode = 0;
                try {
                    responseCode = JSON.parseObject(response, int.class);
                } catch (Exception ignored) {
                }
                authenticated = responseCode == 1;
                Log.i("Authenticate", "Authentication " + (authenticated ? "Succeed" : "Lost"));
            } else {
                Log.i("Authenticate", "Authentication Lost");
                authenticated = false;
            }
        }
        return authenticated; // authenticated 此处已不为null
    }

    public boolean connect(InetSocketAddress address) throws IOException {
        return connect(address, Config.getKey());
    }

    public boolean connect(InetSocketAddress address, String password) throws IOException {
        client.connect(address, Config.timeout);
        input = new MBufferedReader(new InputStreamReader(client.getInputStream(), Config.charset));
        output = new PrintWriter(client.getOutputStream());
        return authenticate(password);
    }

    public void sendRaw(String content) {
        output.print(content);
        output.flush();
    }

    public synchronized void sendLine(String content) {
        sendRaw(content + "\n");
    }

    public String readLine() {
        return readLine(0);
    }

    /**
     * 返回 null 如果连接出现问题, 若 timeout(ms) 为 非正数 则为默认
     */
    public synchronized String readLine(int timeout) {
        // it blocks
        try {
            if (!input.isAlive()) {
                close();
                return null;
            }
            String line;
            if (timeout <= 0) {
                line = input.mReadLine();
            } else {
                int oriTimeout = client.getSoTimeout();
                client.setSoTimeout(timeout);
                line = input.mReadLine();
                client.setSoTimeout(oriTimeout);
            }
            return line;
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            close();
            return null;
        }
    }

    public boolean sendCommand(JSONObject obj) {
        String get = AESBase64Encode(Config.getKey(), obj.toJSONString());
        return sendCommand(get);
    }

    /**
     * timeout 见 readLine
     */
    public CommandResult readCommand(int timeout) {
        String line = readLine(timeout);
        if (line != null) {
            String command = Base64AESDecode(Config.getKey(), line);
//            String command = new String(base64Decode(line));
            if (command != null) {
                Log.i("Command get", "Decoded: " +
                        (command.length() > Config.commandInfoMaxLength ?
                                command.substring(0, Config.commandInfoMaxLength / 2) + "..." +
                                        command.substring(Math.max(Config.commandInfoMaxLength / 2, command.length() - Config.commandInfoMaxLength / 2))
                                : command)
                );
                if (command.matches("^-?[0-9]+$")) {
                    return new CommandResult(Integer.parseInt(command));
                } else {
                    try {
                        return new CommandResult(JSON.parseObject(command)); // 尝试解码成JSON对象
                    } catch (JSONException e) {
                        try {
                            return new CommandResult(JSON.parseArray(command)); // 尝试解码为JSON列表
                        } catch (JSONException exception) {
                            return new CommandResult(); // 无效结果
                        }
                    }
                }
            } else {
                Log.i("Command get", "Raw: " + line);
            }
        }
        return null; // 连接断开
    }

    public CommandResult readCommand() {
        return readCommand(0);
    }

    public boolean sendCommand(String jsonString) {
        if (!isAvailable()) {
            Log.e("Command Send", "Invalid state.");
            return false;
        }
        if (jsonString == null || jsonString.isEmpty()) {
            Log.e("Command Send", "Invalid content.");
            return false;
        }
        String get = AESBase64Encode(Config.getKey(), jsonString);
//        String get = base64Encode(jsonString.getBytes(Config.charset));
        if (get != null) {
            if (get.length() > Config.longestCommand) {
                Log.e("Command Send", "Too long.");
                return false;
            }
            sendLine(get);
            Log.i("Command Send", jsonString + " sent successfully.");
            return true;
        }
        Log.e("Command Send", "Encrypt failed.");
        return false;
    }

    public void close() {
        alive = false;
        try {
            input.close();
        } catch (Exception ignore) {
        }
        try {
            output.close();
        } catch (Exception ignore) {
        }
        try {
            client.close();
        } catch (Exception ignore) {
        }
    }

}
