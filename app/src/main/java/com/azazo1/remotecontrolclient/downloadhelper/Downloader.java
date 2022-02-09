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
        if (sent) { // Log 报告
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
     * Return null if sending failed.
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
        if (result == null) { // local problem.
            return null;
        }
        if (result.checkType(CommandResult.ResultType.JSON_OBJECT)) {
            JSONObject object = result.getResultJsonObject();
            if (object == null) {
                return downloadPartUntilSuccess(path, part);
            }
            int state = object.getInteger("state");
            if (state == 3 || state == 5) { // file not exists || part number is not correct or file is too big.
                return result;
            } else if (state == 1) {
                return result;
            }
        }
        return downloadPartUntilSuccess(path, part); // other failed session.
    }

    /**
     * Download file with single Thread.
     * Will not delete the storeFile.
     * <p>
     * endCode:
     * -1 if local problem
     * 0 if unknown error
     * 1 if success
     * 2 if interrupted
     * 3 if authenticate failed.
     * 4 if IOException
     * 5 if no remote file
     * 6 if remote file is too big
     * 7 if remote file is unavailable (unclear 5 or 6)
     * </p>
     */
    public static boolean plainDownloadFile(@NonNull FileDetail fileDetail, @NonNull File storeFile, @Nullable MyReporter reporter) {
        if (downloading.get()) {
            return false;
        }
        downloading.set(true);
        for (int i = 1; i <= fileDetail.parts; i++) {
            CommandResult result = Downloader.downloadPartUntilSuccess(fileDetail.fullPath(), i);
            // check vital exception(local) to downloading.
            if (result == null) {
                if (reporter != null) {
                    reporter.reportEnd(-1);
                }
                return false;
            }
            // 检查 state
            // downloadPartUntilSuccess 已经排除 null 的可能
            int state = result.getResultJsonObject().getInteger("state");
            if (reporter != null) {
                switch (state) {
                    case 3:
                        reporter.reportEnd(5);
                        return false;
                    case 5:
                        reporter.reportEnd(6);
                        return false;
                    case 0:
                        reporter.reportEnd(0);
                        return false;
                    default:
                }
            }

            // if interrupted
            if (!downloading.get()) {
                if (reporter != null) {
                    reporter.reportEnd(2);
                }
                return false;
            }
            // download part
            Log.i("plainDownloadFile", "part" + i + ": " + PartsManager.savePartToSingleFile(
                    storeFile,
                    result
            ));
            if (reporter != null) {
                reporter.report(i, fileDetail.parts);
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
                if (reporter != null) {
                    reporter.reportEnd(3);
                }
                return false;
            } else {
                if (reporter != null) {
                    reporter.reportEnd(1);
                }
                return true;
            }
        } catch (FileNotFoundException e) {
            Log.e("plainDownloadFile", "inspect: no file.");
            if (reporter != null) {
                reporter.reportEnd(4);
            }
            return false;
        } catch (IOException e) {
            Log.e("plainDownloadFile", "inspect: read file failed.");
            if (reporter != null) {
                reporter.reportEnd(4);
            }
            return false;
        } finally {
            downloading.set(false);
        }
    }

    /**
     * Same as plainDownloadFile(FileDetail, File) but no need for local store file (useDefault).
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
            boolean result = plainDownloadFile(
                    fileDetail, storeFile, reporter
            );
            if (!result) {
                // delete file
                boolean deleted = storeFile.delete();
            }
            return result;
        } else {
            if (reporter != null) {
                reporter.reportEnd(4);
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
            boolean result = plainDownloadFile( // download
                    fileDetail, storeFile, reporter
            );
            if (!result) {
                // delete file
                boolean deleted = storeFile.delete();
            }
            return result;
        } else {
            if (reporter != null) {
                reporter.reportEnd(4);
            }
            Log.e("plainDownloadFile", "Create store file failed.");
            return false;
        }
    }
    // TODO: 2021/12/19 多线程下载
}
