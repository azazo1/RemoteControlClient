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
import java.util.StringJoiner;
import java.util.Vector;

public class IPSearcher {
    public static final int targetPort = 2004;
    public MyReporter reporter;
    public ThreadIPDetector tpd;

    public IPSearcher(@Nullable MyReporter reporter) {
        if (reporter != null) {
            this.reporter = reporter;
        } else {
            this.reporter = (now, total, end) -> {
            };
        }
    }

    public static void main(String[] args) throws IOException {
        new IPSearcher((now, total, b) -> {
            if (now != 0) {
                System.out.print("\r" + (now * 1.0 / total));
            } else {
                System.out.println();
            }
        }).searchAndReport();
    }

    public Vector<String> searchAndReport() throws IOException {
        Vector<String> result;
        result = searchForIP(); // search ip try
        if (result == null || tpd == null || tpd.isStopped()) {
            reporter.report(0, 0, true); // this means being stopped or invalid web
            return new Vector<>();
        }
        reporter.report(1, 1, true);
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
        tpd = new ThreadIPDetector(ipGen, reporter);
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
    public IpGenerator ipGen;
    public Vector<String> availableHosts;
    public MyReporter reporter;
    private boolean alive = true;
    private boolean stopped = false;

    public ThreadIPDetector(IpGenerator ipGen, MyReporter reporter) {
        this.ipGen = ipGen;
        this.reporter = reporter;
        availableHosts = new Vector<>();
    }

    public boolean isStopped() {
        return stopped;
    }

    public synchronized String next() {
        try {
            this.reporter.report(ipGen.cursor, ipGen.genRange, false);
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
        while ((host = next()) != null && alive) {
            if (tryAddress(host)) {
                addHost(host);
            }
        }
    }

    public boolean tryAddress(String host) { // 判断地址是否有效
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isReachable(Config.timeout)) {
                Log.e("Search", "Reached: " + address.getHostAddress());
                String ip = address.getHostAddress();
                InetSocketAddress targetAddressWithPort = new InetSocketAddress(ip, IPSearcher.targetPort);
                Socket socket = new Socket();
                socket.connect(targetAddressWithPort, Config.timeout);
                socket.close();
                return true;
            }
        } catch (IOException ignore) {
        }
        return false;
    }

    public void start() {
        if (!alive) {
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
        alive = false;
    }

    public void stop() {
        alive = false;
        stopped = true;
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

