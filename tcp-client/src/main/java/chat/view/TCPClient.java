package chat.view;

import chat.model.ChatClient;
import chat.model.ClientConfig;
import chat.model.ClientListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;

/** View: JavaFX only. Start with: java TCPClient <ServerIPAddress> <PortNumber> */
public class TCPClient extends Application implements ClientListener {
    private Stage stage;
    private ClientConfig config;
    private ChatClient client;

    private final TextArea chatArea = new TextArea();
    private final TextArea input = new TextArea();
    private final Button sendBtn = new Button("SEND");
    private final Button leaveBtn = new Button("LEAVE");
    private final Circle statusDot = new Circle(7);
    private final Label statusLabel = new Label("Online");

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.config = new ClientConfig(getParameters().getRaw());
        stage.setTitle("TCP Chat Client");
        stage.setOnCloseRequest(e -> {
            if (client != null) client.leave();
        });
        showLogin(null);
        stage.show();
    }

    // ---------------- Login scene ----------------
    private void showLogin(String error) {
        Label title = new Label("Join the chat");
        title.getStyleClass().add("title");
        Label hint = new Label("Server: " + config.getHost() + ":" + config.getPort()
                + "\nLeave the name empty to join in READ-ONLY MODE.");
        TextField nameField = new TextField();
        nameField.setPromptText("Username");
        Button connect = new Button("CONNECT");
        connect.setDefaultButton(true);
        Label err = new Label(error == null ? "" : error);
        err.getStyleClass().add("error");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(24));
        grid.setVgap(12);
        grid.setHgap(8);
        grid.setAlignment(Pos.CENTER);
        grid.add(title, 0, 0);
        grid.add(hint, 0, 1);
        grid.add(nameField, 0, 2);
        grid.add(connect, 0, 3);
        grid.add(err, 0, 4);

        connect.setOnAction(e -> connect(nameField.getText()));
        setScene(grid, 420, 280);
    }

    private void connect(String username) {
        client = new ChatClient(config.getHost(), config.getPort(), this);
        try {
            client.connect(username);
            showChat(client.isReadOnly());
        } catch (IOException ex) {
            showLogin("Cannot connect: " + ex.getMessage());
        }
    }

    // ---------------- Chat scene (GridPane) ----------------
    private void showChat(boolean readOnly) {
        statusDot.getStyleClass().add("dot-online");
        statusLabel.setText(readOnly ? "Online - READ-ONLY MODE" : "Online");
        HBox status = new HBox(8, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_LEFT);

        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        input.setPromptText(readOnly ? "READ-ONLY MODE" : "Type a message (Enter = send, Shift+Enter = new line)");
        input.setPrefRowCount(2);
        input.setWrapText(true);
        input.setDisable(readOnly);
        sendBtn.setDisable(readOnly);

        sendBtn.setOnAction(e -> sendInput());
        leaveBtn.setOnAction(e -> {
            client.leave();
            Platform.exit();
        });
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendInput();
            }
        });

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(12));
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(status, 0, 0, 2, 1);
        grid.add(chatArea, 0, 1, 2, 1);
        grid.add(input, 0, 2);
        HBox buttons = new HBox(8, sendBtn, leaveBtn);
        grid.add(buttons, 1, 2);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, new ColumnConstraints());
        RowConstraints r0 = new RowConstraints();
        RowConstraints r1 = new RowConstraints();
        r1.setVgrow(Priority.ALWAYS);
        grid.getRowConstraints().addAll(r0, r1, new RowConstraints());

        setScene(grid, 620, 480);
    }

    private void sendInput() {
        String text = input.getText().trim();
        input.clear();
        if (text.isEmpty()) return;
        if (client.send(text)) {
            // The server does not echo a message back to its sender, so show it locally
            boolean command = ChatClient.isLeaveCommand(text) || text.equalsIgnoreCase("allUsers");
            if (!command) {
                chatArea.appendText("[" + java.time.LocalTime.now().withNano(0) + "] You: " + text + "\n");
            }
        } else if (client.isConnected()) {
            chatArea.appendText("(READ-ONLY MODE: you cannot send messages)\n");
        }
    }

    private void setScene(javafx.scene.Parent root, double w, double h) {
        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
    }

    // ---- ClientListener: called from the receiver thread -> hop to the FX thread ----
    @Override
    public void onMessage(String line) {
        Platform.runLater(() -> chatArea.appendText(line + "\n"));
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            statusLabel.setText("Offline");
            statusDot.getStyleClass().remove("dot-online");
            statusDot.getStyleClass().add("dot-offline");
            input.setDisable(true);
            sendBtn.setDisable(true);
            chatArea.appendText("*** " + reason + " ***\n");
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
