package com.azazo1.remotecontrolclient.downloadhelper;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.azazo1.remotecontrolclient.CommandResult;
import com.azazo1.remotecontrolclient.Config;
import com.azazo1.remotecontrolclient.Encryptor;
import com.azazo1.remotecontrolclient.Global;
import com.azazo1.remotecontrolclient.MyReporter;
import com.azazo1.remotecontrolclient.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class Downloader {
    private static final AtomicBoolean downloading = new AtomicBoolean(false);

    public static void stopDownloading() {
        downloading.set(false);
    }

    @Nullable
    public static FileDetail getFileDetail(@NonNull String targetFile) {
        targetFile = targetFile.replaceAll("^[\"']", "");
        targetFile = targetFile.replaceAll("[\"']$", "");
        FileDetail detail = null;
        boolean sent = Global.client.sendCommand(
                String.format(
                        Global.activity.getString(R.string.command_file_detail_format),
                        JSON.toJSONString(targetFile)
                )
        );
        if (sent) {
            try {
                @NonNull CommandResult result = Global.client.readCommandUntilGet();
                detail = new FileDetail(result);
                if (detail.available) {
                    String detailString = detail.toString();
                    Log.i("getDetail", detailString.length() > Config.commandInfoMaxLength ? detailString.substring(0, Config.commandInfoMaxLength / 2) + detailString.substring(Math.max(Config.commandInfoMaxLength / 2, detailString.length() - Config.commandInfoMaxLength / 2)) : detailString);
                }
            } catch (IllegalArgumentException ignore) {
            }
        }
        return detail;
    }

    /**
     * The result may be an incorrect one, such as the state of 0, 3 or 5 .
     */
    @Nullable
    public static CommandResult downloadPart(String path, long part) {
        String command = Global.activity.getString(R.string.command_file_transport_get_format, JSON.toJSONString(path), part);
        boolean sent = Global.client.sendCommand(command);
        if (sent) {
            return Global.client.readCommandUntilGet();
        } else {
            return null;
        }
    }

    /**
     * <p>
     * Keep download the same part until result state is 1.
     * </p>
     * <p>
     * It will stop if the sendCommand method returned false,
     * which means something wrong with local machine.
     * Then it will return null.
     * </p>
     * <p>
     * But will cease when state code is 3 or 5, which means this part is an unavailable one.
     * At this time, return the incorrect result.
     * </p>
     */
    @Nullable
    public static CommandResult downloadPartUntilSuccess(String path, long part) {
        CommandResult result = downloadPart(path, part);
        if (result == null) {
            return null;
        }
        if (result.checkType(CommandResult.ResultType.JSON_OBJECT)) {
            JSONObject object = result.getResultJsonObject();
            if (object == null) {
                return downloadPartUntilSuccess(path, part);
            }
            int state = object.getInteger("state");
            if (state == 3 || state == 5) { // file not exists || part number is not correct.
                return result;
            } else if (state == 1) {
                return result;
            }
        }
        return downloadPartUntilSuccess(path, part); // other failed session.
    }

    /**
     * Download file with single Thread.
     */
    public static boolean plainDownloadFile(@NonNull FileDetail fileDetail, @NonNull File storeFile, @Nullable MyReporter reporter) {
        if (downloading.get()) {
            return false;
        }
        downloading.set(true);
        for (int i = 1; i <= fileDetail.parts; i++) {
            CommandResult result = Downloader.downloadPartUntilSuccess(fileDetail.fullPath(), i);
            // check vital exception to downloading.
            if (result == null) {
                if (reporter != null) {
                    reporter.report(-1, -1, true);
                }
                return false;
            }
            // if interrupted
            if (!downloading.get()) {
                if (reporter != null) {
                    reporter.report(-1, -1, true);
                }
                // delete file
                boolean deleted = storeFile.delete();
                return false;
            }
            // download part
            Log.i("plainDownloadFile", "part" + i + ": " + PartsManager.savePartToSingleFile(
                    storeFile,
                    result
            ));
            if (reporter != null) {
                reporter.report(i, fileDetail.parts, false);
            }
        }
        try {
            // inspect file data (md5)
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(storeFile));
            byte[] data = new byte[fileDetail.size];
            int length = bis.read(data);
            boolean compare = (fileDetail.size == length && Objects.equals(Encryptor.md5(data), fileDetail.md5));
            Log.e("plainDownloadFile", "inspect: " + compare);
            if (!compare) {
                boolean deleted = storeFile.delete();
                if (reporter != null) {
                    reporter.report(-1, -1, true);
                }
                return false;
            } else {
                if (reporter != null) {
                    reporter.report(fileDetail.parts, fileDetail.parts, true);
                }
                return true;
            }
        } catch (FileNotFoundException e) {
            Log.e("plainDownloadFile", "inspect: no file.");
            if (reporter != null) {
                reporter.report(-1, -1, true);
            }
            return false;
        } catch (IOException e) {
            Log.e("plainDownloadFile", "inspect: read file failed.");
            if (reporter != null) {
                reporter.report(-1, -1, true);
            }
            return false;
        } finally {
            // check if interrupted
            if (!downloading.get()) {
                boolean deleted = storeFile.delete();
            }
            downloading.set(false);
        }
    }

    /**
     * Same as plainDownloadFile(FileDetail, File) but no need for local store file.
     */
    public static boolean plainDownloadFile(@NonNull FileDetail fileDetail, @Nullable MyReporter reporter) {
        File storeFile = new File(
                Global.activity.getExternalCacheDir().getAbsolutePath() + File.separator + fileDetail.filename
        );
        try {
            boolean created = storeFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (storeFile.exists() && storeFile.canWrite()) {
            return plainDownloadFile(
                    fileDetail, storeFile, reporter
            );
        } else {
            if (reporter != null) {
                reporter.report(-1, -1, true);
            }
            Log.e("plainDownloadFile", "create store file failed.");
            return false;
        }
    }

    /**
     * Same as plainDownloadFile(FileDetail, File) but no needs for local store file, instead needs local store path.
     */
    public static boolean plainDownloadFile(@NonNull FileDetail fileDetail, @NonNull String storeFilePath, @Nullable MyReporter reporter) {
        File storeFile = new File(
                storeFilePath
        );
        try {
            boolean created = storeFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (storeFile.exists() && storeFile.canWrite()) {
            return plainDownloadFile( // download
                    fileDetail, storeFile, reporter
            );
        } else {
            if (reporter != null) {
                reporter.report(-1, -1, true);
            }
            Log.e("plainDownloadFile", "Create store file failed.");
            return false;
        }
    }
    // TODO: 2021/12/19 多线程下载
}
