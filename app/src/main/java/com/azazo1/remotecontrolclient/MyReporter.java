package com.azazo1.remotecontrolclient;

public interface MyReporter {
    /**
     * 报告进度
     */
    void report(int now, int total);

    /**
     * 此方法会先于 reportFailed 和 reportSucceed 执行
     * 用作结束标志
     */
    void reportEnd(int code);

//    /**
//     * 提供更详细的失败信息
//     * 不应该用作结束判定
//     */
//    void reportFailed(String message);

//    /**
//     * 提供更详细的成功信息
//     * 不应该用作结束判定
//     */
//    void reportSucceed(String message);
}
