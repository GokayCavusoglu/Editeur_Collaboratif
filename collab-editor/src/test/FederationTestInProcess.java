package test;

import federation.FederatedServer;
import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class FederationTestInProcess {

    static final int PORT_A = 12350;
    static final int PORT_B = 12351;
    static final int PORT_C = 12352;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Federation Test (3 Servers, In-Process) ===\n");



        FederatedServer serverA = new FederatedServer(PORT_A, "Server-A");
        FederatedServer serverB = new FederatedServer(PORT_B, "Server-B");
        FederatedServer serverC = new FederatedServer(PORT_C, "Server-C");

        Thread tA = daemonThread(() -> { try { serverA.start(); } catch (Exception e) {} });
        Thread tB = daemonThread(() -> { try { serverB.start(); } catch (Exception e) {} });
        Thread tC = daemonThread(() -> { try { serverC.start(); } catch (Exception e) {} });
        tA.start(); tB.start(); tC.start();
        Thread.sleep(1000);

        System.out.println("Servers started. Connecting peers...\n");



        serverA.connectToPeer("localhost", PORT_B);
        serverA.connectToPeer("localhost", PORT_C);
        serverB.connectToPeer("localhost", PORT_A);
        serverB.connectToPeer("localhost", PORT_C);
        serverC.connectToPeer("localhost", PORT_A);
        serverC.connectToPeer("localhost", PORT_B);

        Thread.sleep(2000);
        System.out.println("Peers connected.\n");



        System.out.println("── Sending mutations ──");
        sendCommands(PORT_A, new String[]{
            "ADDL 1 [A] Line from Server-A #1",
            "ADDL 2 [A] Line from Server-A #2",
        });

        Thread.sleep(500);

        sendCommands(PORT_B, new String[]{
            "ADDL 1 [B] Line from Server-B #1",
        });

        Thread.sleep(500);

        sendCommands(PORT_C, new String[]{
            "ADDL 1 [C] Line from Server-C #1",
        });


        System.out.println("\nWaiting 3s for propagation...");
        Thread.sleep(3000);



        System.out.println("\n── Fetching documents ──");
        List<String> docA = fetchDocument(PORT_A);
        List<String> docB = fetchDocument(PORT_B);
        List<String> docC = fetchDocument(PORT_C);

        System.out.println("Server-A (" + docA.size() + " lines): " + docA);
        System.out.println("Server-B (" + docB.size() + " lines): " + docB);
        System.out.println("Server-C (" + docC.size() + " lines): " + docC);


        String[] expected = {
            "[A] Line from Server-A #1",
            "[A] Line from Server-A #2",
            "[B] Line from Server-B #1",
            "[C] Line from Server-C #1",
        };

        boolean allPresent = true;
        for (String exp : expected) {
            boolean inA = docA.stream().anyMatch(l -> l.contains(exp));
            boolean inB = docB.stream().anyMatch(l -> l.contains(exp));
            boolean inC = docC.stream().anyMatch(l -> l.contains(exp));
            if (!inA || !inB || !inC) {
                allPresent = false;
                System.out.println("  MISSING: '" + exp + "' A=" + inA + " B=" + inB + " C=" + inC);
            }
        }

        System.out.println();
        boolean sameSize = docA.size() == docB.size() && docB.size() == docC.size();
        boolean exactMatch = docA.equals(docB) && docB.equals(docC);

        if (allPresent && sameSize) {
            System.out.println("FEDERATION OK — all servers have all " + docA.size() + " lines");
            if (exactMatch) {
                System.out.println("EXACT MATCH — documents are identical across servers");
            } else {
                System.out.println("ORDER DIFFERS — expected without a master server (Task 5)");
            }
        } else if (allPresent) {
            System.out.println("PARTIAL — all content present but sizes differ (race condition)");
        } else {
            System.out.println("FEDERATION FAILED — some content missing");
        }


        serverA.stop(); serverB.stop(); serverC.stop();
        System.exit(allPresent ? 0 : 1);
    }



    static void sendCommands(int port, String[] cmds) throws Exception {
        try (Socket s = new Socket("localhost", port);
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)) {

            consumeDone(in);
            for (String cmd : cmds) {
                out.println(cmd);
                String resp = readSkippingPush(in);
                System.out.println("  :" + port + " <- " + cmd + " -> " + resp);
            }
        }
    }

    static List<String> fetchDocument(int port) throws Exception {
        try (Socket s = new Socket("localhost", port);
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)) {

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

    static String readSkippingPush(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.startsWith("PUSH ")) return line;
        }
        return null;
    }

    static void consumeDone(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith(ProtocolConstants.DONE)) break;
        }
    }

    static Thread daemonThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }
}
