package com.s1ghtre4ders.client.view.duel;

import java.util.function.Consumer;

public class DuelEventBus {

    // Events
    public final SimpleEvent<Consumer<Boolean>> onTurnChange = new SimpleEvent<>();
    public final SimpleEvent<Runnable>        onQteStart    = new SimpleEvent<>();
    public final SimpleEvent<Consumer<String>> onHpUpdate   = new SimpleEvent<>();
    public final SimpleEvent<Consumer<String>> onDuelEnd    = new SimpleEvent<>();

    // Outgoing network calls (wired by LobbyClient)
    public Consumer<Void>   sendAttack = v -> {};
    public Consumer<String> sendQTE    = q -> {};

    /**
     * Minimal single-listener event wrapper.
     */
    public static class SimpleEvent<T> {
        private T listener;

        public void subscribe(T listener) {
            this.listener = listener;
        }

        public void emit(Consumer<T> callback) {
            if (listener != null) {
                callback.accept(listener);
            }
        }
    }
}
