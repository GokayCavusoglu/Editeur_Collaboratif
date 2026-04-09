package network.impl;

import core.DocumentChangeListener;
import core.DocumentManager;
import core.ObservableDocumentManager;
import network.ClientHandler;
import network.ClientRegistry;
import network.CommandExecutor;
import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class PushClientHandler implements ClientHandler, Runnable {

    private final Socket socket;
    private final CommandExecutor executor;
    private final DocumentManager documentManager;
    private final ClientRegistry registry;
    private final String clientId;

    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected = true;

    public PushClientHandler(Socket socket,
                             CommandExecutor executor,
                             DocumentManager documentManager,
                             ClientRegistry registry) {
        this.socket          = socket;
        this.executor        = executor;
        this.documentManager = documentManager;
        this.registry        = registry;
        this.clientId        = socket.getRemoteSocketAddress().toString();
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
    public String getClientId() { return clientId; }

    @Override
    public boolean isConnected() { return connected; }

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

            System.out.println("[PushServer] Client connected: " + clientId);
            registry.register(this);


            sendFullDocument();

            String rawMessage;
            while (connected && (rawMessage = in.readLine()) != null) {
                String trimmed = rawMessage.trim();
                if (trimmed.isEmpty()) continue;


                String response = executor.process(trimmed);


                send(response);


                if (isMutation(trimmed)) {
                    registry.broadcast(this, "PUSH " + trimmed);
                }
            }
        } catch (IOException e) {

        } finally {
            connected = false;
            registry.unregister(this);
            try { socket.close(); } catch (IOException e) {  }
            System.out.println("[PushServer] Client disconnected: " + clientId);
        }
    }



    private void sendFullDocument() {
        List<String> lines = documentManager.getDocument();
        for (int i = 0; i < lines.size(); i++) {
            send(ProtocolConstants.line(i + 1, lines.get(i)));
        }
        send(ProtocolConstants.done());
    }


    private boolean isMutation(String rawCommand) {
        if (rawCommand.length() < 4) return false;
        String kw = rawCommand.substring(0, 4);
        return kw.equals(ProtocolConstants.ADDL)
            || kw.equals(ProtocolConstants.RMVL)
            || kw.equals(ProtocolConstants.MDFL);
    }
}
