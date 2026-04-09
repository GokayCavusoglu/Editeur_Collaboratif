package network.impl;

import network.ClientHandler;
import network.ClientRegistry;

import java.util.concurrent.CopyOnWriteArrayList;

public class ConcurrentClientRegistry implements ClientRegistry {

    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    @Override
    public void register(ClientHandler client) {
        clients.add(client);
        System.out.println("[Registry] Registered " + client.getClientId()
            + " (total: " + clients.size() + ")");
    }

    @Override
    public void unregister(ClientHandler client) {
        clients.remove(client);
        System.out.println("[Registry] Unregistered " + client.getClientId()
            + " (total: " + clients.size() + ")");
    }

    @Override
    public void broadcast(ClientHandler sender, String message) {
        for (ClientHandler client : clients) {
            if (client != sender && client.isConnected()) {
                try {
                    client.send(message);
                } catch (Exception e) {
                    System.err.println("[Registry] Broadcast error to "
                        + client.getClientId() + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void broadcastAll(String message) {
        for (ClientHandler client : clients) {
            if (client.isConnected()) {
                try {
                    client.send(message);
                } catch (Exception e) {
                    System.err.println("[Registry] Broadcast error to "
                        + client.getClientId() + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public int count() {
        return clients.size();
    }
}
