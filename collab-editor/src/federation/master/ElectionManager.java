package federation.master;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;

public class ElectionManager {

    private final int port;
    private final int electionPort;
    private final String serverId;
    private final PeersConfig config;
    private final Runnable onBecomeLeader;
    private final Runnable onNewLeader;

    private volatile boolean running = true;
    private volatile PeersConfig.PeerAddress currentLeader;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket electionSocket;


    private volatile boolean electionInProgress = false;
    private static final int ELECTION_TIMEOUT_MS = 3000;

    public ElectionManager(int port, String serverId, PeersConfig config,
                           Runnable onBecomeLeader, Runnable onNewLeader) {
        this.port           = port;
        this.electionPort   = port + 100;
        this.serverId       = serverId;
        this.config         = config;
        this.onBecomeLeader = onBecomeLeader;
        this.onNewLeader    = onNewLeader;
        this.currentLeader  = config.getMaster();
    }

    public PeersConfig.PeerAddress getCurrentLeader() { return currentLeader; }


    public void start() {
        pool.submit(() -> {
            try {
                electionSocket = new ServerSocket(electionPort);
                System.out.println("[Election-" + serverId + "] Listening on " + electionPort);
                while (running) {
                    try {
                        Socket sock = electionSocket.accept();
                        pool.submit(() -> handleElectionConnection(sock));
                    } catch (IOException e) {
                        if (running)
                            System.err.println("[Election] Accept error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("[Election-" + serverId + "] Cannot start: " + e.getMessage());
            }
        });
    }

    public void stop() {
        running = false;
        pool.shutdownNow();
        try { if (electionSocket != null) electionSocket.close(); } catch (IOException e) {}
    }


    public void triggerElection() {
        if (electionInProgress) return;
        electionInProgress = true;

        System.out.println("[Election-" + serverId + "] Starting election (I am port " + port + ")");

        pool.submit(() -> {
            try {

                List<PeersConfig.PeerAddress> all = config.getAll();
                boolean gotResponse = false;

                for (PeersConfig.PeerAddress peer : all) {
                    if (peer.port > port) {

                        boolean responded = sendElectionMessage(peer, "ELECTION " + port);
                        if (responded) gotResponse = true;
                    }
                }

                if (!gotResponse) {

                    declareVictory();
                } else {

                    System.out.println("[Election-" + serverId
                        + "] Higher server responded, waiting for coordinator...");

                    Thread.sleep(ELECTION_TIMEOUT_MS + 2000);
                    if (electionInProgress) {
                        System.out.println("[Election-" + serverId + "] Timeout, re-electing...");
                        electionInProgress = false;
                        triggerElection();
                    }
                }
            } catch (Exception e) {
                System.err.println("[Election-" + serverId + "] Error: " + e.getMessage());
                electionInProgress = false;
            }
        });
    }

    private void declareVictory() {
        System.out.println("[Election-" + serverId + "] I AM THE NEW LEADER (port " + port + ")");
        currentLeader = new PeersConfig.PeerAddress("localhost", port);
        electionInProgress = false;


        List<PeersConfig.PeerAddress> all = config.getAll();
        for (PeersConfig.PeerAddress peer : all) {
            if (peer.port != port) {
                sendElectionMessage(peer, "COORDINATOR " + port);
            }
        }


        if (onBecomeLeader != null) onBecomeLeader.run();
    }


    private boolean sendElectionMessage(PeersConfig.PeerAddress peer, String message) {
        int peerElectionPort = peer.port + 100;
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(peer.host, peerElectionPort), ELECTION_TIMEOUT_MS);
            sock.setSoTimeout(ELECTION_TIMEOUT_MS);

            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "UTF-8"));

            out.println(message);
            String response = in.readLine();
            return response != null && response.equals("OK");
        } catch (IOException e) {

            return false;
        }
    }

    private void handleElectionConnection(Socket sock) {
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)
        ) {
            String msg = in.readLine();
            if (msg == null) return;

            if (msg.startsWith("ELECTION ")) {


                out.println("OK");
                System.out.println("[Election-" + serverId + "] Received ELECTION, responding OK");
                electionInProgress = false;
                triggerElection();

            } else if (msg.startsWith("COORDINATOR ")) {
                int leaderPort = Integer.parseInt(msg.substring("COORDINATOR ".length()).trim());
                currentLeader = new PeersConfig.PeerAddress("localhost", leaderPort);
                electionInProgress = false;
                System.out.println("[Election-" + serverId
                    + "] New leader is port " + leaderPort);
                out.println("OK");

                if (onNewLeader != null) onNewLeader.run();
            }
        } catch (IOException e) {

        } finally {
            try { sock.close(); } catch (IOException e) {}
        }
    }
}
