package test;

import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ConvergenceTest {

    public static void main(String[] args) throws Exception {
        String host  = "localhost";
        int port     = 12345;
        int nClients = 3;
        int ops      = 5;
        boolean push = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":    host     = args[++i]; break;
                case "--port":    port     = Integer.parseInt(args[++i]); break;
                case "--clients": nClients = Integer.parseInt(args[++i]); break;
                case "--ops":     ops      = Integer.parseInt(args[++i]); break;
                case "--push":    push     = true; break;
            }
        }

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║        Convergence Test                  ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.printf("Server: %s:%d | Clients: %d | Ops/client: %d | Mode: %s%n%n",
            host, port, nClients, ops, push ? "PUSH" : "PULL");



        List<AutoClient> clients = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int c = 0; c < nClients; c++) {
            AutoClient ac = new AutoClient(host, port, "Client" + c, ops, push);
            clients.add(ac);
            Thread t = new Thread(ac);
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            t.join(30_000);
        }



        Thread.sleep(1000);

        System.out.println("\n── Verification: fetching document from " + nClients + " connections ──");

        List<List<String>> snapshots = new ArrayList<>();
        for (int c = 0; c < nClients; c++) {
            snapshots.add(fetchDocument(host, port, push));
        }



        List<String> reference = snapshots.get(0);
        boolean allMatch = true;

        for (int c = 1; c < snapshots.size(); c++) {
            if (!reference.equals(snapshots.get(c))) {
                allMatch = false;
                System.out.println("DIVERGENCE between snapshot 0 and snapshot " + c);
                System.out.println("  Ref size: " + reference.size()
                    + " vs " + snapshots.get(c).size());
            }
        }

        System.out.println();
        if (allMatch) {
            System.out.println("✓ CONVERGENCE OK — all snapshots identical ("
                + reference.size() + " lines)");
            for (int i = 0; i < Math.min(reference.size(), 10); i++) {
                System.out.println("  " + (i + 1) + ": " + reference.get(i));
            }
            if (reference.size() > 10) {
                System.out.println("  ... (" + (reference.size() - 10) + " more)");
            }
        } else {
            System.out.println("✗ DIVERGENCE DETECTED");
        }
    }


    private static List<String> fetchDocument(String host, int port, boolean push)
            throws Exception {
        try (
            Socket s = new Socket(host, port);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(s.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)
        ) {

            if (push) {
                return readUntilDone(in);
            }
            out.println("GETD");
            return readUntilDone(in);
        }
    }

    private static List<String> readUntilDone(BufferedReader in) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("PUSH ")) continue;
            if (line.startsWith(ProtocolConstants.DONE)) break;
            if (line.startsWith(ProtocolConstants.LINE)) {
                String rest = line.substring(5).trim();
                int space = rest.indexOf(' ');
                lines.add(space >= 0 ? rest.substring(space + 1) : "");
            }
        }
        return lines;
    }
}
