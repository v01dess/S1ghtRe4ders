package com.s1ghtre4ders.client.models;

public enum PlayerStatus {
    LOBBY_AVAILABLE("Available", "#00AA00"),      // Green
    LOBBY_DND("Do Not Disturb", "#CC0000"),       // Red
    SPECTATOR("Spectating", "#808080"),           // Gray
    IN_DUEL("In Duel", "#0066FF");                // Blue

    public final String displayName;
    public final String hexColor;

    PlayerStatus(String displayName, String hexColor) {
        this.displayName = displayName;
        this.hexColor = hexColor;
    }
}