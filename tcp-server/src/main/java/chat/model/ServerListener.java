package chat.model;

import java.util.List;

/**
 * Callback interface: the model reports events through it, the view implements it.
 * The model never imports anything from JavaFX.
 */
public interface ServerListener {
    void onLog(String message);

    void onUserListChanged(List<String> usernames);
}
