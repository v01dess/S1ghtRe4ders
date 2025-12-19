package com.s1ghtre4ders.client.models;

public class Player {
    private String username;
    private PlayerStatus status;

    public Player(String username, PlayerStatus status) {
        this.username = username;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return username + " [" + status.displayName + "]";
    }
}