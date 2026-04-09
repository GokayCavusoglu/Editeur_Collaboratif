package network.impl;

import core.DocumentManager;
import core.SynchronizedDocumentManager;
import network.CommandExecutor;
import network.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerCentral implements Server {

    private final int port;
    private final DocumentManager documentManager;
    private final CommandExecutor executor;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public ServerCentral(int port) {
        this(port, new SynchronizedDocumentManager());
    }


    public ServerCentral(int port, DocumentManager documentManager) {
        this.port            = port;
        this.documentManager = documentManager;
        this.executor        = new CommandExecutor(documentManager);
        this.threadPool      = Executors.newCachedThreadPool();
    }

    public DocumentManager getDocumentManager() {
        return documentManager;
    }

    @Override
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("[ServerCentral] Listening on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                TcpClientHandler handler = new TcpClientHandler(clientSocket, executor);
                threadPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[ServerCentral] Accept error: " + e.getMessage());
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
        System.out.println("[ServerCentral] Stopped.");
    }

    @Override
    public int getPort() {
        return port;
    }



    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 12345;
        ServerCentral server = new ServerCentral(port);


        server.documentManager.addLine(1, "Welcome to the collaborative editor.");
        server.documentManager.addLine(2, "Edit any line to get started.");
        server.documentManager.addLine(3, "Use ADDL/RMVL/MDFL/GETD/GETL commands.");

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
