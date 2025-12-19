package com.s1ghtre4ders.server;

import com.s1ghtre4ders.server.duel.DuelManager;
import java.util.UUID;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S1ghtRe4ders Lobby Server
 *
 * A TCP-based multiplayer lobby server supporting:
 * - Player login (LOGIN/REGISTER)
 * - Real-time player list broadcasting
 * - Lobby chat
 * - Player status tracking (LOBBY_AVAILABLE, LOBBY_DND, SPECTATOR, IN_DUEL)
 * - Duel system (turn-based combat with QTE dodge)
 *
 * Protocol:
 * - LOGIN:username:passwordHash
 * - REGISTER:username:passwordHash
 * - CHAT:message
 * - SET_DND:ON|OFF
 * - ENTER_SPECTATE:targetName
 * - EXIT_SPECTATE
 * - DUEL_REQUEST:targetName
 * - DUEL_ACCEPT
 * - DUEL_DECLINE
 * - ATTACK
 * - QTE_RESULT:quality (MISS|HALF|NONE)
 */
public class LobbyServer {
    private static final int PORT = 5555;
    private static final AccountStore accountStore = new AccountStore();
    private static final CopyOnWriteArrayList<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private static final Map<String, PlayerInfo> players = new ConcurrentHashMap<>();
    private static final DuelManager duelManager = new DuelManager();
    private static final Map<String, String> pendingDuelRequests = new ConcurrentHashMap<>();
    private static final Map<String, String> activeDuels = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("🎮 S1ghtRe4ders Lobby Server");
        System.out.println("========================================");
        System.out.println("📡 Listening on port " + PORT);
        System.out.println();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ New connection from " + clientSocket.getInetAddress());

                ClientConnection handler = new ClientConnection(clientSocket);
                clients.add(handler);

                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("❌ Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Broadcast a message to all connected clients
     */
    public static void broadcastMessage(String message) {
        for (ClientConnection client : clients) {
            client.sendMessage(message);
        }
    }

    /**
     * Send a message to a specific player by username
     */
    public static void sendToPlayer(String username, String message) {
        for (ClientConnection client : clients) {
            if (username.equals(client.username)) {
                System.out.println("📤 Sending to " + username + ": " + message);
                client.sendMessage(message);
                return;
            }
        }
        System.err.println("⚠️ Could not send to " + username + " (not connected)");
    }

    /**
     * Broadcast current player list to all clients
     */
    public static void broadcastPlayerList() {
        StringBuilder sb = new StringBuilder("PLAYER_LIST:");

        for (String username : players.keySet()) {
            PlayerInfo info = players.get(username);
            sb.append(username).append(",").append(info.status).append(";");
        }

        broadcastMessage(sb.toString());
        System.out.println("📢 Broadcasted player list: " + players.size() + " players online");
    }

    /**
     * Per-client handler thread
     */
    static class ClientConnection implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username = null;
        private String currentDuelId = null;
        private boolean authenticated = false;

        public ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.authenticated = false;
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("📨 [" + (username != null ? username : "?") + "] " + message);
                    handleMessage(message);
                }
            } catch (IOException e) {
                System.out.println("❌ Client disconnected (read error)");
            } finally {
                cleanup();
            }
        }

        /**
         * Parse and handle incoming messages
         */
        private void handleMessage(String message) {
            if (message.startsWith("LOGIN:")) {
                handleLogin(message);
            } else if (message.startsWith("REGISTER:")) {
                handleRegister(message);
            } else if (!authenticated) {
                sendMessage("ERROR:You must login first");
                return;
            } else if (message.startsWith("CHAT:")) {
                handleChat(message);
            } else if (message.startsWith("SET_DND:")) {
                handleSetDND(message);
            } else if (message.startsWith("ENTER_SPECTATE:")) {
                handleEnterSpectate(message);
            } else if (message.equals("EXIT_SPECTATE")) {
                handleExitSpectate();
            } else if (message.startsWith("DUEL_REQUEST:")) {
                handleDuelRequest(message);
            } else if (message.equals("DUEL_ACCEPT")) {
                handleDuelAccept();
            } else if (message.equals("DUEL_DECLINE")) {
                handleDuelDecline();
            } else if (message.equals("ATTACK")) {
                handleAttack();
            } else if (message.startsWith("QTE_RESULT:")) {
                handleQTEResult(message);
            } else if (message.equals("GET_PLAYERS")) {
                LobbyServer.broadcastPlayerList();
            } else {
                System.out.println("⚠️ Unknown message: " + message);
            }
        }

        /**
         * LOGIN:username:passwordHash
         */
        private void handleLogin(String message) {
            if (authenticated) {
                sendMessage("ERROR:Already logged in");
                return;
            }

            String[] parts = message.substring(6).split(":", 2);
            if (parts.length != 2) {
                sendMessage("LOGIN_FAIL:Invalid format");
                return;
            }

            String username = parts[0].trim();
            String passwordHash = parts[1].trim();

            if (!LobbyServer.accountStore.validateLogin(username, passwordHash)) {
                System.out.println("❌ Failed login attempt: " + username);
                sendMessage("LOGIN_FAIL:Invalid username or password");
                return;
            }

            this.username = username;
            this.authenticated = true;
            players.put(username, new PlayerInfo(username, PlayerStatus.LOBBY_AVAILABLE));

            System.out.println("✅ [" + username + "] logged in");
            sendMessage("LOGIN_OK:" + username);
            LobbyServer.broadcastMessage("CHAT:SERVER:🟢 " + username + " joined the lobby");
            LobbyServer.broadcastPlayerList();
        }

        /**
         * REGISTER:username:passwordHash
         */
        private void handleRegister(String message) {
            if (authenticated) {
                sendMessage("ERROR:Already logged in");
                return;
            }

            String[] parts = message.substring(9).split(":", 2);
            if (parts.length != 2) {
                sendMessage("REGISTER_FAIL:Invalid format");
                return;
            }

            String username = parts[0].trim();
            String passwordHash = parts[1].trim();

            if (username.isEmpty() || passwordHash.isEmpty()) {
                sendMessage("REGISTER_FAIL:Username and password cannot be empty");
                return;
            }

            if (!LobbyServer.accountStore.register(username, passwordHash)) {
                System.out.println("❌ Registration failed: " + username + " already exists");
                sendMessage("REGISTER_FAIL:Username already exists");
                return;
            }

            System.out.println("✅ Registered new account: " + username);
            sendMessage("REGISTER_OK:Account created, you can now login");
        }

        /**
         * CHAT:message - Broadcast chat
         */
        private void handleChat(String message) {
            if (this.username == null) {
                sendMessage("ERROR:You must login first");
                return;
            }

            String chatMessage = message.substring(5).trim();
            if (chatMessage.isEmpty()) {
                return;
            }

            String broadcastMsg = "CHAT:" + this.username + ":" + chatMessage;
            System.out.println("💬 " + broadcastMsg);
            LobbyServer.broadcastMessage(broadcastMsg);
        }

        /**
         * SET_DND:ON or SET_DND:OFF - Toggle do-not-disturb
         */
        private void handleSetDND(String message) {
            if (this.username == null) {
                sendMessage("ERROR:You must login first");
                return;
            }

            String dndState = message.substring(8).trim().toUpperCase();
            PlayerInfo info = players.get(this.username);

            if (info == null) {
                return;
            }

            if ("ON".equals(dndState)) {
                info.status = PlayerStatus.LOBBY_DND;
                System.out.println("🔴 [" + this.username + "] enabled DND");
                LobbyServer.broadcastMessage("CHAT:SERVER:🔴 " + this.username + " enabled Do Not Disturb");
            } else if ("OFF".equals(dndState)) {
                info.status = PlayerStatus.LOBBY_AVAILABLE;
                System.out.println("🟢 [" + this.username + "] disabled DND");
                LobbyServer.broadcastMessage("CHAT:SERVER:🟢 " + this.username + " is now available");
            }

            LobbyServer.broadcastPlayerList();
        }

        /**
         * ENTER_SPECTATE:targetName - Enter spectator mode
         */
        private void handleEnterSpectate(String message) {
            if (this.username == null) {
                sendMessage("ERROR:You must login first");
                return;
            }

            String targetName = message.substring(15).trim();
            PlayerInfo info = players.get(this.username);

            if (info == null) {
                return;
            }

            info.status = PlayerStatus.SPECTATOR;
            System.out.println("👁️ [" + this.username + "] is now spectating " + targetName);
            LobbyServer.broadcastMessage("CHAT:SERVER:👁️ " + this.username + " is spectating");
            LobbyServer.broadcastPlayerList();
        }

        /**
         * EXIT_SPECTATE - Exit spectator mode, return to lobby
         */
        private void handleExitSpectate() {
            if (this.username == null) {
                sendMessage("ERROR:You must login first");
                return;
            }

            PlayerInfo info = players.get(this.username);

            if (info == null) {
                return;
            }

            info.status = PlayerStatus.LOBBY_AVAILABLE;
            System.out.println("👁️ [" + this.username + "] exited spectator mode");
            LobbyServer.broadcastMessage("CHAT:SERVER:👁️ " + this.username + " stopped spectating");
            LobbyServer.broadcastPlayerList();
        }

        /**
         * DUEL_REQUEST:targetName - Request a duel with another player
         */
        private void handleDuelRequest(String message) {
            if (this.username == null) {
                sendMessage("ERROR:You must login first");
                return;
            }

            String targetName = message.substring(13).trim();

            if (!players.containsKey(targetName)) {
                sendMessage("ERROR:Player not found");
                return;
            }

            if (targetName.equals(this.username)) {
                sendMessage("ERROR:Cannot duel yourself");
                return;
            }

            PlayerInfo targetInfo = players.get(targetName);
            if (targetInfo.status == PlayerStatus.LOBBY_DND || targetInfo.status == PlayerStatus.IN_DUEL) {
                sendMessage("ERROR:Player is not available");
                return;
            }

            pendingDuelRequests.put(this.username, targetName);
            System.out.println("⚔️ [" + this.username + "] requested duel with [" + targetName + "]");
            LobbyServer.sendToPlayer(targetName, "DUEL_REQUESTED:" + this.username);
        }

        /**
         * DUEL_ACCEPT - Accept the pending duel request
         */
        private void handleDuelAccept() {
            if (this.username == null) {
                sendMessage("ERROR:You must login first");
                return;
            }

            // Find requester
            String requesterTemp = null;
            for (String req : pendingDuelRequests.keySet()) {
                if (this.username.equals(pendingDuelRequests.get(req))) {
                    requesterTemp = req;
                    break;
                }
            }
            final String requester = requesterTemp;
            if (requester == null) {
                sendMessage("ERROR:No pending duel request");
                return;
            }

            pendingDuelRequests.remove(requester);

            // 1) Generate ONE duelId
            final String duelId = java.util.UUID.randomUUID().toString();
            System.out.println("🆔 Generated duelId: " + duelId);

            // 2) Create duel with that id and callbacks
            DuelManager.Duel duel = duelManager.createDuel(
                    duelId,
                    requester,
                    this.username,
                    new DuelManager.Callbacks() {
                        @Override
                        public void onQteStart(String defenderId) {
                            System.out.println("⚔️ CALLBACK onQteStart -> " + defenderId);
                            LobbyServer.sendToPlayer(defenderId, "QTE_START");
                        }

                        @Override
                        public void onTakeDamage(String playerId, int damage) {
                            System.out.println("⚔️ CALLBACK onTakeDamage -> " + playerId + " dmg=" + damage);
                            PlayerInfo info = players.get(playerId);
                            if (info == null) {
                                System.err.println("   ⚠️ Player not found in players map");
                                return;
                            }

                            info.currentHp = Math.max(0, info.currentHp - damage);
                            System.out.println("   📊 " + playerId + " HP now: " + info.currentHp);

                            String hpMsg = "HP_UPDATE:" + playerId + ":" + info.currentHp;
                            LobbyServer.sendToPlayer(requester, hpMsg);
                            LobbyServer.sendToPlayer(ClientConnection.this.username, hpMsg);

                            LobbyServer.broadcastMessage(
                                    "CHAT:SERVER:⚔️ " + playerId + " took " + damage + " damage (HP: " + info.currentHp + ")"
                            );
                        }

                        @Override
                        public void onTurnChange(String attackerId, String defenderId) {
                            System.out.println("⚔️ CALLBACK onTurnChange -> attacker=" + attackerId + ", defender=" + defenderId);
                            LobbyServer.sendToPlayer(attackerId, "TURN_CHANGE:true");
                            LobbyServer.sendToPlayer(defenderId, "TURN_CHANGE:false");
                            System.out.println("   📤 Sent TURN_CHANGE to both players");
                        }

                        @Override
                        public void onDuelEnd(String winnerId) {
                            System.out.println("🏁 CALLBACK onDuelEnd -> winner=" + winnerId);
                            String loserId = winnerId.equals(requester)
                                    ? ClientConnection.this.username
                                    : requester;

                            PlayerInfo winnerInfo = players.get(winnerId);
                            PlayerInfo loserInfo = players.get(loserId);

                            if (winnerInfo != null) {
                                winnerInfo.status = PlayerStatus.LOBBY_AVAILABLE;
                                System.out.println("   📊 " + winnerId + " status -> LOBBY_AVAILABLE");
                            }
                            if (loserInfo != null) {
                                loserInfo.status = PlayerStatus.LOBBY_AVAILABLE;
                                System.out.println("   📊 " + loserId + " status -> LOBBY_AVAILABLE");
                            }

                            LobbyServer.sendToPlayer(winnerId, "DUEL_END:WIN");
                            LobbyServer.sendToPlayer(loserId, "DUEL_END:LOSE");

                            LobbyServer.broadcastMessage(
                                    "CHAT:SERVER:🏆 " + winnerId + " defeated " + loserId
                            );

                            activeDuels.remove(duelId);
                            duelManager.endDuel(duelId);
                            LobbyServer.broadcastPlayerList();
                        }
                    }
            );

            duel.callbacks.onTurnChange(duel.state.getAttackerId(), duel.state.getDefenderId());

            // 3) Track active duel
            activeDuels.put(duelId, requester + "," + this.username);

            // 4) Set HP + status
            PlayerInfo p1 = players.get(requester);
            PlayerInfo p2 = players.get(this.username);
            if (p1 != null) {
                p1.currentHp = 100;
                p1.status = PlayerStatus.IN_DUEL;
                System.out.println("📊 " + requester + " status -> IN_DUEL, HP=100");
            }
            if (p2 != null) {
                p2.currentHp = 100;
                p2.status = PlayerStatus.IN_DUEL;
                System.out.println("📊 " + this.username + " status -> IN_DUEL, HP=100");
            }

            // 5) Set duelId on both connections
            this.currentDuelId = duelId;
            for (ClientConnection client : clients) {
                if (requester.equals(client.username)) {
                    client.currentDuelId = duelId;
                    break;
                }
            }

            // 6) Send DUEL_START
            LobbyServer.sendToPlayer(requester, "DUEL_START:" + duelId + ":1");
            LobbyServer.sendToPlayer(this.username, "DUEL_START:" + duelId + ":2");

            System.out.println("⚔️ Duel started: " + requester + " vs " + this.username);
            LobbyServer.broadcastPlayerList();
        }


        /**
         * DUEL_DECLINE - Decline the pending duel request
         */
        private void handleDuelDecline() {
            if (this.username == null) {
                sendMessage("ERROR:You must login first");
                return;
            }

            String requester = null;
            for (String req : pendingDuelRequests.keySet()) {
                if (this.username.equals(pendingDuelRequests.get(req))) {
                    requester = req;
                    break;
                }
            }

            if (requester == null) {
                sendMessage("ERROR:No pending duel request");
                return;
            }

            pendingDuelRequests.remove(requester);
            LobbyServer.sendToPlayer(requester, "DUEL_DECLINED:" + this.username);
            System.out.println("❌ [" + this.username + "] declined duel from [" + requester + "]");
        }

        /**
         * ATTACK - Send attack command to duel manager
         */
        private void handleAttack() {
            if (currentDuelId == null) {
                sendMessage("ERROR:Not in a duel");
                return;
            }

            System.out.println("⚔️ handleAttack: " + this.username + " attacks in duel " + currentDuelId);
            duelManager.attack(currentDuelId, this.username);
        }

        /**
         * QTE_RESULT:quality - Send QTE result
         * quality = MISS | HALF | NONE
         */
        private void handleQTEResult(String message) {
            if (currentDuelId == null) {
                sendMessage("ERROR:Not in a duel");
                return;
            }

            String quality = message.substring(11).trim();
            System.out.println("⚡ handleQTEResult: " + this.username + " QTE result=" + quality);
            duelManager.qteResult(currentDuelId, this.username, quality);
        }

        /**
         * Send a message to this client
         */
        public void sendMessage(String message) {
            out.println(message);
        }

        /**
         * Cleanup when client disconnects
         */
        private void cleanup() {
            if (authenticated && this.username != null) {
                players.remove(this.username);
                System.out.println("❌ [" + this.username + "] disconnected");
                LobbyServer.broadcastMessage("CHAT:SERVER:🔴 " + this.username + " left the lobby");
                LobbyServer.broadcastPlayerList();

                pendingDuelRequests.remove(this.username);
                for (String req : new ArrayList<>(pendingDuelRequests.keySet())) {
                    if (this.username.equals(pendingDuelRequests.get(req))) {
                        pendingDuelRequests.remove(req);
                    }
                }

                if (currentDuelId != null) {
                    duelManager.endDuel(currentDuelId);
                    activeDuels.remove(currentDuelId);
                }
            }

            clients.remove(this);

            try {
                socket.close();
            } catch (IOException e) {
                // Already closed
            }
        }
    }

    /**
     * Player info holder
     */
    static class PlayerInfo {
        String username;
        PlayerStatus status;
        int currentHp = 100;

        public PlayerInfo(String username, PlayerStatus status) {
            this.username = username;
            this.status = status;
        }
    }

    /**
     * Player state enum
     */
    enum PlayerStatus {
        LOBBY_AVAILABLE,
        LOBBY_DND,
        SPECTATOR,
        IN_DUEL
    }
}
