package benchmark;

import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class BenchmarkRunner {

    private final String host;
    private final int port;
    private final int opsPerClient;
    private final int warmupOps;
    private final boolean pushMode;

    public BenchmarkRunner(String host, int port, int opsPerClient,
                            int warmupOps, boolean pushMode) {
        this.host         = host;
        this.port         = port;
        this.opsPerClient = opsPerClient;
        this.warmupOps    = warmupOps;
        this.pushMode     = pushMode;
    }


    public double[] runScenario(int numClients) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        List<Future<List<Long>>> futures = new ArrayList<>();

        AtomicLong totalOps = new AtomicLong(0);
        long startTime = System.nanoTime();

        for (int c = 0; c < numClients; c++) {
            final int clientId = c;
            futures.add(pool.submit(() -> runClient(clientId, totalOps)));
        }


        List<Long> allLatencies = new ArrayList<>();
        for (Future<List<Long>> f : futures) {
            allLatencies.addAll(f.get(60, TimeUnit.SECONDS));
        }

        long elapsed = System.nanoTime() - startTime;
        pool.shutdown();


        Collections.sort(allLatencies);
        double avgLatency = allLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = percentile(allLatencies, 50);
        long p95 = percentile(allLatencies, 95);
        long p99 = percentile(allLatencies, 99);
        double throughput = (totalOps.get() * 1_000_000_000.0) / elapsed;

        return new double[]{avgLatency, p50, p95, p99, throughput};
    }

    private List<Long> runClient(int clientId, AtomicLong totalOps) {
        List<Long> latencies = new ArrayList<>();
        Random rng = new Random(clientId);

        try (Socket sock = new Socket(host, port);
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(sock.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)) {


            if (pushMode) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(ProtocolConstants.DONE)) break;
                }
            }


            for (int i = 0; i < warmupOps; i++) {
                out.println("ADDL 1 warmup" + i);
                readResponse(in);
            }


            for (int i = 0; i < opsPerClient; i++) {
                String cmd = randomCommand(rng, i, clientId);

                long t0 = System.nanoTime();
                out.println(cmd);
                readResponse(in);
                long t1 = System.nanoTime();

                long latencyUs = (t1 - t0) / 1000;
                latencies.add(latencyUs);
                totalOps.incrementAndGet();
            }
        } catch (Exception e) {
            System.err.println("[Bench-" + clientId + "] Error: " + e.getMessage());
        }

        return latencies;
    }

    private String randomCommand(Random rng, int opNum, int clientId) {
        int action = rng.nextInt(4);
        switch (action) {
            case 0: return "ADDL 1 bench-" + clientId + "-" + opNum;
            case 1: return "MDFL 1 bench-" + clientId + "-" + opNum;
            case 2: return "GETD";
            default: return "ADDL 1 bench-" + clientId + "-" + opNum;
        }
    }

    private String readResponse(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("PUSH ")) continue;
            if (line.startsWith(ProtocolConstants.DONE)) return line;
            return line;
        }
        return null;
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }



    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port    = 12345;
        int ops     = 100;
        int warmup  = 10;
        boolean push = false;
        int[] clientCounts = {1, 2, 5, 10, 20};

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":    host   = args[++i]; break;
                case "--port":    port   = Integer.parseInt(args[++i]); break;
                case "--ops":     ops    = Integer.parseInt(args[++i]); break;
                case "--warmup":  warmup = Integer.parseInt(args[++i]); break;
                case "--push":    push   = true; break;
            }
        }

        BenchmarkRunner bench = new BenchmarkRunner(host, port, ops, warmup, push);


        System.out.println("clients,avg_latency_us,p50_us,p95_us,p99_us,throughput_ops_sec");

        for (int n : clientCounts) {
            System.err.println("Running with " + n + " clients...");
            double[] results = bench.runScenario(n);
            System.out.printf("%d,%.0f,%.0f,%.0f,%.0f,%.1f%n",
                n, results[0], results[1], results[2], results[3], results[4]);
        }
    }
}
