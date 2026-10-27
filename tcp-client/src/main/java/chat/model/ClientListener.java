package chat.model;

/** Events the model reports to whoever owns it (the JavaFX view). No JavaFX types here. */
public interface ClientListener {
    void onMessage(String line);

    void onDisconnected(String reason);
}
