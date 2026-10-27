package chat.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/** Pure model: TCP connection + receiver thread (writeUTF/readUTF). No JavaFX. */
public class ChatClient {
    private final String host;
    private final int port;
    private final ClientListener listener;
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dout;
    private volatile boolean connected;
    private volatile boolean leaving;
    private boolean readOnly;

    public ChatClient(String host, int port, ClientListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /** Connects and sends the identity. A blank username means READ-ONLY MODE. */
    public void connect(String username) throws IOException {
        readOnly = username == null || username.isBlank();
        socket = new Socket(host, port);
        dis = new DataInputStream(socket.getInputStream());
        dout = new DataOutputStream(socket.getOutputStream());
        dout.writeUTF(readOnly ? "" : username.trim());
        dout.flush();
        connected = true;
        Thread.ofVirtual().name("receiver").start(this::receiveLoop);   // receive independently of the keyboard
    }

    private void receiveLoop() {
        String reason = "Disconnected from server";
        try {
            while (connected) listener.onMessage(dis.readUTF());
        } catch (IOException e) {
            reason = leaving ? "You left the chat" : "The server closed the connection";
        } finally {
            connected = false;
            closeQuietly();
            listener.onDisconnected(reason);
        }
    }

    /** Sends a line. "end" / "bye" make the client leave. Returns false if it was not sent. */
    public synchronized boolean send(String text) {
        if (!connected || text == null || text.isBlank()) return false;
        String t = text.trim();
        if (isLeaveCommand(t)) {
            leave();
            return true;
        }
        if (readOnly && !t.equalsIgnoreCase("allUsers")) return false;
        try {
            dout.writeUTF(t);
            dout.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Notifies the server, then closes the connection. */
    public void leave() {
        if (!connected) return;
        leaving = true;
        try {
            dout.writeUTF("bye");
            dout.flush();
        } catch (IOException ignored) {
        }
        connected = false;
        closeQuietly();
    }

    public static boolean isLeaveCommand(String t) {
        return t.equalsIgnoreCase("end") || t.equalsIgnoreCase("bye");
    }

    public boolean isReadOnly() { return readOnly; }
    public boolean isConnected() { return connected; }

    private void closeQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}
