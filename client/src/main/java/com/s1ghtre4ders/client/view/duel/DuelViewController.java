package com.s1ghtre4ders.client.view.duel;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class DuelViewController {
    private StackPane root;
    private VBox duelArena;
    private VBox victoryScreen;
    private VBox defeatScreen;

    private Label playerHpLabel;
    private Label opponentHpLabel;
    private Label turnLabel;
    private Button attackButton;

    private QTEBar qteBar;
    private boolean qteActive = false;

    private DuelEventBus eventBus;
    private String playerName;
    private Runnable backToLobbyCallback;

    public DuelViewController(String playerName, String opponentName, DuelEventBus eventBus, Runnable backToLobbyCallback) {
        System.out.println("🎮 DuelViewController constructor called for: " + playerName);
        this.playerName = playerName;
        this.eventBus = eventBus;
        this.backToLobbyCallback = backToLobbyCallback;

        initializeDuelArena();
        setupEventListeners();
        System.out.println("🎮 DuelViewController initialization complete");
    }

    private void initializeDuelArena() {
        System.out.println("🎮 initializeDuelArena: Creating root StackPane");

        // Create root FIRST
        root = new StackPane();
        root.setStyle("-fx-background-color: #1a1a1a;");
        root.setPrefSize(1200, 700);
        root.setFocusTraversable(true);

        System.out.println("🎮 initializeDuelArena: Creating duelArena VBox");

        // === Duel Arena View ===
        duelArena = new VBox(20);
        duelArena.setStyle("-fx-background-color: #1a1a1a; -fx-padding: 20;");
        duelArena.setAlignment(Pos.CENTER);

        // HP Labels
        HBox hpBox = new HBox(40);
        hpBox.setAlignment(Pos.CENTER);

        playerHpLabel = new Label("Your HP: 100/100");
        playerHpLabel.setStyle("-fx-font-size: 18; -fx-text-fill: #32b8c6;");

        opponentHpLabel = new Label("Opponent HP: 100/100");
        opponentHpLabel.setStyle("-fx-font-size: 18; -fx-text-fill: #ff5459;");

        hpBox.getChildren().addAll(playerHpLabel, opponentHpLabel);
        duelArena.getChildren().add(hpBox);

        // Turn label
        turnLabel = new Label("YOUR TURN");
        turnLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #32b8c6;");
        duelArena.getChildren().add(turnLabel);

        // QTE Bar (integrated)
        qteBar = new QTEBar();
        duelArena.getChildren().add(qteBar.getPane());

        // Attack button
        attackButton = new Button("ATTACK");
        attackButton.setPrefSize(150, 50);
        attackButton.setStyle(
                "-fx-font-size: 18; "
                        + "-fx-padding: 15; "
                        + "-fx-background-color: #32b8c6; "
                        + "-fx-text-fill: white; "
                        + "-fx-font-weight: bold; "
                        + "-fx-border-radius: 8; "
                        + "-fx-cursor: hand;"
        );
        attackButton.setOnAction(e -> {
            System.out.println("⚔️ ATTACK button clicked");
            onAttackPressed();
        });
        duelArena.getChildren().add(attackButton);

        System.out.println("🎮 initializeDuelArena: Creating victory/defeat screens");

        // === Victory Screen ===
        victoryScreen = createEndScreen("🏆 VICTORY!", "#00aa00", "You won the duel!");

        // === Defeat Screen ===
        defeatScreen = createEndScreen("💀 DEFEAT", "#aa0000", "You lost the duel...");

        // Add all to root
        root.getChildren().addAll(duelArena, victoryScreen, defeatScreen);
        victoryScreen.setVisible(false);
        defeatScreen.setVisible(false);

        System.out.println("🎮 initializeDuelArena: Complete - root has " + root.getChildren().size() + " children");
    }

    private VBox createEndScreen(String title, String color, String subtitle) {
        VBox screen = new VBox(30);
        screen.setStyle("-fx-background-color: #1a1a1a; -fx-padding: 40;");
        screen.setAlignment(Pos.CENTER);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 48; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 18; -fx-text-fill: #cccccc;");

        Button backButton = new Button("BACK TO LOBBY");
        backButton.setPrefSize(200, 50);
        backButton.setStyle(
                "-fx-font-size: 16; "
                        + "-fx-padding: 15; "
                        + "-fx-background-color: #32b8c6; "
                        + "-fx-text-fill: white; "
                        + "-fx-font-weight: bold; "
                        + "-fx-border-radius: 8; "
                        + "-fx-cursor: hand;"
        );
        backButton.setOnAction(e -> {
            System.out.println("🏠 Back to Lobby button clicked");
            if (backToLobbyCallback != null) {
                backToLobbyCallback.run();
            }
        });

        screen.getChildren().addAll(titleLabel, subtitleLabel, backButton);
        return screen;
    }

    private void setupEventListeners() {
        System.out.println("🎮 setupEventListeners: Subscribing to DuelEventBus events");

        // Turn change listener
        eventBus.onTurnChange.subscribe(isMyTurn -> Platform.runLater(() -> {
            System.out.println("🎮 TURN_CHANGE event: isMyTurn=" + isMyTurn);
            attackButton.setDisable(!isMyTurn || qteActive);
            turnLabel.setText(isMyTurn ? "YOUR TURN" : "OPPONENT'S TURN");
            turnLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: "
                    + (isMyTurn ? "#32b8c6" : "#ff5459") + ";");
        }));

        // QTE start listener
        eventBus.onQteStart.subscribe(() -> Platform.runLater(() -> {
            System.out.println("🎮 QTE_START event received - starting QTE");
            startQTE();
        }));

        // HP update listener
        eventBus.onHpUpdate.subscribe(data -> Platform.runLater(() -> {
            System.out.println("🎮 HP_UPDATE event: " + data);
            String[] parts = data.split(":");
            if (parts.length >= 3) {
                String playerId = parts[1];
                try {
                    int hp = Integer.parseInt(parts[2]);
                    if (playerId.equals(playerName)) {
                        playerHpLabel.setText("Your HP: " + hp + "/100");
                        System.out.println("  -> Updated YOUR HP to " + hp);
                    } else {
                        opponentHpLabel.setText("Opponent HP: " + hp + "/100");
                        System.out.println("  -> Updated OPPONENT HP to " + hp);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("❌ Invalid HP value: " + parts[2]);
                }
            }
        }));

        // Duel end listener
        eventBus.onDuelEnd.subscribe(result -> Platform.runLater(() -> {
            System.out.println("🎮 DUEL_END event: result=" + result);
            duelArena.setVisible(false);
            if ("WIN".equals(result)) {
                System.out.println("🏆 VICTORY!");
                victoryScreen.setVisible(true);
            } else {
                System.out.println("💀 DEFEAT!");
                defeatScreen.setVisible(true);
            }
        }));

        System.out.println("🎮 setupEventListeners: Complete");
    }

    private void startQTE() {
        System.out.println("⚡ startQTE: Beginning QTE sequence");
        qteActive = true;
        attackButton.setDisable(true);
        root.requestFocus();

        qteBar.start(quality -> {
            System.out.println("⚡ QTE result received: " + quality);
            onQteInput(quality);
        });
    }

    private void onQteInput(String quality) {
        if (!qteActive) return;

        qteActive = false;
        qteBar.stop();

        System.out.println("⚡ onQteInput: Sending QTE_RESULT=" + quality);
        eventBus.sendQTE.accept(quality);

        // Re-enable attack button after delay
        Timeline delay = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            attackButton.setDisable(false);
        }));
        delay.play();
    }

    private void onAttackPressed() {
        attackButton.setDisable(true);
        System.out.println("⚔️ onAttackPressed: Sending ATTACK to server");
        eventBus.sendAttack.accept(null);
    }

    public StackPane getRoot() {
        System.out.println("🎮 getRoot() called - returning: " + root);
        return root;
    }
}
