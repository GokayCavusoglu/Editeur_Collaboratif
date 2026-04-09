package test;

import protocol.ProtocolConstants;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoClient implements Runnable {

    private final String host;
    private final int port;
    private final String id;
    private final int numOps;
    private final boolean pushMode;
    private final Random rng = new Random();


    private volatile List<String> finalDocument = new ArrayList<>();

    public AutoClient(String host, int port, String id, int numOps, boolean pushMode) {
        this.host     = host;
        this.port     = port;
        this.id       = id;
        this.numOps   = numOps;
        this.pushMode = pushMode;
    }

    public List<String> getFinalDocument() {
        return finalDocument;
    }

    @Override
    public void run() {
        try (
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
        ) {
            System.out.println("[" + id + "] Connected");


            if (pushMode) {
                consumeUntilDone(in);
            }

            for (int op = 0; op < numOps; op++) {

                out.println("GETD");
                List<String> doc = readDocument(in);
                int size = doc.size();


                int action = (size == 0) ? 0 : rng.nextInt(3);

                switch (action) {
                    case 0: {
                        int pos = rng.nextInt(size + 1) + 1;
                        String text = "[" + id + "] added#" + op;
                        out.println("ADDL " + pos + " " + text);
                        readSingleResponse(in);
                        break;
                    }
                    case 1: {
                        int pos = rng.nextInt(size) + 1;
                        String text = "[" + id + "] modified#" + op;
                        out.println("MDFL " + pos + " " + text);
                        readSingleResponse(in);
                        break;
                    }
                    case 2: {
                        int pos = rng.nextInt(size) + 1;
                        out.println("RMVL " + pos);
                        readSingleResponse(in);
                        break;
                    }
                }


                Thread.sleep(50 + rng.nextInt(100));
            }


            Thread.sleep(300);
            out.println("GETD");
            finalDocument = readDocument(in);

            System.out.println("[" + id + "] Done — " + finalDocument.size() + " lines");

        } catch (Exception e) {
            System.err.println("[" + id + "] Error: " + e.getMessage());
        }
    }



    private List<String> readDocument(BufferedReader in) throws IOException {
        List<String> lines = new ArrayList<>();
        String response;
        while ((response = in.readLine()) != null) {

            if (response.startsWith("PUSH ")) continue;

            if (response.startsWith(ProtocolConstants.DONE)) break;
            if (response.startsWith(ProtocolConstants.LINE)) {

                String rest = response.substring(5).trim();
                int space = rest.indexOf(' ');
                lines.add(space >= 0 ? rest.substring(space + 1) : "");
            }
        }
        return lines;
    }

    private String readSingleResponse(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {

            if (line.startsWith("PUSH ")) continue;
            return line;
        }
        return null;
    }

    private void consumeUntilDone(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith(ProtocolConstants.DONE)) break;
        }
    }
}
