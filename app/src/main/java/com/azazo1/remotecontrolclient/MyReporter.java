package com.azazo1.remotecontrolclient;

public interface MyReporter {
    // if failed it will be (-1, -1, true)
    void report(int now, int total, boolean end);
}
