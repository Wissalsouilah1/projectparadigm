package chat.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Host/port come from the command line if given, otherwise from config.properties. */
public final class ClientConfig {
    private final String host;
    private final int port;

    public ClientConfig(List<String> args) {
        Properties props = new Properties();
        Path external = Path.of("config.properties");
        try {
            if (Files.exists(external)) {
                try (InputStream in = Files.newInputStream(external)) {
                    props.load(in);
                }
            } else {
                try (InputStream in = ClientConfig.class.getResourceAsStream("/config.properties")) {
                    if (in != null) props.load(in);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read config: " + e.getMessage());
        }
        String h = props.getProperty("server.host", "localhost").trim();
        int p = Integer.parseInt(props.getProperty("server.port", "3000").trim());

        // java TCPClient <ServerIPAddress> <PortNumber>
        if (args.size() >= 1) h = args.get(0);
        if (args.size() >= 2) p = Integer.parseInt(args.get(1));
        this.host = h;
        this.port = p;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
}
