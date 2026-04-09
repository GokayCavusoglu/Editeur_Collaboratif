package network;

import java.util.List;

public interface ClientRegistry {

    void register(ClientHandler client);

    void unregister(ClientHandler client);


    void broadcast(ClientHandler sender, String message);


    void broadcastAll(String message);


    int count();
}
