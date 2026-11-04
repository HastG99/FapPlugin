package ru.hastg9.fapplugin.managers;

import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public class FapSession {

    private final BossBar bossBar;
    private final long startedAt = System.nanoTime();
    private int count;
    private BukkitTask task;

    public FapSession(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    public void setTask(BukkitTask task) {
        this.task = task;
    }

    public void close() {
        if (task != null) task.cancel();
        bossBar.removeAll();
    }
}
