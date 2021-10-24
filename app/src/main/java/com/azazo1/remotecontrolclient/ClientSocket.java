package com.azazo1.remotecontrolclient;

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
import java.util.Vector;

import static com.azazo1.remotecontrolclient.Encryptor.AESBase64Encode;
import static com.azazo1.remotecontrolclient.Encryptor.Base64AESDecode;


class MBufferedReader extends BufferedReader {
    private StringBuilder lineBuf = new StringBuilder();
    private Vector<String> bufLines = new Vector<>();
    private boolean alive = true;

    public MBufferedReader(Reader in) {
        super(in);
    }

    public boolean isAlive() {
        return alive;
    }

    @Override
    public String readLine() throws IOException {
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
    private Boolean verified = null;
    private boolean alive = true;

    public ClientSocket() throws IOException {
        init();
    }

    public boolean isAvailable() {
        if (verified == null) {
            return false;
        }
        return alive && verified && !client.isClosed();
    }

    public void init() throws IOException {
        if (client != null) {
            client.close();
        }
        client = new Socket();
    }

    public boolean verify() { // 服务端不响应
        if (verified == null) {
            long stamp = Tools.getTimeInMilli();
            String concat = Config.name + Config.version + Config.key + stamp;
            String encoded = Encryptor.md5(concat.getBytes(Config.charset));
            JSONObject obj = new JSONObject();
            obj.put("name", Config.name);
            obj.put("version", Config.version);
            obj.put("stamp", stamp);
            obj.put("md5", encoded);
            sendLine(obj.toJSONString());

            String response = readLine();
            if (response != null) {
                int responseCode = JSON.parseObject(response, int.class);
                verified = responseCode == 1;
                Log.i("Verify", "Verify " + (verified ? "Succeed" : "Lost"));
            } else {
                Log.i("Verify", "Verify Lost");
                verified = false;
            }
            return verified;
        }
        return verified; // verified 此处已不为null
    }

    public boolean connect(InetSocketAddress address) throws IOException {
        client.connect(address, Config.timeout);
        input = new MBufferedReader(new InputStreamReader(client.getInputStream(), Config.charset));
        output = new PrintWriter(client.getOutputStream());
        return verify();
    }

    public void sendRaw(String content) {
        output.print(content);
        output.flush();
    }

    public void sendLine(String content) {
        sendRaw(content + "\n");
    }

    public String readLine() {
        try {
            if (!input.isAlive()) {
                close();
                return null;
            }
            String line = input.readLine();
            if (line.equals("end")) { // 忽略end
                return readLine();
            }
            return line;
        } catch (IOException e) {
            e.printStackTrace();
            close();
            return null;
        }
    }

    public boolean sendCommand(JSONObject obj) {
        String get = AESBase64Encode(Config.key, obj.toJSONString());
        return sendCommand(get);
    }

    public CommandResult readCommand() {
        String line = readLine();
        if (line != null) {
            String command = Base64AESDecode(Config.key, line);
            if (command != null) {
                Log.i("Command get", "Decoded: " + command);
                if (command.matches("^-?[0-9]+$")) {
                    return new CommandResult(Integer.parseInt(command));
                } else {
                    try {
                        return new CommandResult(JSON.parseObject(command)); // 尝试解码成JSON对象
                    } catch (JSONException e) {
                        try {
                            return new CommandResult(JSON.parseArray(command)); // 尝试解码为JSON列表
                        } catch (JSONException exception) {
                            return new CommandResult(command); // 尝试解码为普通字符串
                        }
                    }
                }
            } else {
                Log.i("Command get", "Raw: " + line);
            }
        }
        return new CommandResult();
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
        String get = AESBase64Encode(Config.key, jsonString);
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
