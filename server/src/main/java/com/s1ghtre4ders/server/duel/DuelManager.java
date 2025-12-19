package com.s1ghtre4ders.server.duel;

import java.util.*;
import java.util.concurrent.*;

public class DuelManager {
    private static final int MAX_HP = 100;
    private static final int QTE_WINDOW_MS = 4000; // 4 second QTE window
    private static final int BASE_DAMAGE = 15;

    private final ConcurrentHashMap<String, Duel> duels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public static class Duel {
        public final String duelId;
        public final DuelState state;
        public final Callbacks callbacks;

        public Duel(String duelId, String p1, String p2, Callbacks callbacks) {
            this.duelId = duelId;
            this.state = new DuelState(p1, p2, MAX_HP);
            this.callbacks = callbacks;
        }
    }

    public interface Callbacks {
        void onQteStart(String defenderId);
        void onTakeDamage(String playerId, int damage);
        void onTurnChange(String attackerId, String defenderId);
        void onDuelEnd(String winnerId);
    }

    public Duel createDuel(String duelId, String p1Id, String p2Id, Callbacks callbacks) {
        Duel duel = new Duel(duelId, p1Id, p2Id, callbacks);
        duels.put(duelId, duel);
        System.out.println("🎮 DuelManager.createDuel: Created duel " + duelId + " (" + p1Id + " vs " + p2Id + ")");
        return duel;
    }

    public void attack(String duelId, String attackerId) {
        System.out.println("⚔️ DuelManager.attack: " + attackerId + " attacks in duel " + duelId);

        Duel duel = duels.get(duelId);
        if (duel == null) {
            System.err.println("   ❌ Duel not found!");
            return;
        }

        DuelState state = duel.state;

        if (state.getPhase() == DuelState.Phase.ENDED) {
            System.err.println("   ❌ Duel already ended!");
            return;
        }

        if (!state.getAttackerId().equals(attackerId)) {
            System.err.println("   ❌ Not your turn! Current attacker: " + state.getAttackerId());
            return;
        }

        String defenderId = state.getDefenderId();
        System.out.println("   📢 Starting QTE for defender: " + defenderId);

        // Mark QTE window as open
        state.setQteWindow(QTE_WINDOW_MS);

        // Notify defender
        duel.callbacks.onQteStart(defenderId);

        // Set timeout: if no QTE result in QTE_WINDOW_MS, apply full damage
        scheduler.schedule(() -> {
            System.out.println("⏱️ QTE timeout check for duel " + duelId);
            if (state.isQteValid()) {
                System.out.println("⏱️ QTE timed out! Applying full damage to " + defenderId);
                applyDamage(duel, defenderId, BASE_DAMAGE, "TIMEOUT");
            } else {
                System.out.println("⏱️ QTE already resolved, skipping timeout");
            }
        }, QTE_WINDOW_MS, TimeUnit.MILLISECONDS);
    }

    public void qteResult(String duelId, String defenderId, String quality) {
        System.out.println("⚡ DuelManager.qteResult: " + defenderId + " QTE result=" + quality);

        Duel duel = duels.get(duelId);
        if (duel == null) {
            System.err.println("   ❌ Duel not found!");
            return;
        }

        DuelState state = duel.state;

        if (!state.isQteValid()) {
            System.err.println("   ❌ QTE window closed or already resolved!");
            return;
        }

        // Mark QTE as resolved
        state.invalidateQte();

        int damage = 0;

        if ("NONE".equals(quality)) {
            // Perfect dodge - no damage
            damage = 0;
            System.out.println("   ✓ Perfect dodge! No damage");
        } else if ("HALF".equals(quality)) {
            // Good dodge - half damage
            damage = BASE_DAMAGE / 2;
            System.out.println("   ◑ Good dodge! Half damage (" + damage + ")");
        } else if ("MISS".equals(quality)) {
            // Miss or outside zone - full damage
            damage = BASE_DAMAGE;
            System.out.println("   ✗ Missed! Full damage (" + damage + ")");
        } else {
            // Unknown - treat as miss
            damage = BASE_DAMAGE;
            System.out.println("   ? Unknown QTE result, treating as miss");
        }

        applyDamage(duel, defenderId, damage, quality);
    }

    private void applyDamage(Duel duel, String defenderId, int damage, String source) {
        System.out.println("🔴 applyDamage: " + defenderId + " takes " + damage + " damage (from: " + source + ")");

        DuelState state = duel.state;
        state.takeDamage(defenderId, damage);

        // Notify about damage
        duel.callbacks.onTakeDamage(defenderId, damage);

        // Check if duel is over
        if (state.getPhase() == DuelState.Phase.ENDED) {
            System.out.println("💀 Duel phase is ENDED!");
            String winnerId = state.getHp(state.player1Id) > 0 ? state.player1Id : state.player2Id;
            System.out.println("🏆 Winner: " + winnerId);
            duel.callbacks.onDuelEnd(winnerId);
            duels.remove(duel.duelId);
        } else {
            // Advance to next turn
            System.out.println("↪️ Moving to next turn");
            state.nextTurn();
            String nextAttackerId = state.getAttackerId();
            String nextDefenderId = state.getDefenderId();
            System.out.println("↪️ Next attacker: " + nextAttackerId + ", defender: " + nextDefenderId);
            // After duel is created, set initial turn
            duel.callbacks.onTurnChange(duel.state.getAttackerId(), duel.state.getDefenderId());

        }
    }

    public void endDuel(String duelId) {
        System.out.println("🔚 DuelManager.endDuel: Ending duel " + duelId);
        duels.remove(duelId);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
