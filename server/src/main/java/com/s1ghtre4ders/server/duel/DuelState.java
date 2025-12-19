package com.s1ghtre4ders.server.duel;

import java.util.concurrent.atomic.AtomicBoolean;

public class DuelState {
    public enum Phase {
        ACTIVE,
        ENDED
    }

    // Players
    public final String player1Id;
    public final String player2Id;

    // Current HP
    private int player1Hp;
    private int player2Hp;

    // Current turn state
    private boolean player1Turn = true; // true = p1 attacks, false = p2 attacks

    // QTE state
    private long qteExpiresAt = 0;
    private final AtomicBoolean qteValid = new AtomicBoolean(false);

    // Duel phase
    private Phase phase = Phase.ACTIVE;

    public DuelState(String p1Id, String p2Id, int maxHp) {
        this.player1Id = p1Id;
        this.player2Id = p2Id;
        this.player1Hp = maxHp;
        this.player2Hp = maxHp;
        System.out.println("🎮 DuelState created: " + p1Id + " vs " + p2Id + " (P1 turn)");
    }

    public String getAttackerId() {
        return player1Turn ? player1Id : player2Id;
    }

    public String getDefenderId() {
        return player1Turn ? player2Id : player1Id;
    }

    public int getHp(String playerId) {
        return playerId.equals(player1Id) ? player1Hp : player2Hp;
    }

    public Phase getPhase() {
        return phase;
    }

    /**
     * Start QTE window
     */
    public void setQteWindow(long durationMs) {
        qteValid.set(true);
        qteExpiresAt = System.currentTimeMillis() + durationMs;
        System.out.println("   ⏱️ QTE window opened until " + qteExpiresAt);
    }

    /**
     * Check if QTE window is still valid
     */
    public boolean isQteValid() {
        if (!qteValid.get()) {
            return false;
        }
        boolean stillValid = System.currentTimeMillis() < qteExpiresAt;
        if (!stillValid) {
            System.out.println("   ⏱️ QTE window expired");
        }
        return stillValid;
    }

    /**
     * Invalidate QTE window (player responded)
     */
    public void invalidateQte() {
        qteValid.set(false);
        System.out.println("   ⚡ QTE window closed (player responded)");
    }

    /**
     * Apply damage to a player
     */
    public void takeDamage(String playerId, int damage) {
        if (playerId.equals(player1Id)) {
            player1Hp = Math.max(0, player1Hp - damage);
            System.out.println("   📊 " + player1Id + " HP: " + player1Hp);
        } else {
            player2Hp = Math.max(0, player2Hp - damage);
            System.out.println("   📊 " + player2Id + " HP: " + player2Hp);
        }

        // Check win condition
        if (player1Hp == 0 || player2Hp == 0) {
            phase = Phase.ENDED;
            System.out.println("💀 Duel ended! P1 HP=" + player1Hp + ", P2 HP=" + player2Hp);
        }
    }

    /**
     * Switch to next turn
     */
    public void nextTurn() {
        player1Turn = !player1Turn;
        System.out.println("↪️ Turn switched to: " + getAttackerId());
    }
}