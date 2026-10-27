package chat.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MODEL (no JavaFX). Same logic as the console TcpServer:
 *  - one virtual thread per client
 *  - two shared maps keyed by socket (output stream, username)
 *  - first message from a client = its username (blank = READ-ONLY MODE)
 *  - messages are formatted "[HH:mm:ss] user: text" and sent to the other clients
 * Events are reported to the view through {@link ServerListener}.
 */
public class ChatServer {

    private final int port;
    private final ServerListener listener;

    // Socket -> output stream (the "active connections")
    private final Map<Socket, DataOutputStream> clients = new ConcurrentHashMap<>();
    // Socket -> username (blank = read-only)
    private final Map<Socket, String> usernames = new ConcurrentHashMap<>();
    // Makes "check duplicate name + register" atomic
    private final Object registerLock = new Object();

    private volatile boolean running;
    private ServerSocket serverSocket;

    public ChatServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
    }

    // ------------------------------------------------------------------
    // Connection management
    // ------------------------------------------------------------------
    public void start() {
        running = true;
        Thread.ofVirtual().name("accept-loop").start(this::acceptLoop);
    }

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket(port)) {
            serverSocket = ss;
            listener.onLog("Server Started on port " + port);
            listener.onLog("TCP Server using Virtual Threads: Waiting for clients...");

            while (running) {
                listener.onLog("Waiting for Client...");
                Socket socketForClient = ss.accept();                       // blocking
                listener.onLog("New connection accepted: " + socketForClient.getRemoteSocketAddress());

                // One virtual thread per client
                Thread.ofVirtual().start(() -> handleClient(socketForClient));
            }
        } catch (IOException ex) {
            if (running) listener.onLog("Server error: " + ex.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        for (Socket s : clients.keySet()) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        listener.onLog("Server stopped");
    }

    // ------------------------------------------------------------------
    // Everything that happens for ONE client (runs in its own virtual thread)
    // ------------------------------------------------------------------
    private void handleClient(Socket socketForClient) {

        try (Socket clientSocket = socketForClient;
             DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream())) {

            // The first message from the client is its username
            String requested = dis.readUTF().trim();
            boolean readOnly = requested.isBlank();
            String username = register(clientSocket, dout, requested);

            if (readOnly) {
                sendToClient(clientSocket, "READ-ONLY MODE: You cannot send messages.");
                listener.onLog(displayName(clientSocket) + " connected");
            } else {
                sendToClient(clientSocket, "Welcome " + username);
                listener.onLog("Welcome " + username);
                broadcast("[Server] " + username + " joined the chat.", clientSocket);
            }

            while (true) {
                String msg = dis.readUTF();                                // wait for this client

                if (msg.equalsIgnoreCase("end") || msg.equalsIgnoreCase("bye")) {
                    break;                                                 // disconnection
                }
                if (msg.equalsIgnoreCase("allUsers")) {                    // reply to this client only
                    sendToClient(clientSocket, getAllUsers());
                    continue;
                }
                if (readOnly) {                                            // read-only cannot post
                    sendToClient(clientSocket, "READ-ONLY MODE: Message not sent.");
                    continue;
                }
                if (msg.isBlank()) continue;                               // ignore empty lines

                String formatted = "[" + LocalTime.now().withNano(0) + "] " + username + ": " + msg;
                listener.onLog(formatted);
                broadcast(formatted, clientSocket);                        // everyone except the sender
            }

        } catch (IOException ex) {
            listener.onLog("Client disconnected: " + socketForClient.getRemoteSocketAddress());
        } finally {
            // Normal and abnormal disconnections end here
            String name = usernames.remove(socketForClient);
            boolean wasRegistered = clients.remove(socketForClient) != null;
            try {
                socketForClient.close();
            } catch (IOException ignored) {
            }
            if (wasRegistered) {
                publishUsers();
                if (name != null && !name.isBlank()) {
                    listener.onLog(name + " left the chat");
                    if (running) broadcast("[Server] " + name + " left the chat.", null);
                } else {
                    listener.onLog("Read-only guest left");
                }
            }
        }
    }

    /** Registers the client; a duplicate username gets a numeric suffix (ahmed -> ahmed_2). */
    private String register(Socket socket, DataOutputStream dout, String requested) {
        String name = requested;
        synchronized (registerLock) {
            if (!name.isBlank()) {
                int n = 2;
                while (usernames.containsValue(name)) {
                    name = requested + "_" + n++;
                }
            }
            clients.put(socket, dout);
            usernames.put(socket, name);
        }
        publishUsers();
        return name;
    }

    // ------------------------------------------------------------------
    // Message distribution
    // ------------------------------------------------------------------

    /** Sends a message to every client except the sender (sender == null -> everybody). */
    private void broadcast(String message, Socket sender) {
        for (Map.Entry<Socket, DataOutputStream> entry : clients.entrySet()) {
            Socket recipient = entry.getKey();
            if (recipient == sender) continue;
            try {
                sendToClient(recipient, message);
            } catch (IOException ex) {
                // One dead recipient must not affect the sender or the others
                listener.onLog("Could not send to " + recipient.getRemoteSocketAddress());
                try {
                    recipient.close();                 // its own thread will clean up
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** The single place where anything is written to a client. */
    private void sendToClient(Socket socket, String message) throws IOException {
        DataOutputStream out = clients.get(socket);
        if (out == null) throw new IOException("Client is no longer registered.");

        synchronized (out) {                           // two threads never write to one stream at once
            if (socket.isClosed()) throw new IOException("Client socket is closed.");
            out.writeUTF(message);
            out.flush();
        }
    }

    private String getAllUsers() {
        StringBuilder result = new StringBuilder("Active users: ");
        boolean found = false;
        for (Map.Entry<Socket, String> entry : usernames.entrySet()) {
            String name = entry.getValue();
            if (!name.isBlank() && !entry.getKey().isClosed()) {
                if (found) result.append(", ");
                result.append(name);
                found = true;
            }
        }
        if (!found) result.append("none");
        return result.toString();
    }

    // ------------------------------------------------------------------
    // Helpers for the view
    // ------------------------------------------------------------------
    private String displayName(Socket socket) {
        String name = usernames.get(socket);
        if (name == null) return "?";
        return name.isBlank() ? "Guest-" + socket.getPort() + " (read-only)" : name;
    }

    private void publishUsers() {
        List<String> names = new ArrayList<>();
        for (Socket s : usernames.keySet()) names.add(displayName(s));
        names.sort(String.CASE_INSENSITIVE_ORDER);     // stable order in the server's ListView
        listener.onUserListChanged(names);
    }
}
