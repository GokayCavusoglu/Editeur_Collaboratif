package network.impl;

import network.ClientHandler;
import network.CommandExecutor;

import java.io.*;
import java.net.Socket;

public class TcpClientHandler implements ClientHandler, Runnable {

    private final Socket socket;
    private final CommandExecutor executor;
    private final String clientId;

    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected = true;


    private Runnable onDisconnect;

    public TcpClientHandler(Socket socket, CommandExecutor executor) {
        this.socket   = socket;
        this.executor = executor;
        this.clientId = socket.getRemoteSocketAddress().toString();
    }

    public void setOnDisconnect(Runnable callback) {
        this.onDisconnect = callback;
    }



    @Override
    public synchronized void send(String message) {
        if (connected && out != null) {


            for (String line : message.split("\n")) {
                out.println(line);
            }
        }
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        connected = false;
        try { socket.close(); } catch (IOException e) {  }
    }



    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            System.out.println("[Server] Client connected: " + clientId);

            String rawMessage;
            while (connected && (rawMessage = in.readLine()) != null) {
                String trimmed = rawMessage.trim();
                if (trimmed.isEmpty()) continue;

                String response = executor.process(trimmed);
                send(response);
            }
        } catch (IOException e) {

        } finally {
            connected = false;
            try { socket.close(); } catch (IOException e) {  }
            System.out.println("[Server] Client disconnected: " + clientId);
            if (onDisconnect != null) onDisconnect.run();
        }
    }
}
