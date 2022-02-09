package com.azazo1.remotecontrolclient;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

public class IPSearcher {
    public int targetPort;
    public MyReporter reporter;
    public ThreadIPDetector tpd;

    public IPSearcher(@Nullable MyReporter reporter, int targetPort) {
        this.targetPort = targetPort;
        if (reporter != null) {
            this.reporter = reporter;
        } else {
            this.reporter = new MyReporter() {
                @Override
                public void report(int now, int total) {
                }

                @Override
                public void reportEnd(int code) {
                }
            };
        }
    }

    public static void main(String[] args) throws IOException {
        new IPSearcher(new MyReporter() {
            @Override
            public void report(int now, int total) {
                if (now != 0) {
                    System.out.print("\r" + (now * 1.0 / total));
                } else {
                    System.out.println();
                }
            }

            @Override
            public void reportEnd(int code) {
            }
        }, Config.serverPort).searchAndReport();
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public Vector<String> searchAndReport() throws IOException {
        Vector<String> result;
        result = searchForIP(); // search ip try
        if (result == null || tpd == null || tpd.isStopped() || Thread.currentThread().isInterrupted()) {
            // this means being stopped or invalid web
            reporter.reportEnd(-1);
            return new Vector<>();
        }
        return result;
    }

    protected Vector<String> searchForIP() throws IOException {
        int mask = -1;
        String host = null;
        outerLoop:
        for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
             interfaces.hasMoreElements(); ) {
            NetworkInterface anInterface = interfaces.nextElement();
            for (Enumeration<InetAddress> addresses = anInterface.getInetAddresses();
                 addresses.hasMoreElements(); ) {
                InetAddress address = addresses.nextElement();
                if (!address.isLoopbackAddress()) {
                    if (address.isSiteLocalAddress()) {
                        host = address.getHostAddress();
                        List<InterfaceAddress> targetAddress = NetworkInterface.getByInetAddress(address).getInterfaceAddresses();
                        for (InterfaceAddress interfaceAddress : targetAddress) {
                            if (!(interfaceAddress.getNetworkPrefixLength() >= 32)) {
                                mask = interfaceAddress.getNetworkPrefixLength();
                            }
                        }
                        break outerLoop;
                    }
                }
            }
        }
        if (host == null || mask == -1) {
            return null;
        }
        IpGenerator ipGen = new IpGenerator(host, mask);
        Log.i("Search", "Get local address successfully. \nHost:" + host + ", Mask: " + mask + ". " + " \nUsing threads to find target host...");
        tpd = new ThreadIPDetector(ipGen, targetPort, reporter);
        tpd.start();
        return tpd.availableHosts;
    }

    public void stop() {
        try {
            tpd.stop();
        } catch (NullPointerException ignore) {
        }
    }
}

class ThreadIPDetector implements Runnable {
    private final int targetPort;
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    public IpGenerator ipGen;
    public Vector<String> availableHosts;
    public MyReporter reporter;

    public ThreadIPDetector(IpGenerator ipGen, int port, MyReporter reporter) {
        this.ipGen = ipGen;
        this.reporter = reporter;
        availableHosts = new Vector<>();
        this.targetPort = port;
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public synchronized String next() {
        try {
            this.reporter.report(ipGen.cursor, ipGen.genRange);
//            System.out.println("Trying: " + ipGen.cursor + ", total: " + ipGen.genRange);
            return ipGen.next();
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public synchronized void addHost(String host) {
        availableHosts.add(host);
    }

    @Override
    public void run() {
        String host;
        while ((host = next()) != null && alive.get()) {
            if (tryAddress(host)) {
                addHost(host);
            }
        }
    }

    public boolean tryAddress(String host) { // 判断地址是否有效
        try {
            InetAddress address = InetAddress.getByName(host);
            String ip = address.getHostAddress(); // eg: www.baidu.com -> xxx.xxx.xxx.xxx
            InetSocketAddress targetAddressWithPort = new InetSocketAddress(ip, targetPort);
            Socket socket = new Socket();
            socket.connect(targetAddressWithPort, Config.timeout);
            socket.close();
            Log.e("Search", "Reached: " + ip);
            return true;
        } catch (IOException e) {
            Log.e("Search", "Failed: " + host + e.getMessage());
        }
        return false;
    }

    public void start() {
        if (!alive.get()) {
            throw new RuntimeException("Already Closed!");
        }
        Thread[] threads = new Thread[Config.ipSearchingThread];
        for (int i = 0; i < Config.ipSearchingThread; i++) {
            threads[i] = new Thread(this);
            threads[i].start();
        }
        int now = 1;
        for (Thread t : threads) {
            try {
//                Log.i("Search", "Joining thread-" + now + ", " + (threads.length - now + 1) + " left.");
                t.join();
                now += 1;
            } catch (InterruptedException e) {
                Log.i("Search", "Joining Interrupted.");
            }
        }
        Log.i("Search", "Joining Over.");
        alive.set(false);
    }

    public void stop() {
        alive.set(false);
        stopped.set(true);
    }
}

class IpGenerator {
    public String host;
    public long host_parent;
    public int mask;
    public int genRange;
    public int cursor;
    public boolean End;

    public IpGenerator(String host, int mask) {
        this.host = host;
        this.mask = mask;
        host_parent = splitHost(host) & convertMaskToBits(mask);
        genRange = 1 << (32 - mask);
        cursor = 1;
        End = false;
    }

    public static long splitHost(String host) {
        String[] ints = host.split("\\.");
        long total = 0;
        for (int i = 0; i < ints.length; i++) {
            long single = Integer.parseInt(ints[i]);
            total += single << ((3 - i) * 8);
        }
        return total;
    }

    public static String joinHost(long host_int) {
        int part = 4;
        int length = 32;
        String[] parts = new String[part];
        for (int i = 0; i < part; i++) {
            long single = host_int % (1L << (length / part));
            host_int >>= (length / part);
            parts[(part - i - 1)] = String.valueOf(single);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return String.join(".", parts);
        } else {
            StringJoiner joiner = new StringJoiner(".");
            for (String p : parts) {
                joiner.add(p);
            }
            return joiner.toString();
        }
    }

    public static long convertMaskToBits(int mask) {
        long total = 0;
        int length = 32; // The length of IPv4
        for (int i = 1; i <= mask; i++) {
            total += 1L << (length - i);
        }
        return total;
    }

    public String next() throws NoSuchFieldException {
        if (End) {
            throw (new NoSuchFieldException("generate out of range!"));
        }
        String result = joinHost(host_parent + cursor);
        cursor += 1;
        if (cursor >= genRange) {
            End = true;
        }
        return result;
    }

}

