package chat;

import chat.view.TCPClient;

/** Entry point for the fat JAR: java -jar tcp-client-1.0-shaded.jar localhost 3000 */
public class Launcher {
    public static void main(String[] args) {
        TCPClient.main(args);
    }
}
