package com.azazo1.remotecontrolclient;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.concurrent.atomic.AtomicInteger;

import static com.azazo1.remotecontrolclient.Encryptor.AESBase64Encode;
import static com.azazo1.remotecontrolclient.Encryptor.Base64AESDecode;


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
    private final AtomicInteger waitCount = new AtomicInteger(0); // 用来记录发送但为接收结果的命令
    private Socket client;
    private MBufferedReader input;
    private PrintWriter output;
    private Boolean authenticated = null;
    private boolean alive = true;

    public ClientSocket() throws IOException {
        init();
    }

    public int getWaitCount() {
        return waitCount.intValue();
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
        return authenticate(Config.key);
    }

    public boolean authenticate(String key) {
        if (authenticated == null) {
            long stamp = Tools.getTimeInMilli();
            String concat = Config.name + Config.version + key + stamp;
            String encoded = Encryptor.md5(concat.getBytes(Config.charset));
            JSONObject obj = new JSONObject();
            obj.put("name", Config.name);
            obj.put("version", Config.version);
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
        return connect(address, Config.key);
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

    public synchronized String readLine() {
        // it blocks
        try {
            if (!input.isAlive()) {
                close();
                return null;
            }
            String line = input.mReadLine();
//            if (line.equals("end")) { // ignore end
//                return readLine();
//            }
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

    public @Nullable
    CommandResult readCommand() {
        String line = readLine();
        if (line != null) {
            String command = Base64AESDecode(Config.key, line);
            if (command != null) {
                waitCount.set(waitCount.intValue() - 1);

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
                            return new CommandResult();
                        }
                    }
                }
            } else {
                Log.i("Command get", "Raw: " + line);
            }
        }
        return new CommandResult();
    }

    @NonNull
    public CommandResult readCommandUntilGet() {
        CommandResult result = readCommand();
        if (result == null) {
            return readCommandUntilGet();
        }
        return result;
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
            waitCount.set(waitCount.intValue() + 1);
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
