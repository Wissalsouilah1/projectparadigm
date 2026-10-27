package chat.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads settings at runtime: ./config.properties first, then the bundled default. */
public final class ServerConfig {
    private final Properties props = new Properties();

    public ServerConfig() {
        Path external = Path.of("config.properties");
        try {
            if (Files.exists(external)) {
                try (InputStream in = Files.newInputStream(external)) {
                    props.load(in);
                }
            } else {
                try (InputStream in = ServerConfig.class.getResourceAsStream("/config.properties")) {
                    if (in != null) props.load(in);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read config: " + e.getMessage());
        }
    }

    public int getPort() {
        return Integer.parseInt(props.getProperty("server.port", "3000").trim());
    }
}
