package com.s1ghtre4ders.client;

import com.s1ghtre4ders.client.models.Player;
import com.s1ghtre4ders.client.models.PlayerStatus;
import com.s1ghtre4ders.client.view.duel.DuelEventBus;
import com.s1ghtre4ders.client.view.duel.DuelViewController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

/**
 * S1ghtRe4ders Lobby Client
 * Main JavaFX application for the multiplayer lobby
 *
 * Features:
 * - Login/Registration via LoginWindow
 * - Real-time player list with status colors
 * - Lobby chat system
 * - DND (Do Not Disturb) toggle
 * - Spectator mode for watching duels
 * - Duel system with QTE dodge mechanics
 */
public class LobbyClient extends Application {
    // ═══════════════════════════════════════════════════════
    // STATIC CONNECTION FIELDS (shared from LoginWindow)
    // ═══════════════════════════════════════════════════════
    private static String currentUsernameStatic = "";
    private static Socket staticSocket;
    private static BufferedReader staticIn;
    private static PrintWriter staticOut;

    public static void setConnection(Socket socket, BufferedReader in, PrintWriter out, String username) {
        System.out.println("🔌 LobbyClient.setConnection: Setting static connection for " + username);
        staticSocket = socket;
        staticIn = in;
        staticOut = out;
        currentUsernameStatic = username;
    }

    // ═══════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Stage primaryStage;

    // UI Components
    private Label connectionStatus;
    private Label usernameLabel;
    private ListView<Player> playerListView;
    private ListView<String> chatListView;
    private TextField chatInput;
    private Button dndToggleBtn;
    private Button duelBtn;
    private Button spectateBtn;
    private Button exitSpectateBtn;

    // Data
    private ObservableList<Player> players = FXCollections.observableArrayList();
    private ObservableList<String> chatMessages = FXCollections.observableArrayList();

    // User state
    private String currentUsername;
    private boolean isDND = false;
    private boolean isSpectating = false;
    private boolean isInDuel = false;

    // Duel state
    private String currentDuelId = null;
    private int duelRole = 0;
    private DuelViewController duelViewController = null;
    private DuelEventBus duelEventBus = null;

    // Views (persistent)
    private VBox lobbyView = null;

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("🚀 LobbyClient.start: Initializing application");
        this.primaryStage = primaryStage;

        primaryStage.setOnCloseRequest(e -> {
            System.out.println("🚪 PRIMARY STAGE CLOSE REQUEST - cleaning up");
            closeConnection();
        });

        LoginWindow loginWindow = new LoginWindow(primaryStage, this::initializeLobby);
        loginWindow.show();
    }

    /**
     * Initialize lobby after successful login (called by LoginWindow callback)
     */
    private void initializeLobby() {
        System.out.println("🎮 initializeLobby: Called by LoginWindow");
        Platform.runLater(() -> {
            try {
                // Copy static connection fields set by LoginWindow
                this.socket = staticSocket;
                this.in = staticIn;
                this.out = staticOut;
                this.currentUsername = currentUsernameStatic;

                System.out.println("🎮 initializeLobby: Copied connection for user: " + currentUsername);

                // Build UI
                lobbyView = buildLobbyUI();

                // Set scene
                Scene scene = new Scene(lobbyView, 1200, 700);
                primaryStage.setTitle("🎮 S1ghtRe4ders Lobby");
                primaryStage.setScene(scene);
                primaryStage.setMinWidth(1000);
                primaryStage.setMinHeight(600);
                primaryStage.setWidth(1200);
                primaryStage.setHeight(700);
                primaryStage.centerOnScreen();
                primaryStage.show();

                System.out.println("🎮 initializeLobby: Scene set on primaryStage");

                // Start listening to server
                connect();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("❌ initializeLobby error: " + e.getMessage());
                showAlert("❌ Failed to initialize lobby: " + e.getMessage());
            }
        });
    }

    /**
     * Build the lobby UI layout
     */
    private VBox buildLobbyUI() {
        System.out.println("🎨 buildLobbyUI: Starting UI construction");

        // ═══════════════════════════════════════════════════════
        // TOP: Connection Status Bar
        // ═══════════════════════════════════════════════════════
        HBox topPanel = new HBox(20);
        topPanel.setPadding(new Insets(15));
        topPanel.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");

        Label connLabel = new Label("🔌 Connection:");
        connLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");

        connectionStatus = new Label("🟢 Connected");
        connectionStatus.setStyle("-fx-font-size: 12; -fx-text-fill: #00aa00;");

        Label playerLabel = new Label("👤 Player:");
        playerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");

        usernameLabel = new Label(currentUsername);
        usernameLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        topPanel.getChildren().addAll(connLabel, connectionStatus, new Separator(), playerLabel, usernameLabel);
        HBox.setHgrow(topPanel.getChildren().get(2), Priority.ALWAYS);

        // ═══════════════════════════════════════════════════════
        // LEFT: Player List + Buttons
        // ═══════════════════════════════════════════════════════
        VBox leftPanel = new VBox(12);
        leftPanel.setPadding(new Insets(15));
        leftPanel.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 1 0 0;");
        leftPanel.setPrefWidth(400);

        Label playersTitle = new Label("👥 Players");
        playersTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        playerListView = new ListView<>(players);
        playerListView.setPrefHeight(300);
        playerListView.setCellFactory(param -> new PlayerListCell());

        duelBtn = new Button("⚔️ Challenge");
        duelBtn.setPrefWidth(Double.MAX_VALUE);
        duelBtn.setStyle("-fx-padding: 10; -fx-font-weight: bold;");
        duelBtn.setOnAction(e -> sendDuelRequest());

        dndToggleBtn = new Button("🔕 DND: OFF");
        dndToggleBtn.setPrefWidth(Double.MAX_VALUE);
        dndToggleBtn.setStyle("-fx-padding: 10; -fx-font-weight: bold;");
        dndToggleBtn.setOnAction(e -> toggleDND());

        spectateBtn = new Button("👁️ Spectate");
        spectateBtn.setPrefWidth(Double.MAX_VALUE);
        spectateBtn.setStyle("-fx-padding: 10; -fx-font-weight: bold;");
        spectateBtn.setOnAction(e -> enterSpectate());

        exitSpectateBtn = new Button("❌ Exit Spectate");
        exitSpectateBtn.setPrefWidth(Double.MAX_VALUE);
        exitSpectateBtn.setStyle("-fx-padding: 10; -fx-font-weight: bold; -fx-text-fill: #cc0000;");
        exitSpectateBtn.setVisible(false);
        exitSpectateBtn.setOnAction(e -> exitSpectate());

        VBox buttonBox = new VBox(8);
        buttonBox.getChildren().addAll(duelBtn, dndToggleBtn, spectateBtn, exitSpectateBtn);

        leftPanel.getChildren().addAll(playersTitle, playerListView, new Separator(), buttonBox);
        VBox.setVgrow(playerListView, Priority.ALWAYS);

        // ═══════════════════════════════════════════════════════
        // RIGHT: Chat
        // ═══════════════════════════════════════════════════════
        VBox rightPanel = new VBox(10);
        rightPanel.setPadding(new Insets(15));

        Label chatTitle = new Label("💬 Lobby Chat");
        chatTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        chatListView = new ListView<>(chatMessages);
        chatListView.setPrefHeight(400);
        chatListView.setStyle("-fx-font-size: 11;");
        chatListView.setCellFactory(param -> new ChatCell());

        HBox chatBox = new HBox(8);
        chatInput = new TextField();
        chatInput.setPromptText("Type a message... (press Enter to send)");
        chatInput.setOnAction(e -> sendChat());

        Button sendBtn = new Button("Send");
        sendBtn.setPrefWidth(70);
        sendBtn.setStyle("-fx-padding: 8;");
        sendBtn.setOnAction(e -> sendChat());

        chatBox.getChildren().addAll(chatInput, sendBtn);
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        rightPanel.getChildren().addAll(chatTitle, chatListView, chatBox);
        VBox.setVgrow(chatListView, Priority.ALWAYS);

        // ═══════════════════════════════════════════════════════
        // MAIN LAYOUT
        // ═══════════════════════════════════════════════════════
        HBox mainContent = new HBox(0);
        mainContent.getChildren().addAll(leftPanel, rightPanel);
        HBox.setHgrow(leftPanel, Priority.SOMETIMES);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        VBox root = new VBox(0);
        root.getChildren().addAll(topPanel, mainContent);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        System.out.println("🎨 buildLobbyUI: Complete");
        return root;
    }

    /**
     * Connect to server and start listening for messages
     */
    private void connect() {
        System.out.println("🔌 connect: Establishing listener thread");
        if (socket == null || !socket.isConnected()) {
            System.err.println("❌ Socket not connected!");
            showAlert("❌ Not connected to server!");
            return;
        }

        Platform.runLater(() -> {
            connectionStatus.setText("🟢 Connected");
            connectionStatus.setStyle("-fx-font-size: 12; -fx-text-fill: #00aa00;");
            addChatMessage("✅ Connected to lobby!");
        });

        new Thread(this::listenForMessages).start();
        System.out.println("🔌 connect: Listener thread started");
    }

    /**
     * Background thread: listen for server messages
     */
    private void listenForMessages() {
        System.out.println("📡 listenForMessages: Starting message loop");
        try {
            String message;
            while ((message = in.readLine()) != null) {
                handleServerMessage(message);
            }
            System.out.println("📡 listenForMessages: Server closed connection (null read)");
        } catch (IOException e) {
            System.err.println("📡 listenForMessages: IOException: " + e.getMessage());
            Platform.runLater(() -> {
                connectionStatus.setText("🔴 Disconnected");
                connectionStatus.setStyle("-fx-font-size: 12; -fx-text-fill: #cc0000;");
                addChatMessage("❌ Connection lost!");
            });
        }
    }

    /**
     * Handle all incoming server messages
     */
    private void handleServerMessage(String message) {
        System.out.println("📨 SERVER MSG: " + message);
        Platform.runLater(() -> {
            if (message.startsWith("PLAYER_LIST:")) {
                updatePlayerList(message);
            } else if (message.startsWith("CHAT:")) {
                handleChatMessage(message);
            } else if (message.startsWith("DUEL_REQUESTED:")) {
                handleDuelRequested(message.substring(15));
            } else if (message.startsWith("DUEL_DECLINED:")) {
                addChatMessage("❌ " + message.substring(14) + " declined your duel request");
            } else if (message.startsWith("DUEL_START:")) {
                System.out.println("⚔️ DUEL_START received - parsing data");
                handleDuelStart(message.substring(11));
            } else if (message.startsWith("HP_UPDATE:")) {
                if (duelEventBus != null) {
                    System.out.println("⚔️ HP_UPDATE forwarding to eventBus");
                    duelEventBus.onHpUpdate.emit(l -> l.accept(message));
                } else {
                    System.out.println("⚠️ HP_UPDATE but duelEventBus is null");
                }
            } else if (message.startsWith("QTE_START")) {
                if (duelEventBus != null) {
                    System.out.println("⚔️ QTE_START forwarding to eventBus");
                    duelEventBus.onQteStart.emit(Runnable::run);
                } else {
                    System.out.println("⚠️ QTE_START but duelEventBus is null");
                }
            } else if (message.startsWith("TURN_CHANGE:")) {
                boolean isMyTurn = message.substring(12).equals("true");
                if (duelEventBus != null) {
                    System.out.println("⚔️ TURN_CHANGE(" + isMyTurn + ") forwarding to eventBus");
                    duelEventBus.onTurnChange.emit(l -> l.accept(isMyTurn));
                } else {
                    System.out.println("⚠️ TURN_CHANGE but duelEventBus is null");
                }
            } else if (message.startsWith("DUEL_END:")) {
                String result = message.substring(9).trim();
                if (duelEventBus != null) {
                    System.out.println("⚔️ DUEL_END(" + result + ") forwarding to eventBus");
                    duelEventBus.onDuelEnd.emit(l -> l.accept(result));
                } else {
                    System.out.println("⚠️ DUEL_END but duelEventBus is null");
                }
            } else if (message.startsWith("ERROR:")) {
                addChatMessage("⚠️ " + message.substring(6));
            } else {
                System.out.println("⚠️ Unknown message type: " + message);
            }
        });
    }

    /**
     * Handle chat message from server
     */
    private void handleChatMessage(String message) {
        String[] parts = message.substring(5).split(":", 2);
        if (parts.length == 2) {
            addChatMessage(parts[0] + ": " + parts[1]);
        }
    }

    /**
     * Handle duel request dialog
     */
    private void handleDuelRequested(String requester) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("⚔️ Duel Request");
        dialog.setHeaderText(requester + " wants to duel you!");
        dialog.setContentText("Do you accept?");

        ButtonType acceptType = new ButtonType("Accept", ButtonBar.ButtonData.YES);
        ButtonType declineType = new ButtonType("Decline", ButtonBar.ButtonData.NO);
        dialog.getDialogPane().getButtonTypes().addAll(acceptType, declineType);

        dialog.setResultConverter(dialogButton -> dialogButton == acceptType);

        dialog.showAndWait().ifPresent(accepted -> {
            if (accepted) {
                System.out.println("✅ Duel accepted from " + requester);
                out.println("DUEL_ACCEPT");
                addChatMessage("✅ You accepted the duel from " + requester);
            } else {
                System.out.println("❌ Duel declined from " + requester);
                out.println("DUEL_DECLINE");
                addChatMessage("❌ You declined the duel from " + requester);
            }
        });
    }

    /**
     * Handle duel start - switch to duel view
     */
    private void handleDuelStart(String data) {
        System.out.println("⚔️ handleDuelStart: Received data: " + data);

        String[] parts = data.split(":");
        if (parts.length != 2) {
            System.err.println("❌ handleDuelStart: Invalid data format (expected 3 parts, got " + parts.length + ")");
            return;
        }

        currentDuelId = parts[0];
        duelRole = Integer.parseInt(parts[1]);
        isInDuel = true;

        System.out.println("⚔️ handleDuelStart: duelId=" + currentDuelId + ", role=" + duelRole);

        duelEventBus = new DuelEventBus();
        System.out.println("⚔️ handleDuelStart: DuelEventBus created");

        duelViewController = new DuelViewController(
                currentUsername,
                "Opponent",
                duelEventBus,
                this::returnToLobby
        );
        System.out.println("⚔️ handleDuelStart: DuelViewController created");

        duelEventBus.sendAttack = ignored -> {
            System.out.println("🌐 Sending ATTACK to server");
            out.println("ATTACK");
        };
        duelEventBus.sendQTE = isInput -> {
            System.out.println("🌐 Sending QTE_RESULT:" + isInput + " to server");
            out.println("QTE_RESULT:" + isInput);
        };

        // Swap scene
        System.out.println("⚔️ handleDuelStart: Getting current scene");
        Scene currentScene = primaryStage.getScene();
        if (currentScene == null) {
            System.out.println("⚔️ handleDuelStart: Scene is null - creating new scene");
            currentScene = new Scene(duelViewController.getRoot(), 1200, 700);
            primaryStage.setScene(currentScene);
        } else {
            System.out.println("⚔️ handleDuelStart: Scene exists - setting new root");
            currentScene.setRoot(duelViewController.getRoot());
        }

        System.out.println("⚔️ handleDuelStart: Requesting focus on duel root");
        duelViewController.getRoot().requestFocus();

        System.out.println("⚔️ handleDuelStart: COMPLETE - duel GUI should be visible. Root=" + primaryStage.getScene().getRoot());
        addChatMessage("⚔️ Duel started! You are Player " + duelRole);
    }

    /**
     * Return to lobby from duel (callback)
     */
    private void returnToLobby() {
        System.out.println("🏠 returnToLobby: Called");
        isInDuel = false;
        duelEventBus = null;
        duelViewController = null;
        if (lobbyView != null) {
            System.out.println("🏠 returnToLobby: Setting root back to lobbyView");
            primaryStage.getScene().setRoot(lobbyView);
            System.out.println("🏠 returnToLobby: Complete");
        }
    }

    /**
     * Send duel request to selected player
     */
    private void sendDuelRequest() {
        Player selected = playerListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("⚠️ Please select a player to challenge!");
            return;
        }

        if (selected.getUsername().equals(currentUsername)) {
            showAlert("⚠️ You can't challenge yourself!");
            return;
        }

        if (selected.getStatus() == PlayerStatus.LOBBY_DND) {
            showAlert("⚠️ That player is in Do Not Disturb mode!");
            return;
        }

        if (selected.getStatus() == PlayerStatus.IN_DUEL) {
            showAlert("⚠️ That player is already in a duel!");
            return;
        }

        System.out.println("⚔️ sendDuelRequest: Requesting duel with " + selected.getUsername());
        out.println("DUEL_REQUEST:" + selected.getUsername());
        addChatMessage("⚔️ You sent a duel request to " + selected.getUsername());
    }

    /**
     * Toggle DND on/off
     */
    private void toggleDND() {
        isDND = !isDND;
        dndToggleBtn.setText(isDND ? "🔕 DND: ON" : "🔕 DND: OFF");
        dndToggleBtn.setStyle(isDND ?
                "-fx-padding: 10; -fx-font-weight: bold; -fx-text-fill: #cc0000;" :
                "-fx-padding: 10; -fx-font-weight: bold;"
        );

        out.println("SET_DND:" + (isDND ? "ON" : "OFF"));
    }

    /**
     * Enter spectator mode
     */
    private void enterSpectate() {
        Player selected = playerListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("⚠️ Please select a player to spectate!");
            return;
        }

        if (selected.getUsername().equals(currentUsername)) {
            showAlert("⚠️ You can't spectate yourself!");
            return;
        }

        isSpectating = true;
        out.println("ENTER_SPECTATE:" + selected.getUsername());
        spectateBtn.setVisible(false);
        exitSpectateBtn.setVisible(true);
        addChatMessage("👁️ You are now spectating " + selected.getUsername());
    }

    /**
     * Exit spectator mode
     */
    private void exitSpectate() {
        isSpectating = false;
        out.println("EXIT_SPECTATE");
        spectateBtn.setVisible(true);
        exitSpectateBtn.setVisible(false);
        addChatMessage("👁️ You exited spectator mode");
    }

    /**
     * Send chat message
     */
    private void sendChat() {
        String msg = chatInput.getText().trim();
        if (!msg.isEmpty() && out != null) {
            out.println("CHAT:" + msg);
            chatInput.clear();
        }
    }

    /**
     * Update player list from server
     */
    private void updatePlayerList(String message) {
        players.clear();
        String data = message.substring(12);

        if (!data.isEmpty()) {
            for (String entry : data.split(";")) {
                if (!entry.isEmpty()) {
                    String[] parts = entry.split(",");
                    if (parts.length == 2) {
                        try {
                            players.add(new Player(parts[0], PlayerStatus.valueOf(parts[1])));
                        } catch (IllegalArgumentException e) {
                            System.err.println("⚠️ Unknown status: " + parts[1]);
                        }
                    }
                }
            }
        }
    }

    /**
     * Add message to chat
     */
    private void addChatMessage(String message) {
        chatMessages.add(message);
        chatListView.scrollTo(chatMessages.size() - 1);
    }

    /**
     * Show alert dialog
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("⚠️ Alert");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Close connection on exit
     */
    private void closeConnection() {
        System.out.println("🔌 closeConnection: Closing socket");
        try {
            if (socket != null && socket.isConnected()) {
                socket.close();
                System.out.println("🔌 closeConnection: Socket closed");
            }
        } catch (IOException e) {
            System.err.println("🔌 closeConnection: Error - " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // CUSTOM LIST CELLS
    // ═══════════════════════════════════════════════════════

    private static class PlayerListCell extends ListCell<Player> {
        @Override
        protected void updateItem(Player item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
            } else {
                setText(item.getUsername() + " [" + item.getStatus().displayName + "]");
                setStyle("-fx-text-fill: " + item.getStatus().hexColor + "; -fx-font-weight: bold;");
            }
        }
    }

    private static class ChatCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item);
                setWrapText(true);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // MAIN ENTRY
    // ═══════════════════════════════════════════════════════

    public static void main(String[] args) {
        launch(args);
    }
}
