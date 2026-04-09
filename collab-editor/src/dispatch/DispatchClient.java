package dispatch;

import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;

public class DispatchClient {


    public static String getServerAssignment(String dispatchHost, int dispatchPort)
            throws IOException {
        try (Socket sock = new Socket(dispatchHost, dispatchPort);
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(sock.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)) {

            out.println("CONNECT");
            String response = in.readLine();

            if (response != null && response.startsWith("SERVER ")) {
                String[] parts = response.split("\\s+");
                return parts[1] + ":" + parts[2];
            }
            throw new IOException("Dispatch error: " + response);
        }
    }


    public static void notifyDisconnect(String dispatchHost, int dispatchPort,
                                         String serverHost, int serverPort) {
        try (Socket sock = new Socket(dispatchHost, dispatchPort);
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)) {
            out.println("DISCONNECT " + serverHost + " " + serverPort);
        } catch (IOException e) {

        }
    }
}
