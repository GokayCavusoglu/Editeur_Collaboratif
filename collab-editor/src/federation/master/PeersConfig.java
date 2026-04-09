package federation.master;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PeersConfig {

    public static class PeerAddress {
        public final String host;
        public final int port;

        public PeerAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() { return host + ":" + port; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PeerAddress)) return false;
            PeerAddress that = (PeerAddress) o;
            return port == that.port && host.equals(that.host);
        }

        @Override
        public int hashCode() { return host.hashCode() * 31 + port; }
    }

    private PeerAddress master;
    private final List<PeerAddress> peers = new ArrayList<>();

    public PeerAddress getMaster()      { return master; }
    public List<PeerAddress> getPeers() { return peers; }


    public List<PeerAddress> getAll() {
        List<PeerAddress> all = new ArrayList<>();
        if (master != null) all.add(master);
        all.addAll(peers);
        return all;
    }


    public boolean isMaster(String host, int port) {
        return master != null && master.host.equals(host) && master.port == port;
    }


    public static PeersConfig load(String path) throws IOException {
        return load(new FileReader(path));
    }


    public static PeersConfig load(Reader reader) throws IOException {
        PeersConfig config = new PeersConfig();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;


                String[] parts = line.split("\\s*=\\s*", 2);
                if (parts.length != 2) continue;

                String role = parts[0].trim().toLowerCase();
                String[] addr = parts[1].trim().split("\\s+");
                if (addr.length != 2) continue;

                String host = addr[0];
                int port = Integer.parseInt(addr[1]);

                if (role.equals("master")) {
                    config.master = new PeerAddress(host, port);
                } else if (role.equals("peer")) {
                    config.peers.add(new PeerAddress(host, port));
                }
            }
        }
        return config;
    }
}
