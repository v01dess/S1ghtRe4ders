package com.s1ghtre4ders.server;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S1ghtRe4ders Account Store
 *
 * Manages player authentication with persistent JSON file storage
 * - SHA-256 password hashing
 * - Thread-safe concurrent map
 * - Automatic load/save to accounts.json
 * - Simple JSON parsing (no external dependencies)
 */
public class AccountStore {
    private static final String ACCOUNTS_FILE = "accounts.json";
    private final Map<String, String> accounts; // username -> passwordHash

    public AccountStore() {
        this.accounts = new ConcurrentHashMap<>();
        loadAccounts();
    }

    /**
     * Load accounts from JSON file on startup
     */
    private void loadAccounts() {
        try {
            File file = new File(ACCOUNTS_FILE);
            if (file.exists()) {
                String content = new String(Files.readAllBytes(file.toPath()));
                parseJSON(content);
                System.out.println("📖 Loaded " + accounts.size() + " accounts from " + ACCOUNTS_FILE);
            } else {
                System.out.println("📁 No accounts file found; creating new one on first registration");
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading accounts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parse JSON accounts file
     * Format: {"accounts":[{"username":"Alice","hash":"abc..."},{"username":"Bob","hash":"def..."}]}
     */
    private void parseJSON(String json) {
        try {
            // Normalize whitespace
            json = json.trim();

            // Find the accounts array
            int start = json.indexOf("[");
            int end = json.lastIndexOf("]");

            if (start == -1 || end == -1 || start >= end) {
                System.out.println("⚠️ Invalid JSON format");
                return;
            }

            String arrayContent = json.substring(start + 1, end);
            if (arrayContent.trim().isEmpty()) {
                return; // Empty accounts array
            }

            // Split by account objects: },{
            String[] entries = arrayContent.split("\\}\\s*,\\s*\\{");

            for (String entry : entries) {
                // Clean up braces and whitespace
                entry = entry.replaceAll("[\\{\\}]", "").trim();
                if (entry.isEmpty()) {
                    continue;
                }

                String username = null;
                String hash = null;

                // Parse key-value pairs
                String[] pairs = entry.split(",");
                for (String pair : pairs) {
                    pair = pair.trim();
                    if (pair.contains("username")) {
                        username = extractValue(pair);
                    } else if (pair.contains("hash")) {
                        hash = extractValue(pair);
                    }
                }

                // Add to map if valid
                if (username != null && !username.isEmpty() && hash != null && !hash.isEmpty()) {
                    accounts.put(username, hash);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error parsing JSON: " + e.getMessage());
        }
    }

    /**
     * Extract value from JSON key:value pair
     * Example: "username":"Alice" -> Alice
     */
    private String extractValue(String pair) {
        int colonIndex = pair.indexOf(":");
        if (colonIndex == -1) return null;

        String value = pair.substring(colonIndex + 1).trim();
        // Remove quotes
        return value.replaceAll("^\"|\"$", "");
    }

    /**
     * Register a new account
     * @return true if registration successful, false if username exists or invalid
     */
    public boolean register(String username, String passwordHash) {
        if (username == null || username.trim().isEmpty()) {
            System.out.println("⚠️ Registration failed: empty username");
            return false;
        }

        if (passwordHash == null || passwordHash.isEmpty()) {
            System.out.println("⚠️ Registration failed: empty password hash");
            return false;
        }

        // Normalize username to lowercase for case-insensitive comparison
        String normalizedUsername = username.toLowerCase().trim();

        if (accounts.containsKey(normalizedUsername)) {
            System.out.println("⚠️ Registration failed: username '" + normalizedUsername + "' already exists");
            return false;
        }

        accounts.put(normalizedUsername, passwordHash);
        saveAccounts();
        System.out.println("✅ Registered new account: " + normalizedUsername);
        return true;
    }

    /**
     * Validate login credentials
     * @return true if username exists and password hash matches
     */
    public boolean validateLogin(String username, String passwordHash) {
        if (username == null || passwordHash == null) {
            return false;
        }

        // Normalize username for case-insensitive matching
        String normalizedUsername = username.toLowerCase().trim();

        if (!accounts.containsKey(normalizedUsername)) {
            System.out.println("⚠️ Login failed: username '" + normalizedUsername + "' not found");
            return false;
        }

        String storedHash = accounts.get(normalizedUsername);
        boolean isValid = storedHash.equals(passwordHash);

        if (!isValid) {
            System.out.println("⚠️ Login failed: incorrect password for '" + normalizedUsername + "'");
        }

        return isValid;
    }

    /**
     * Save all accounts to JSON file
     */
    private synchronized void saveAccounts() {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"accounts\":[");

            List<String> users = new ArrayList<>(accounts.keySet());
            for (int i = 0; i < users.size(); i++) {
                String username = users.get(i);
                String hash = accounts.get(username);

                json.append("{\"username\":\"").append(username)
                        .append("\",\"hash\":\"").append(hash).append("\"}");

                if (i < users.size() - 1) {
                    json.append(",");
                }
            }

            json.append("]}");

            Files.write(Paths.get(ACCOUNTS_FILE), json.toString().getBytes());
        } catch (IOException e) {
            System.err.println("❌ Error saving accounts: " + e.getMessage());
        }
    }

    /**
     * Hash a password using SHA-256
     * @param password Plain text password
     * @return Hex-encoded SHA-256 hash, or null on error
     */
    public static String hashPassword(String password) {
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if an account exists
     */
    public boolean accountExists(String username) {
        return username != null && accounts.containsKey(username.toLowerCase().trim());
    }

    /**
     * Get the number of registered accounts
     */
    public int getAccountCount() {
        return accounts.size();
    }
}
