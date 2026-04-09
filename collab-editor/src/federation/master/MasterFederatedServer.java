package federation.master;

import core.DocumentManager;
import core.SynchronizedDocumentManager;
import network.ClientHandler;
import network.ClientRegistry;
import network.CommandExecutor;
import network.impl.ConcurrentClientRegistry;
import protocol.ProtocolConstants;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MasterFederatedServer {

    private final int port;
    private final String serverId;
    private final boolean isMaster;
    private final DocumentManager documentManager;
    private final CommandExecutor executor;
    private final ClientRegistry clientRegistry;
    private final ExecutorService threadPool;


    private final AtomicLong globalSequence = new AtomicLong(0);
    private final CopyOnWriteArrayList<SlaveConnection> slaves = new CopyOnWriteArrayList<>();


    private volatile Socket masterSocket;
    private volatile PrintWriter masterOut;
    private volatile BufferedReader masterIn;
    private final PeersConfig config;


    private volatile boolean running = true;
    private volatile boolean masterAlive = true;

    private int failAfterSecs = -1;
    private int pauseDurationSecs = 5;
    private int pauseIntervalSecs = 15;

    private ServerSocket serverSocket;

    public MasterFederatedServer(int port, String serverId, PeersConfig config) {
        this.port            = port;
        this.serverId        = serverId;
        this.config          = config;
        this.isMaster        = config.isMaster("127.0.0.1", port)
                            || config.isMaster("localhost", port);
        this.documentManager = new SynchronizedDocumentManager();
        this.executor        = new CommandExecutor(documentManager);
        this.clientRegistry  = new ConcurrentClientRegistry();
        this.threadPool      = Executors.newCachedThreadPool();
    }

    public DocumentManager getDocumentManager() { return documentManager; }
    public boolean isMaster()                   { return isMaster; }
    public boolean isRunning()                  { return running; }
    public boolean isMasterAlive()              { return masterAlive; }
    public int getPort()                        { return port; }
    public String getServerId()                 { return serverId; }
    public ClientRegistry getClientRegistry()   { return clientRegistry; }
    public PeersConfig getConfig()              { return config; }


    public void enableFailureSimulation(int failAfterSecs, int pauseIntervalSecs, int pauseDurationSecs) {
        this.failAfterSecs = failAfterSecs;
        this.pauseIntervalSecs = pauseIntervalSecs;
        this.pauseDurationSecs = pauseDurationSecs;
    }





    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        String role = isMaster ? "MASTER" : "SLAVE";
        System.out.println("[" + serverId + "] " + role + " listening on port " + port);

        if (isMaster && failAfterSecs > 0) {
            startFailureSimulation();
        }

        if (!isMaster) {

            threadPool.submit(this::connectToMaster);
        }


        while (running) {
            try {
                Socket sock = serverSocket.accept();
                threadPool.submit(() -> handleConnection(sock));
            } catch (IOException e) {
                if (running)
                    System.err.println("[" + serverId + "] Accept error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        for (SlaveConnection s : slaves) s.close();
        try { if (masterSocket != null) masterSocket.close(); } catch (IOException e) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
        threadPool.shutdownNow();
        System.out.println("[" + serverId + "] Stopped.");
    }





    private void handleConnection(Socket sock) {
        try {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);


            sendFullDocument(out);


            String first = in.readLine();
            if (first == null) { sock.close(); return; }

            if (first.startsWith("SLAVE_HELLO")) {
                handleSlaveConnection(sock, in, out, first);
            } else {
                handleClientConnection(sock, in, out, first);
            }
        } catch (IOException e) {
            try { sock.close(); } catch (IOException ex) {}
        }
    }



    private void handleClientConnection(Socket sock, BufferedReader in,
                                         PrintWriter out, String firstMsg) throws IOException {
        String cid = sock.getRemoteSocketAddress().toString();
        ClientSession session = new ClientSession(cid, out);
        clientRegistry.register(session);

        System.out.println("[" + serverId + "] Client connected: " + cid);

        try {
            processClientMessage(firstMsg.trim(), session, out);

            String line;
            while (running && (line = in.readLine()) != null) {
                processClientMessage(line.trim(), session, out);
            }
        } finally {
            clientRegistry.unregister(session);
            sock.close();
            System.out.println("[" + serverId + "] Client disconnected: " + cid);
        }
    }

    private void processClientMessage(String msg, ClientSession session, PrintWriter out) {
        if (msg.isEmpty()) return;
        String kw = msg.length() >= 4 ? msg.substring(0, 4) : "";

        if (kw.equals("ADDL") || kw.equals("RMVL") || kw.equals("MDFL")) {
            if (isMaster) {

                String response = executor.process(msg);
                out.println(response);
                long seq = globalSequence.incrementAndGet();
                broadcastToSlaves("MASTER_ORDER " + seq + " " + msg);
                clientRegistry.broadcast(session, "PUSH " + msg);
            } else {

                if (masterAlive && masterOut != null) {
                    masterOut.println("FORWARD " + session.getClientId() + " " + msg);


                    out.println(ProtocolConstants.done());
                } else {
                    out.println(ProtocolConstants.error("Master unavailable"));
                }
            }
        } else {

            String response = executor.process(msg);
            out.println(response);
        }
    }



    private void handleSlaveConnection(Socket sock, BufferedReader in,
                                        PrintWriter out, String handshake) throws IOException {
        String slaveId = handshake.substring("SLAVE_HELLO ".length()).trim();
        SlaveConnection slave = new SlaveConnection(slaveId, sock, out);
        slaves.add(slave);
        System.out.println("[" + serverId + "] Slave registered: " + slaveId
            + " (total: " + slaves.size() + ")");

        try {

            String line;
            while (running && (line = in.readLine()) != null) {
                if (line.startsWith("FORWARD ")) {
                    String payload = line.substring("FORWARD ".length());
                    int sp = payload.indexOf(' ');
                    if (sp < 0) continue;
                    String rawCommand = payload.substring(sp + 1);


                    executor.process(rawCommand);


                    long seq = globalSequence.incrementAndGet();
                    broadcastToSlaves("MASTER_ORDER " + seq + " " + rawCommand);


                    clientRegistry.broadcastAll("PUSH " + rawCommand);
                } else if (line.startsWith("ELECTION")) {

                    handleElectionMessage(line, slave);
                }
            }
        } finally {
            slaves.remove(slave);
            sock.close();
            System.out.println("[" + serverId + "] Slave disconnected: " + slaveId);
        }
    }

    private void broadcastToSlaves(String message) {
        for (SlaveConnection slave : slaves) {
            try {
                slave.send(message);
            } catch (Exception e) {
                System.err.println("[" + serverId + "] Broadcast to slave "
                    + slave.id + " failed: " + e.getMessage());
            }
        }
    }



    private void connectToMaster() {
        PeersConfig.PeerAddress masterAddr = config.getMaster();
        while (running) {
            try {
                System.out.println("[" + serverId + "] Connecting to master "
                    + masterAddr + "...");
                masterSocket = new Socket(masterAddr.host, masterAddr.port);
                masterIn = new BufferedReader(
                    new InputStreamReader(masterSocket.getInputStream(), "UTF-8"));
                masterOut = new PrintWriter(
                    new OutputStreamWriter(masterSocket.getOutputStream(), "UTF-8"), true);


                String line;
                while ((line = masterIn.readLine()) != null) {
                    if (line.startsWith(ProtocolConstants.DONE)) break;
                }


                masterOut.println("SLAVE_HELLO " + serverId + " " + port);
                masterAlive = true;
                System.out.println("[" + serverId + "] Connected to master.");


                while (running && (line = masterIn.readLine()) != null) {
                    if (line.startsWith("MASTER_ORDER ")) {
                        handleMasterOrder(line);
                    } else if (line.startsWith("ELECTION")) {

                    }
                }


                masterAlive = false;
                System.out.println("[" + serverId + "] Lost connection to master!");

            } catch (IOException e) {
                masterAlive = false;
                System.out.println("[" + serverId + "] Cannot reach master: " + e.getMessage());
            }


            if (running) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
            }
        }
    }

    private void handleMasterOrder(String message) {

        String payload = message.substring("MASTER_ORDER ".length());
        int sp = payload.indexOf(' ');
        if (sp < 0) return;

        String rawCommand = payload.substring(sp + 1);


        executor.process(rawCommand);


        clientRegistry.broadcastAll("PUSH " + rawCommand);
    }





    private void startFailureSimulation() {
        threadPool.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                while (running) {
                    Thread.sleep(pauseIntervalSecs * 1000L);
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;

                    if (elapsed >= failAfterSecs) {
                        System.out.println("[" + serverId + "] MASTER PERMANENT FAILURE!");
                        stop();
                        return;
                    }

                    System.out.println("[" + serverId + "] MASTER PAUSING for "
                        + pauseDurationSecs + "s...");

                    try { serverSocket.close(); } catch (IOException e) {}
                    Thread.sleep(pauseDurationSecs * 1000L);


                    if (running) {
                        serverSocket = new ServerSocket(port);
                        System.out.println("[" + serverId + "] MASTER RESUMED.");
                    }
                }
            } catch (Exception e) {
                System.out.println("[" + serverId + "] Failure sim ended: " + e.getMessage());
            }
        });
    }


    void handleElectionMessage(String message, SlaveConnection from) {

    }





    static class ClientSession implements ClientHandler {
        private final String id;
        private final PrintWriter out;
        private volatile boolean connected = true;

        ClientSession(String id, PrintWriter out) {
            this.id = id;
            this.out = out;
        }

        @Override public synchronized void send(String msg) {
            if (connected) {
                for (String line : msg.split("\n")) out.println(line);
            }
        }
        @Override public String getClientId()  { return id; }
        @Override public boolean isConnected()  { return connected; }
        @Override public void close()           { connected = false; }
    }

    static class SlaveConnection {
        final String id;
        final Socket socket;
        final PrintWriter out;

        SlaveConnection(String id, Socket socket, PrintWriter out) {
            this.id = id; this.socket = socket; this.out = out;
        }

        synchronized void send(String msg) { out.println(msg); }
        void close() { try { socket.close(); } catch (IOException e) {} }
    }



    private void sendFullDocument(PrintWriter out) {
        List<String> lines = documentManager.getDocument();
        for (int i = 0; i < lines.size(); i++) {
            out.println(ProtocolConstants.line(i + 1, lines.get(i)));
        }
        out.println(ProtocolConstants.done());
    }



    public static void main(String[] args) throws Exception {
        int port = 12345;
        String id = "Server";
        String cfgPath = "peers.cfg";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": case "-p": port = Integer.parseInt(args[++i]); break;
                case "--id":              id   = args[++i]; break;
                case "--config": case "-c": cfgPath = args[++i]; break;
            }
        }

        PeersConfig config = PeersConfig.load(cfgPath);
        MasterFederatedServer server = new MasterFederatedServer(port, id, config);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
