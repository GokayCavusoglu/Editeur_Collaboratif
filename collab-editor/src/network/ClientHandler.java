package network;

public interface ClientHandler {


    void send(String message);


    String getClientId();


    boolean isConnected();


    void close();
}
