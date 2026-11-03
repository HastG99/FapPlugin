package ru.hastg9.fapplugin.leaderboard;

import java.util.UUID;

public class BoardEntry implements Comparable<BoardEntry> {

    private final String name;
    private final UUID uuid;
    private final long time;

    public BoardEntry(String name, UUID uuid, long time) {
        this.name = name;
        this.uuid = uuid;
        this.time = time;
    }

    public String getName() {
        return name;
    }

    public UUID getUUID() {
        return uuid;
    }

    public long getTime() {
        return time;
    }

    @Override
    public int compareTo(BoardEntry other) {
        int result = Long.compare(time, other.time);
        return result != 0 ? result : uuid.compareTo(other.uuid);
    }

}
