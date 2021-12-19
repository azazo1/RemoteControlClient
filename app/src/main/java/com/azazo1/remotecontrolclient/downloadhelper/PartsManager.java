package com.azazo1.remotecontrolclient.downloadhelper;

import android.util.Log;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.Encryptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.Objects;

public class PartsManager {
    /**
     * save a file part to local machine.
     */
    public static boolean savePartAsMultiplePartFile(@NonNull String storePath, @NonNull CommandResult filePartResult) throws IOException {
        filePartResult.checkType(CommandResult.ResultType.JSON_OBJECT, true);
        JSONObject obj = filePartResult.getResultJsonObject();
        String filename = obj.getString("filename");
        long part = obj.getLong("part");
        File saveFile = new File(storePath + File.separator + filename + ".part" + part);
        boolean created = saveFile.createNewFile();
        if ((created || saveFile.exists()) && saveFile.canWrite()) {
            PrintWriter printer = new PrintWriter(new FileOutputStream(saveFile));
            printer.println(obj.toJSONString());
            printer.flush();
            printer.close();
            return true;
        }
        return false;
    }

    public static boolean savePartAsMultiplePartFile(@NonNull File storePath, @NonNull CommandResult filePartResult) throws IOException {
        return savePartAsMultiplePartFile(storePath.getAbsolutePath(), filePartResult);
    }

    /**
     * use the result of createPartsStoreSingleFile method as the storeFile arg.
     */
    public static boolean savePartToSingleFile(@NonNull File storeFile, @NonNull CommandResult filePartResult) {
        filePartResult.checkType(CommandResult.ResultType.JSON_OBJECT, true);
        JSONObject obj = filePartResult.getResultJsonObject();
        try {
            if (storeFile.exists() && storeFile.canWrite() && checkPart(obj)) {
                byte[] data = Encryptor.base64Decode(obj.getString("data"));
                int start = obj.getInteger("start");
                try (RandomAccessFile raFile = new RandomAccessFile(storeFile, "rw");) {
                    raFile.seek(start);
                    raFile.write(data);
                }
                return true;
            }
        } catch (FileNotFoundException ignore) {
        } catch (IOException e) {
            Log.e("savePartToSingleFile", "saving failed.");
            e.printStackTrace();
        }
        return false;
    }

//    public static File createPartsStoreSingleFile(String targetFilePath, FileDetail detail) {
//        File file;
//        try {
//            file = new File(targetFilePath);
//            boolean created = file.createNewFile();
//            if (file.exists() && file.canWrite()) {
//                try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
//                    fileOutputStream.write(new byte[detail.size]);
//                    fileOutputStream.flush();
//                }
//                return file;
//            }
//        } catch (IOException e) {
//            Log.e("createPartsStoreSingleFile", "Create file failed.");
//            e.printStackTrace();
//        }
//        return null;
//    }

    public static boolean checkPart(@NonNull JSONObject filePartObj) {
        String base64Data = filePartObj.getString("data");
        String md5 = filePartObj.getString("md5");
        int state = filePartObj.getInteger("state");
        return state == 1 && Objects.equals(Encryptor.md5(Encryptor.base64Decode(base64Data)), md5);
    }
}
