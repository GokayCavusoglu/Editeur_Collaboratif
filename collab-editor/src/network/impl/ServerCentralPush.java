package network.impl;

import core.DocumentManager;
import core.SynchronizedDocumentManager;
import network.ClientRegistry;
import network.CommandExecutor;
import network.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerCentralPush implements Server {

    private final int port;
    private final DocumentManager documentManager;
    private final CommandExecutor executor;
    private final ClientRegistry registry;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public ServerCentralPush(int port) {
        this(port, new SynchronizedDocumentManager());
    }

    public ServerCentralPush(int port, DocumentManager documentManager) {
        this.port            = port;
        this.documentManager = documentManager;
        this.executor        = new CommandExecutor(documentManager);
        this.registry        = new ConcurrentClientRegistry();
        this.threadPool      = Executors.newCachedThreadPool();
    }

    public DocumentManager getDocumentManager() { return documentManager; }
    public ClientRegistry getRegistry()         { return registry; }

    @Override
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("[ServerPush] Listening on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                PushClientHandler handler = new PushClientHandler(
                    clientSocket, executor, documentManager, registry);
                threadPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[ServerPush] Accept error: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        threadPool.shutdownNow();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {  }
        System.out.println("[ServerPush] Stopped.");
    }

    @Override
    public int getPort() { return port; }



    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 12346;
        ServerCentralPush server = new ServerCentralPush(port);

        server.documentManager.addLine(1, "Welcome to the collaborative editor (PUSH).");
        server.documentManager.addLine(2, "Changes are broadcast in real time.");
        server.documentManager.addLine(3, "Try ADDL/RMVL/MDFL to see push updates.");

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
