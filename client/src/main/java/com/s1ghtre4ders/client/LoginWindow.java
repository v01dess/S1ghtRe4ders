package com.s1ghtre4ders.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;

/**
 * Login window for S1ghtRe4ders
 * Handles registration and login before opening the main lobby
 * - Creates new accounts with SHA-256 hashed passwords
 * - Validates credentials on login
 * - Establishes connection to server
 * - Passes authenticated connection to LobbyClient
 */
public class LoginWindow {
    private Stage primaryStage;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Label statusLabel;
    private Runnable onLoginSuccess;

    public LoginWindow(Stage primaryStage, Runnable onLoginSuccess) {
        this.primaryStage = primaryStage;
        this.onLoginSuccess = onLoginSuccess;
    }

    /**
     * Display the login/registration window
     */
    public void show() {
        primaryStage.setTitle("🎮 S1ghtRe4ders - Login");
        primaryStage.setWidth(450);
        primaryStage.setHeight(400);
        primaryStage.setResizable(false);

        VBox root = new VBox(15);
        root.setPadding(new Insets(40, 30, 30, 30));
        root.setStyle("-fx-alignment: top_center;");

        // ═══════════════════════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════════════════════
        Label titleLabel = new Label("S1ghtRe4ders");
        titleLabel.setStyle("-fx-font-size: 32; -fx-font-weight: bold; -fx-text-fill: #0066ff;");

        Label subtitleLabel = new Label("Multiplayer Lobby System");
        subtitleLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666; -fx-font-style: italic;");

        VBox headerBox = new VBox(5);
        headerBox.setStyle("-fx-alignment: center;");
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);

        // ═══════════════════════════════════════════════════════
        // INPUT FIELDS
        // ═══════════════════════════════════════════════════════
        Label usernameLabel = new Label("📝 Username:");
        usernameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter username (4+ characters)");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-font-size: 12; -fx-padding: 10;");

        Label passwordLabel = new Label("🔐 Password:");
        passwordLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password (4+ characters)");
        passwordField.setPrefHeight(40);
        passwordField.setStyle("-fx-font-size: 12; -fx-padding: 10;");

        // Allow Enter key to submit login
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) {
                handleLogin(usernameField, passwordField);
            }
        });
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) {
                handleLogin(usernameField, passwordField);
            }
        });

        VBox inputBox = new VBox(12);
        inputBox.getChildren().addAll(
                usernameLabel, usernameField,
                passwordLabel, passwordField
        );

        // ═══════════════════════════════════════════════════════
        // STATUS LABEL
        // ═══════════════════════════════════════════════════════
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #ff6600;");
        statusLabel.setWrapText(true);

        // ═══════════════════════════════════════════════════════
        // BUTTONS
        // ═══════════════════════════════════════════════════════
        HBox buttonBox = new HBox(10);
        buttonBox.setStyle("-fx-alignment: center; -fx-spacing: 10;");

        Button loginBtn = new Button("🔑 Login");
        loginBtn.setPrefWidth(120);
        loginBtn.setPrefHeight(40);
        loginBtn.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        loginBtn.setOnAction(e -> handleLogin(usernameField, passwordField));

        Button registerBtn = new Button("📝 Create Account");
        registerBtn.setPrefWidth(140);
        registerBtn.setPrefHeight(40);
        registerBtn.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        registerBtn.setOnAction(e -> handleRegister(usernameField, passwordField));

        buttonBox.getChildren().addAll(loginBtn, registerBtn);

        // ═══════════════════════════════════════════════════════
        // ASSEMBLE
        // ═══════════════════════════════════════════════════════
        root.getChildren().addAll(
                headerBox,
                new Separator(),
                inputBox,
                statusLabel,
                new Separator(),
                buttonBox
        );

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Handle login button click
     */
    private void handleLogin(TextField usernameField, PasswordField passwordField) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("⚠️ Please enter both username and password", "#ff6600");
            return;
        }

        if (username.length() < 3) {
            setStatus("⚠️ Username must be at least 3 characters", "#ff6600");
            return;
        }

        setStatus("🔄 Logging in...", "#0066ff");

        new Thread(() -> {
            try {
                socket = new Socket("localhost", 5555);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String passwordHash = hashPassword(password);
                if (passwordHash == null) {
                    setStatus("❌ Password hashing failed", "#cc0000");
                    closeConnection();
                    return;
                }

                out.println("LOGIN:" + username + ":" + passwordHash);

                String response = in.readLine();

                if (response != null && response.startsWith("LOGIN_OK:")) {
                    setStatus("✅ Login successful!", "#00aa00");
                    LobbyClient.setConnection(socket, in, out, username);
                    Thread.sleep(800);
                    if (onLoginSuccess != null) {
                        onLoginSuccess.run();
                    }
                } else if (response != null && response.startsWith("LOGIN_FAIL:")) {
                    String reason = response.substring(11);
                    setStatus("❌ " + reason, "#cc0000");
                    closeConnection();
                } else {
                    setStatus("❌ Server error: unexpected response", "#cc0000");
                    closeConnection();
                }
            } catch (IOException e) {
                setStatus("❌ Connection failed: " + e.getMessage(), "#cc0000");
                closeConnection();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Handle register button click
     */
    private void handleRegister(TextField usernameField, PasswordField passwordField) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("⚠️ Please enter both username and password", "#ff6600");
            return;
        }

        if (username.length() < 3) {
            setStatus("⚠️ Username must be at least 3 characters", "#ff6600");
            return;
        }

        if (password.length() < 4) {
            setStatus("⚠️ Password must be at least 4 characters", "#ff6600");
            return;
        }

        setStatus("🔄 Creating account...", "#0066ff");

        new Thread(() -> {
            try {
                socket = new Socket("localhost", 5555);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String passwordHash = hashPassword(password);
                if (passwordHash == null) {
                    setStatus("❌ Password hashing failed", "#cc0000");
                    closeConnection();
                    return;
                }

                out.println("REGISTER:" + username + ":" + passwordHash);

                String response = in.readLine();

                if (response != null && response.startsWith("REGISTER_OK")) {
                    setStatus("✅ Account created! You can now login.", "#00aa00");
                    Platform.runLater(() -> {
                        usernameField.clear();
                        passwordField.clear();
                        usernameField.requestFocus();
                    });
                    closeConnection();
                } else if (response != null && response.startsWith("REGISTER_FAIL:")) {
                    String reason = response.substring(14);
                    setStatus("❌ " + reason, "#cc0000");
                    closeConnection();
                } else {
                    setStatus("❌ Server error: unexpected response", "#cc0000");
                    closeConnection();
                }
            } catch (IOException e) {
                setStatus("❌ Connection failed: " + e.getMessage(), "#cc0000");
                closeConnection();
            }
        }).start();
    }

    /**
     * Hash password using SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            System.err.println("❌ Error hashing password: " + e.getMessage());
            return null;
        }
    }

    /**
     * Update status message with color
     */
    private void setStatus(String message, String color) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + color + ";");
        });
    }

    /**
     * Close socket connection
     */
    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Silently ignore
        }
    }
}
