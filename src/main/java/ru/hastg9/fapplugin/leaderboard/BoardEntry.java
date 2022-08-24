package ru.hastg9.fapplugin.leaderboard;

import java.util.UUID;

public class BoardEntry implements Comparable<BoardEntry> {

    private final String name;
    private final UUID uuid;
    private int time;

    public BoardEntry(String name, UUID uuid, int time) {
        this.name = name;
        this.uuid = uuid;
        this.time = time;
    }

    public void updateRecord(int time) {
        if(this.time > time) this.time = time;
    }

    public String getName() {
        return name;
    }

    public UUID getUUID() {
        return uuid;
    }

    public int getTime() {
        return time;
    }

    @Override
    public int compareTo(BoardEntry other) {
        return other.getTime() - time;
    }

}
