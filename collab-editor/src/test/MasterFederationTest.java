package test;

import core.SynchronizedDocumentManager;
import dispatch.ServerDispatch;
import dispatch.DispatchClient;
import federation.master.*;
import federation.master.PeersConfig.PeerAddress;
import protocol.ProtocolConstants;
import benchmark.BenchmarkRunner;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class MasterFederationTest {

    static final int MASTER_PORT = 12360;
    static final int SLAVE1_PORT = 12361;
    static final int SLAVE2_PORT = 12362;
    static final int DISPATCH_PORT = 12300;

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================");
        System.out.println("  Master Federation + Dispatch + Benchmark + Fault Tolerance");
        System.out.println("================================================================\n");

        testMasterFederation();
        testDispatchServer();
        testBenchmark();
        testFaultTolerance();
    }





    static void testMasterFederation() throws Exception {
        System.out.println("── Test 1: Master Federation ──\n");

        PeersConfig config = buildConfig();
        MasterFederatedServer master = new MasterFederatedServer(MASTER_PORT, "Master", config);
        MasterFederatedServer slave1 = new MasterFederatedServer(SLAVE1_PORT, "Slave1", config);
        MasterFederatedServer slave2 = new MasterFederatedServer(SLAVE2_PORT, "Slave2", config);

        Thread tM = daemon(() -> { try { master.start(); } catch (Exception e) {} });
        Thread tS1 = daemon(() -> { try { slave1.start(); } catch (Exception e) {} });
        Thread tS2 = daemon(() -> { try { slave2.start(); } catch (Exception e) {} });
        tM.start(); Thread.sleep(500);
        tS1.start(); tS2.start();
        Thread.sleep(2000);

        System.out.println("  Master: " + master.isMaster() + " | Slave1: " + !slave1.isMaster()
            + " | Slave2: " + !slave2.isMaster());

        try {

            sendCommands(MASTER_PORT, new String[]{
                "ADDL 1 [Master] Line 1",
                "ADDL 2 [Master] Line 2",
            });

            Thread.sleep(500);


            sendCommands(SLAVE1_PORT, new String[]{
                "ADDL 1 [Slave1] Line 3",
            });

            Thread.sleep(1000);


            List<String> docM  = fetchDocument(MASTER_PORT);
            List<String> docS1 = fetchDocument(SLAVE1_PORT);
            List<String> docS2 = fetchDocument(SLAVE2_PORT);

            System.out.println("\n  Master  (" + docM.size()  + " lines): " + docM);
            System.out.println("  Slave1  (" + docS1.size() + " lines): " + docS1);
            System.out.println("  Slave2  (" + docS2.size() + " lines): " + docS2);

            boolean allContent = true;
            for (String expected : new String[]{"[Master] Line 1", "[Master] Line 2", "[Slave1] Line 3"}) {
                boolean inM  = docM.stream().anyMatch(l -> l.contains(expected));
                boolean inS1 = docS1.stream().anyMatch(l -> l.contains(expected));
                boolean inS2 = docS2.stream().anyMatch(l -> l.contains(expected));
                if (!inM || !inS1 || !inS2) {
                    allContent = false;
                    System.out.println("  MISSING: " + expected);
                }
            }

            System.out.println(allContent
                ? "\n  ✓ MASTER FEDERATION OK — all content propagated"
                : "\n  ✗ MASTER FEDERATION FAILED");

        } finally {
            master.stop(); slave1.stop(); slave2.stop();
            Thread.sleep(500);
        }
    }





    static void testDispatchServer() throws Exception {
        System.out.println("\n── Test 2: Dispatch Server (Load Balancing) ──\n");

        PeersConfig config = buildConfig();
        MasterFederatedServer master = new MasterFederatedServer(MASTER_PORT, "Master", config);
        MasterFederatedServer slave1 = new MasterFederatedServer(SLAVE1_PORT, "Slave1", config);

        Thread tM = daemon(() -> { try { master.start(); } catch (Exception e) {} });
        Thread tS1 = daemon(() -> { try { slave1.start(); } catch (Exception e) {} });
        tM.start(); Thread.sleep(500); tS1.start(); Thread.sleep(1000);


        List<PeerAddress> servers = Arrays.asList(
            new PeerAddress("localhost", MASTER_PORT),
            new PeerAddress("localhost", SLAVE1_PORT)
        );
        ServerDispatch dispatch = new ServerDispatch(DISPATCH_PORT, servers,
            ServerDispatch.Strategy.ROUND_ROBIN);
        Thread tD = daemon(() -> { try { dispatch.start(); } catch (Exception e) {} });
        tD.start();
        Thread.sleep(2000);

        try {

            Map<String, Integer> assignments = new HashMap<>();
            for (int i = 0; i < 4; i++) {
                String assigned = DispatchClient.getServerAssignment("localhost", DISPATCH_PORT);
                assignments.merge(assigned, 1, Integer::sum);
                System.out.println("  Client " + i + " assigned to " + assigned);
            }

            System.out.println("  Distribution: " + assignments);
            boolean balanced = assignments.size() == 2;
            System.out.println(balanced
                ? "\n  ✓ DISPATCH OK — load distributed across servers"
                : "\n  ✗ DISPATCH FAILED — unbalanced");

        } finally {
            dispatch.stop(); master.stop(); slave1.stop();
            Thread.sleep(500);
        }
    }





    static void testBenchmark() throws Exception {
        System.out.println("\n── Test 3: Performance Benchmark ──\n");

        PeersConfig config = buildConfig();
        MasterFederatedServer master = new MasterFederatedServer(MASTER_PORT, "Master", config);
        Thread tM = daemon(() -> { try { master.start(); } catch (Exception e) {} });
        tM.start(); Thread.sleep(1000);

        try {
            BenchmarkRunner bench = new BenchmarkRunner("localhost", MASTER_PORT, 50, 5, false);
            System.out.println("  clients | avg_latency_us | p95_us | throughput_ops/s");
            System.out.println("  --------|----------------|--------|------------------");

            for (int n : new int[]{1, 2, 5, 10}) {
                double[] r = bench.runScenario(n);
                System.out.printf("  %7d | %14.0f | %6.0f | %16.1f%n",
                    n, r[0], r[2], r[4]);
            }

            System.out.println("\n  ✓ BENCHMARK COMPLETE");
        } finally {
            master.stop();
            Thread.sleep(500);
        }
    }





    static void testFaultTolerance() throws Exception {
        System.out.println("\n── Test 4: Fault Tolerance + Leader Election ──\n");

        PeersConfig config = buildConfig();

        MasterFederatedServer master = new MasterFederatedServer(MASTER_PORT, "Master", config);
        MasterFederatedServer slave1 = new MasterFederatedServer(SLAVE1_PORT, "Slave1", config);
        MasterFederatedServer slave2 = new MasterFederatedServer(SLAVE2_PORT, "Slave2", config);

        Thread tM = daemon(() -> { try { master.start(); } catch (Exception e) {} });
        Thread tS1 = daemon(() -> { try { slave1.start(); } catch (Exception e) {} });
        Thread tS2 = daemon(() -> { try { slave2.start(); } catch (Exception e) {} });
        tM.start(); Thread.sleep(500);
        tS1.start(); tS2.start();
        Thread.sleep(2000);


        ElectionManager em1 = new ElectionManager(SLAVE1_PORT, "Slave1", config,
            () -> System.out.println("  [Slave1] I AM NOW THE LEADER!"),
            () -> System.out.println("  [Slave1] Acknowledged new leader"));
        ElectionManager em2 = new ElectionManager(SLAVE2_PORT, "Slave2", config,
            () -> System.out.println("  [Slave2] I AM NOW THE LEADER!"),
            () -> System.out.println("  [Slave2] Acknowledged new leader"));
        em1.start(); em2.start();
        Thread.sleep(1000);

        try {

            sendCommands(MASTER_PORT, new String[]{"ADDL 1 [Pre-failure] Data"});
            Thread.sleep(500);

            System.out.println("  Master alive: " + master.isRunning());
            System.out.println("  Slave1 masterAlive: " + slave1.isMasterAlive());


            System.out.println("\n  >>> KILLING MASTER <<<\n");
            master.stop();
            Thread.sleep(1000);

            System.out.println("  Master alive: " + master.isRunning());


            System.out.println("  Triggering election...");
            em2.triggerElection();
            Thread.sleep(4000);

            PeerAddress newLeader = em2.getCurrentLeader();
            System.out.println("\n  Election result: new leader = " + newLeader);


            boolean correctLeader = newLeader != null && newLeader.port == SLAVE2_PORT;
            System.out.println(correctLeader
                ? "\n  ✓ ELECTION OK — highest-port server elected"
                : "\n  ✗ ELECTION ISSUE — unexpected leader: " + newLeader);


            List<String> docS1 = fetchDocument(SLAVE1_PORT);
            List<String> docS2 = fetchDocument(SLAVE2_PORT);
            boolean dataPreserved = docS1.stream().anyMatch(l -> l.contains("[Pre-failure] Data"))
                                 && docS2.stream().anyMatch(l -> l.contains("[Pre-failure] Data"));
            System.out.println(dataPreserved
                ? "  ✓ DATA PRESERVED — slaves retain pre-failure data"
                : "  ✗ DATA LOST");

        } finally {
            em1.stop(); em2.stop();
            slave1.stop(); slave2.stop();
            Thread.sleep(500);
        }
    }





    static PeersConfig buildConfig() {
        String cfgText = "master = localhost " + MASTER_PORT + "\n"
                       + "peer = localhost " + SLAVE1_PORT + "\n"
                       + "peer = localhost " + SLAVE2_PORT + "\n";
        try {
            return PeersConfig.load(new StringReader(cfgText));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
                readSkipping(in);
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

    static String readSkipping(BufferedReader in) throws IOException {
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

    static Thread daemon(Runnable r) {
        Thread t = new Thread(r); t.setDaemon(true); return t;
    }
}
