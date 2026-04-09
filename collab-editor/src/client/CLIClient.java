package client;

import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;

public class CLIClient {

    private final String host;
    private final int port;
    private final boolean pushMode;

    public CLIClient(String host, int port, boolean pushMode) {
        this.host     = host;
        this.port     = port;
        this.pushMode = pushMode;
    }

    public void run() {
        try (
            Socket socket = new Socket(host, port);
            BufferedReader serverIn = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter serverOut = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            BufferedReader userIn = new BufferedReader(
                new InputStreamReader(System.in))
        ) {
            System.out.println("Connected to " + host + ":" + port
                + " [" + (pushMode ? "PUSH" : "PULL") + " mode]");
            System.out.println("Type commands (GETD, GETL i, ADDL i text, MDFL i text, RMVL i)");
            System.out.println("Type 'quit' to exit.\n");


            if (pushMode) {
                startPushReader(serverIn);
            }


            String userLine;
            System.out.print("> ");
            while ((userLine = userIn.readLine()) != null) {
                String trimmed = userLine.trim();
                if (trimmed.equalsIgnoreCase("quit")) break;
                if (trimmed.isEmpty()) { System.out.print("> "); continue; }

                serverOut.println(trimmed);


                if (!pushMode) {
                    readResponse(serverIn, trimmed);
                }

                System.out.print("> ");
            }

            System.out.println("Disconnected.");
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }


    private void readResponse(BufferedReader serverIn, String sentCommand) throws IOException {
        String keyword = sentCommand.length() >= 4 ? sentCommand.substring(0, 4) : "";

        if (keyword.equals(ProtocolConstants.GETD)) {

            String line;
            while ((line = serverIn.readLine()) != null) {
                System.out.println("  " + line);
                if (line.startsWith(ProtocolConstants.DONE)) break;
            }
        } else {

            String response = serverIn.readLine();
            if (response != null) {
                System.out.println("  " + response);
            }
        }
    }


    private void startPushReader(BufferedReader serverIn) {
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = serverIn.readLine()) != null) {
                    if (line.startsWith("PUSH ")) {
                        System.out.println("\n  [PUSH] " + line.substring(5));
                    } else {
                        System.out.println("  " + line);
                    }
                    System.out.print("> ");
                }
            } catch (IOException e) {
                System.out.println("\n[Disconnected from server]");
            }
        });
        reader.setDaemon(true);
        reader.start();
    }



    public static void main(String[] args) {
        String host = "localhost";
        int port    = 12345;
        boolean push = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host": case "-h": host = args[++i]; break;
                case "--port": case "-p": port = Integer.parseInt(args[++i]); break;
                case "--push":            push = true; break;
            }
        }


        if (push && port == 12345) port = 12346;

        new CLIClient(host, port, push).run();
    }
}
