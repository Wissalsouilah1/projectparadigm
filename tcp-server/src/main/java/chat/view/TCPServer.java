package chat.view;

import chat.model.ChatServer;
import chat.model.ServerConfig;
import chat.model.ServerListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** View: JavaFX only. All networking lives in ChatServer. Start with: java TCPServer */
public class TCPServer extends Application implements ServerListener {
    private final TextArea logArea = new TextArea();
    private final ListView<String> userList = new ListView<>();
    private final Map<String, Color> userColors = new HashMap<>();
    private final Random random = new Random();
    private ChatServer server;

    @Override
    public void start(Stage stage) {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setId("logArea");

        // Random pastel background colour per user
        userList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setStyle("");
                } else {
                    Color c = userColors.computeIfAbsent(name, n -> Color.hsb(random.nextDouble() * 360, 0.35, 1.0));
                    setText(name);
                    setStyle("-fx-background-color: " + toHex(c) + "; -fx-text-fill: #222;");
                }
            }
        });

        GridPane root = new GridPane();
        root.setPadding(new Insets(12));
        root.setHgap(10);
        root.setVgap(8);

        Label logTitle = new Label("Server Log");
        Label usersTitle = new Label("Connected Users");
        logTitle.getStyleClass().add("title");
        usersTitle.getStyleClass().add("title");

        root.add(logTitle, 0, 0);
        root.add(usersTitle, 1, 0);
        root.add(logArea, 0, 1);
        root.add(userList, 1, 1);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHgrow(Priority.ALWAYS);
        ColumnConstraints c1 = new ColumnConstraints(220);
        root.getColumnConstraints().addAll(c0, c1);
        RowConstraints r0 = new RowConstraints();
        RowConstraints r1 = new RowConstraints();
        r1.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(r0, r1);

        Scene scene = new Scene(root, 760, 460);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setTitle("TCP Chat Server");
        stage.setScene(scene);
        stage.show();

        server = new ChatServer(new ServerConfig().getPort(), this);
        server.start();
    }

    @Override
    public void stop() {
        if (server != null) server.stop();
    }

    // ---- ServerListener: may be called from any thread -> hop to the FX thread ----
    @Override
    public void onLog(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    @Override
    public void onUserListChanged(List<String> usernames) {
        Platform.runLater(() -> userList.getItems().setAll(usernames));
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
