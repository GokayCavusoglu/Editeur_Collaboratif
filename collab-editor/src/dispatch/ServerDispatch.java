package dispatch;

import federation.master.PeersConfig;
import federation.master.PeersConfig.PeerAddress;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerDispatch {

    public enum Strategy { ROUND_ROBIN, LEAST_CONNECTIONS }

    private final int port;
    private final List<PeerAddress> servers;
    private final Strategy strategy;
    private volatile boolean running = true;


    private final AtomicInteger rrCounter = new AtomicInteger(0);


    private final ConcurrentHashMap<PeerAddress, AtomicInteger> connectionCounts
        = new ConcurrentHashMap<>();


    private final ConcurrentHashMap<PeerAddress, Boolean> serverHealth
        = new ConcurrentHashMap<>();

    public ServerDispatch(int port, List<PeerAddress> servers, Strategy strategy) {
        this.port     = port;
        this.servers  = servers;
        this.strategy = strategy;
        for (PeerAddress s : servers) {
            connectionCounts.put(s, new AtomicInteger(0));
            serverHealth.put(s, true);
        }
    }

    public void start() throws IOException {
        ServerSocket ss = new ServerSocket(port);
        System.out.println("[Dispatch] Listening on port " + port
            + " | Strategy: " + strategy + " | Servers: " + servers.size());


        Thread healthChecker = new Thread(this::healthCheckLoop);
        healthChecker.setDaemon(true);
        healthChecker.start();

        while (running) {
            try {
                Socket client = ss.accept();
                handleDispatchRequest(client);
            } catch (IOException e) {
                if (running) System.err.println("[Dispatch] Error: " + e.getMessage());
            }
        }
        ss.close();
    }

    public void stop() { running = false; }

    private void handleDispatchRequest(Socket client) {
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(client.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(client.getOutputStream(), "UTF-8"), true)
        ) {
            String request = in.readLine();
            if (request != null && request.trim().equals("CONNECT")) {
                PeerAddress assigned = chooseServer();
                if (assigned != null) {
                    out.println("SERVER " + assigned.host + " " + assigned.port);
                    connectionCounts.get(assigned).incrementAndGet();
                    System.out.println("[Dispatch] Assigned client to " + assigned
                        + " (connections: " + connectionCounts.get(assigned).get() + ")");
                } else {
                    out.println("ERRL No servers available");
                }
            }

            if (request != null && request.trim().startsWith("DISCONNECT ")) {
                String[] parts = request.trim().split("\\s+");
                if (parts.length >= 3) {
                    PeerAddress addr = new PeerAddress(parts[1], Integer.parseInt(parts[2]));
                    AtomicInteger count = connectionCounts.get(addr);
                    if (count != null) count.decrementAndGet();
                }
            }
        } catch (Exception e) {

        } finally {
            try { client.close(); } catch (IOException e) {}
        }
    }

    private PeerAddress chooseServer() {
        List<PeerAddress> healthy = servers.stream()
            .filter(s -> serverHealth.getOrDefault(s, false))
            .collect(java.util.stream.Collectors.toList());

        if (healthy.isEmpty()) return null;

        switch (strategy) {
            case ROUND_ROBIN:
                int idx = rrCounter.getAndIncrement() % healthy.size();
                return healthy.get(idx);

            case LEAST_CONNECTIONS:
                PeerAddress best = null;
                int min = Integer.MAX_VALUE;
                for (PeerAddress s : healthy) {
                    int count = connectionCounts.getOrDefault(s, new AtomicInteger(0)).get();
                    if (count < min) { min = count; best = s; }
                }
                return best;

            default:
                return healthy.get(0);
        }
    }

    private void healthCheckLoop() {
        while (running) {
            for (PeerAddress server : servers) {
                boolean alive = checkServer(server);
                serverHealth.put(server, alive);
            }
            try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
        }
    }

    private boolean checkServer(PeerAddress server) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(server.host, server.port), 2000);

            sock.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    public String getLoadReport() {
        StringBuilder sb = new StringBuilder("Load Report:\n");
        for (PeerAddress s : servers) {
            sb.append("  ").append(s)
              .append(" — connections: ").append(connectionCounts.get(s).get())
              .append(" — healthy: ").append(serverHealth.get(s))
              .append("\n");
        }
        return sb.toString();
    }



    public static void main(String[] args) throws Exception {
        int port = 12300;
        String cfgPath = "peers.cfg";
        Strategy strat = Strategy.LEAST_CONNECTIONS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": port = Integer.parseInt(args[++i]); break;
                case "--config": cfgPath = args[++i]; break;
                case "--strategy": strat = Strategy.valueOf(args[++i].toUpperCase()); break;
            }
        }

        PeersConfig config = PeersConfig.load(cfgPath);
        ServerDispatch dispatch = new ServerDispatch(port, config.getAll(), strat);
        Runtime.getRuntime().addShutdownHook(new Thread(dispatch::stop));
        dispatch.start();
    }
}
