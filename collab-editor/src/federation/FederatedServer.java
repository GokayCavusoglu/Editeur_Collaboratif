package federation;

import core.DocumentManager;
import core.SynchronizedDocumentManager;
import network.ClientHandler;
import network.ClientRegistry;
import network.CommandExecutor;
import network.impl.ConcurrentClientRegistry;
import network.impl.PushClientHandler;
import protocol.ProtocolConstants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FederatedServer {

    private final int port;
    private final String serverId;
    private final DocumentManager documentManager;
    private final CommandExecutor executor;
    private final ClientRegistry clientRegistry;
    private final ExecutorService threadPool;
    private final CopyOnWriteArrayList<PeerConnection> peers;

    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public FederatedServer(int port, String serverId) {
        this.port            = port;
        this.serverId        = serverId;
        this.documentManager = new SynchronizedDocumentManager();
        this.executor        = new CommandExecutor(documentManager);
        this.clientRegistry  = new ConcurrentClientRegistry();
        this.threadPool      = Executors.newCachedThreadPool();
        this.peers           = new CopyOnWriteArrayList<>();
    }

    public DocumentManager getDocumentManager() { return documentManager; }



    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("[" + serverId + "] Listening on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();


                FederatedClientHandler handler = new FederatedClientHandler(
                    clientSocket, this);
                threadPool.submit(handler);
            } catch (IOException e) {
                if (running)
                    System.err.println("[" + serverId + "] Accept error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        for (PeerConnection peer : peers) peer.close();
        threadPool.shutdownNow();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
        System.out.println("[" + serverId + "] Stopped.");
    }




    public void connectToPeer(String peerHost, int peerPort) {
        threadPool.submit(() -> {
            try {
                Socket socket = new Socket(peerHost, peerPort);
                PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));



                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(ProtocolConstants.DONE)) break;
                }


                out.println("PEER_HELLO " + serverId + " " + port);

                PeerConnection peer = new PeerConnection(
                    peerHost + ":" + peerPort, socket, in, out);
                peers.add(peer);

                System.out.println("[" + serverId + "] Connected to peer "
                    + peerHost + ":" + peerPort);


                while (running && (line = in.readLine()) != null) {
                    handlePeerMessage(line, peer);
                }
            } catch (IOException e) {
                System.err.println("[" + serverId + "] Peer connection to "
                    + peerHost + ":" + peerPort + " failed: " + e.getMessage());
            }
        });
    }




    void broadcastAndForward(String rawCommand, ClientHandler sender) {

        clientRegistry.broadcast(sender, "PUSH " + rawCommand);


        forwardToPeers(rawCommand);
    }


    private final java.util.concurrent.atomic.AtomicLong seqCounter =
        new java.util.concurrent.atomic.AtomicLong(0);

    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> seenMessages =
        new java.util.concurrent.ConcurrentHashMap<>();


    private void handlePeerMessage(String message, PeerConnection source) {
        if (!message.startsWith("PEER_PUSH ")) return;


        String payload = message.substring("PEER_PUSH ".length());
        int firstSpace = payload.indexOf(' ');
        if (firstSpace < 0) return;

        String msgId = payload.substring(0, firstSpace);
        String rawCommand = payload.substring(firstSpace + 1);


        if (seenMessages.putIfAbsent(msgId, Boolean.TRUE) != null) {
            return;
        }

        String keyword = rawCommand.length() >= 4 ? rawCommand.substring(0, 4) : "";
        if (!keyword.equals("ADDL") && !keyword.equals("RMVL") && !keyword.equals("MDFL"))
            return;


        executor.process(rawCommand);


        clientRegistry.broadcastAll("PUSH " + rawCommand);

        System.out.println("[" + serverId + "] Applied peer change: " + rawCommand);


        if (seenMessages.size() > 10000) seenMessages.clear();
    }


    private void forwardToPeers(String rawCommand) {
        String msgId = serverId + ":" + seqCounter.incrementAndGet();

        seenMessages.put(msgId, Boolean.TRUE);

        for (PeerConnection peer : peers) {
            try {
                peer.send("PEER_PUSH " + msgId + " " + rawCommand);
            } catch (Exception e) {
                System.err.println("[" + serverId + "] Forward to peer "
                    + peer.getId() + " failed: " + e.getMessage());
            }
        }
    }



    String getServerId()              { return serverId; }
    CommandExecutor getExecutor()     { return executor; }
    ClientRegistry getClientRegistry(){ return clientRegistry; }





    static class FederatedClientHandler implements ClientHandler, Runnable {

        private final Socket socket;
        private final FederatedServer server;
        private final String clientId;
        private BufferedReader in;
        private PrintWriter out;
        private volatile boolean connected = true;
        private boolean isPeer = false;

        FederatedClientHandler(Socket socket, FederatedServer server) {
            this.socket   = socket;
            this.server   = server;
            this.clientId = socket.getRemoteSocketAddress().toString();
        }

        @Override
        public synchronized void send(String message) {
            if (connected && out != null) {
                for (String line : message.split("\n")) {
                    out.println(line);
                }
            }
        }

        @Override public String getClientId()  { return clientId; }
        @Override public boolean isConnected()  { return connected; }
        @Override public void close() {
            connected = false;
            try { socket.close(); } catch (IOException e) {}
        }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);





                sendFullDocument();


                String firstLine = in.readLine();
                if (firstLine == null) return;

                if (firstLine.startsWith("PEER_HELLO")) {
                    isPeer = true;
                    handleIncomingPeer(firstLine);
                } else {
                    handleRegularClient(firstLine);
                }
            } catch (IOException e) {

            } finally {
                connected = false;
                if (!isPeer) server.getClientRegistry().unregister(this);
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void handleRegularClient(String firstLine) throws IOException {
            System.out.println("[" + server.getServerId() + "] Client connected: " + clientId);
            server.getClientRegistry().register(this);


            processClientMessage(firstLine.trim());

            String line;
            while (connected && (line = in.readLine()) != null) {
                processClientMessage(line.trim());
            }
        }

        private void processClientMessage(String trimmed) {
            if (trimmed.isEmpty()) return;
            String keyword = trimmed.length() >= 4 ? trimmed.substring(0, 4) : "";

            if (keyword.equals("ADDL") || keyword.equals("RMVL") || keyword.equals("MDFL")) {

                String response = server.getExecutor().process(trimmed);
                send(response);
                server.broadcastAndForward(trimmed, this);
            } else {

                String response = server.getExecutor().process(trimmed);
                send(response);
            }
        }

        private void handleIncomingPeer(String handshake) throws IOException {
            System.out.println("[" + server.getServerId()
                + "] Incoming peer connection: " + handshake);


            PeerConnection peer = new PeerConnection(clientId, socket, in, out);
            server.peers.add(peer);


            String line;
            while (connected && (line = in.readLine()) != null) {
                server.handlePeerMessage(line, peer);
            }

            server.peers.remove(peer);
        }

        private void sendFullDocument() {
            List<String> lines = server.getDocumentManager().getDocument();
            for (int i = 0; i < lines.size(); i++) {
                send(ProtocolConstants.line(i + 1, lines.get(i)));
            }
            send(ProtocolConstants.done());
        }
    }





    static class PeerConnection {
        private final String id;
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        PeerConnection(String id, Socket socket, BufferedReader in, PrintWriter out) {
            this.id     = id;
            this.socket = socket;
            this.in     = in;
            this.out    = out;
        }

        String getId() { return id; }

        synchronized void send(String message) {
            out.println(message);
        }

        void close() {
            try { socket.close(); } catch (IOException e) {}
        }
    }



    public static void main(String[] args) throws Exception {
        int port = 12345;
        String id = "Server-A";
        List<String> peerAddresses = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": case "-p": port = Integer.parseInt(args[++i]); break;
                case "--id":              id   = args[++i]; break;
                case "--peer":            peerAddresses.add(args[++i]); break;
            }
        }

        FederatedServer server = new FederatedServer(port, id);


        server.documentManager.addLine(1, "Federated editor — " + id);
        server.documentManager.addLine(2, "Connected peers will sync.");


        final List<String> finalPeers = peerAddresses;
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException e) {}
            for (String addr : finalPeers) {
                String[] parts = addr.split(":");
                server.connectToPeer(parts[0], Integer.parseInt(parts[1]));
            }
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
