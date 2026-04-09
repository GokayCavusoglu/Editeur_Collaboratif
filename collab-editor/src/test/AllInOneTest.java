package test;

import core.SynchronizedDocumentManager;
import core.DocumentManager;
import network.impl.ServerCentral;
import network.impl.ServerCentralPush;
import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class AllInOneTest {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "pull";
        int nClients = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        int ops = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        boolean push = mode.equals("push");
        int port = push ? 12346 : 12345;

        System.out.println("=== Convergence Test ===");
        System.out.println("Mode: " + mode.toUpperCase() + " | Clients: " + nClients
            + " | Ops/client: " + ops + " | Port: " + port);
        System.out.println();



        DocumentManager doc = new SynchronizedDocumentManager();
        doc.addLine(1, "Initial line 1");
        doc.addLine(2, "Initial line 2");

        Thread serverThread;
        final Object serverReady = new Object();

        if (push) {
            ServerCentralPush server = new ServerCentralPush(port, doc);
            serverThread = new Thread(() -> {
                try {
                    synchronized (serverReady) { serverReady.notifyAll(); }
                    server.start();
                } catch (IOException e) {
                    System.err.println("Server error: " + e.getMessage());
                }
            });
            serverThread.setDaemon(true);
            synchronized (serverReady) {
                serverThread.start();
                serverReady.wait(2000);
            }
            Thread.sleep(500);


            boolean ok = runConvergenceTest("localhost", port, nClients, ops, true);
            server.stop();
            System.exit(ok ? 0 : 1);
        } else {
            ServerCentral server = new ServerCentral(port, doc);
            serverThread = new Thread(() -> {
                try {
                    synchronized (serverReady) { serverReady.notifyAll(); }
                    server.start();
                } catch (IOException e) {
                    System.err.println("Server error: " + e.getMessage());
                }
            });
            serverThread.setDaemon(true);
            synchronized (serverReady) {
                serverThread.start();
                serverReady.wait(2000);
            }
            Thread.sleep(500);

            boolean ok = runConvergenceTest("localhost", port, nClients, ops, false);
            server.stop();
            System.exit(ok ? 0 : 1);
        }
    }

    static boolean runConvergenceTest(String host, int port, int nClients, int ops,
                                       boolean push) throws Exception {


        List<AutoClient> clients = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int c = 0; c < nClients; c++) {
            AutoClient ac = new AutoClient(host, port, "Client" + c, ops, push);
            clients.add(ac);
            Thread t = new Thread(ac);
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) t.join(30_000);
        Thread.sleep(1000);



        System.out.println("\n── Verification ──");
        List<List<String>> snapshots = new ArrayList<>();
        for (int c = 0; c < nClients; c++) {
            snapshots.add(fetchDocument(host, port, push));
        }



        List<String> ref = snapshots.get(0);
        boolean ok = true;
        for (int c = 1; c < snapshots.size(); c++) {
            if (!ref.equals(snapshots.get(c))) {
                ok = false;
                System.out.println("DIVERGENCE: snapshot 0 (" + ref.size()
                    + " lines) vs snapshot " + c + " (" + snapshots.get(c).size() + " lines)");
            }
        }

        System.out.println();
        if (ok) {
            System.out.println("CONVERGENCE OK — all " + nClients
                + " snapshots identical (" + ref.size() + " lines)");
            for (int i = 0; i < Math.min(ref.size(), 10); i++) {
                System.out.println("  " + (i + 1) + ": " + ref.get(i));
            }
            if (ref.size() > 10) System.out.println("  ... (" + (ref.size() - 10) + " more)");
        } else {
            System.out.println("DIVERGENCE DETECTED");
        }
        return ok;
    }

    static List<String> fetchDocument(String host, int port, boolean push) throws Exception {
        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)) {
            if (push) {
                return readUntilDone(in);
            }
            out.println("GETD");
            return readUntilDone(in);
        }
    }

    static List<String> readUntilDone(BufferedReader in) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("PUSH ")) continue;
            if (line.startsWith(ProtocolConstants.DONE)) break;
            if (line.startsWith(ProtocolConstants.LINE)) {
                String rest = line.substring(5).trim();
                int sp = rest.indexOf(' ');
                lines.add(sp >= 0 ? rest.substring(sp + 1) : "");
            }
        }
        return lines;
    }
}
