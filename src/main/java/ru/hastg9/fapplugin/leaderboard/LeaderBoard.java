package ru.hastg9.fapplugin.leaderboard;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.*;

public class LeaderBoard {
    private final TreeSet<BoardEntry> records;
    private final Map<UUID, BoardEntry> recordsMap;
    private final File dataFile;
    private final Object saveLock = new Object();
    private boolean saveEnabled;

    public LeaderBoard(String fileName) {
        records = new TreeSet<>();
        recordsMap = new HashMap<>();

        dataFile = new File(FapPlugin.getInstance().getDataFolder(), fileName + ".yml");

    }

    public void update(Player player, long record) {
        update(player.getName(), player.getUniqueId(), record);
    }

    public synchronized void update(String name, UUID uuid, long record) {
        if (name == null || name.isBlank() || record <= 0) return;
        BoardEntry previous = recordsMap.get(uuid);
        if (previous != null) records.remove(previous);
        BoardEntry entry = new BoardEntry(name, uuid,
                previous == null ? record : Math.min(record, previous.getTime()));
        records.add(entry);
        recordsMap.put(uuid, entry);
    }

    public synchronized int size() {
        return records.size();
    }

    public synchronized Set<BoardEntry> getTop(int numberOfResults) {
        final Set<BoardEntry> result = new TreeSet<>();
        Iterator<BoardEntry> it = records.iterator();

        for (int i = 0; i < numberOfResults; i++) {
            if(!it.hasNext()) break;

            result.add(it.next());
        }

        return result;
    }

    public void printTop(CommandSender sender, int numberOfResults) {
        sender.sendMessage(FapPlugin.getMessage("messages.leaderboard.header"));

        if(size() == 0) {
            sender.sendMessage(FapPlugin.getMessage("messages.leaderboard.no-data"));
            return;
        }

        Set<BoardEntry> entries = FapPlugin.getLeaderBoard().getTop(numberOfResults);

        int i = 1;
        for (BoardEntry entry : entries) {
            String time = StringUtils.formatDouble(entry.getTime() / 1000.0);

            sender.sendMessage(String.format(
                    FapPlugin.getMessage("messages.leaderboard.entry"),
                    i,
                    entry.getName(),
                    time
            ));

            i++;
        }
    }

    public synchronized BoardEntry getEntry(String playerName) {
        return recordsMap.values().stream()
                .filter(entry -> entry.getName().equalsIgnoreCase(playerName))
                .findFirst().orElse(null);
    }

    public synchronized BoardEntry getEntry(UUID uuid) {
        return recordsMap.get(uuid);
    }

    public void save() {
        synchronized (saveLock) {
            List<BoardEntry> snapshot;
            synchronized (this) {
                if (!saveEnabled) return;
                snapshot = new ArrayList<>(records);
            }
            Path temporary = null;
            try {
                YamlConfiguration configuration = new YamlConfiguration();
                for (BoardEntry entry : snapshot) {
                    ConfigurationSection section = configuration.createSection(entry.getUUID().toString());
                    section.set("username", entry.getName());
                    section.set("time", entry.getTime());
                }
                Path target = dataFile.toPath();
                Files.createDirectories(target.getParent());
                temporary = Files.createTempFile(target.getParent(), "data-", ".tmp");
                Files.writeString(temporary, configuration.saveToString(), StandardCharsets.UTF_8);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                FapPlugin.LOGGER.log(Level.WARNING, "Could not save records", ex);
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ex) {
                        FapPlugin.LOGGER.log(Level.WARNING, "Could not remove temporary data file", ex);
                    }
                }
            }
        }
    }

    public void load() {
        synchronized (saveLock) {
            synchronized (this) {
                saveEnabled = false;
                if (!dataFile.exists()) {
                    saveEnabled = true;
                    return;
                }
                YamlConfiguration configuration = new YamlConfiguration();
                try {
                    configuration.load(dataFile);
                } catch (IOException | InvalidConfigurationException ex) {
                    FapPlugin.LOGGER.log(Level.WARNING, "Could not load records; saving is disabled", ex);
                    backupData();
                    return;
                }
                List<BoardEntry> entries = new ArrayList<>();
                boolean invalid = false;
                for (String key : configuration.getKeys(false)) {
                    ConfigurationSection section = configuration.getConfigurationSection(key);
                    try {
                        UUID uuid = UUID.fromString(key);
                        if (section == null || !section.isString("username")
                                || section.getString("username").isBlank()
                                || !(section.isInt("time") || section.isLong("time"))
                                || section.getLong("time") <= 0) {
                            throw new IllegalArgumentException("Invalid record");
                        }
                        entries.add(new BoardEntry(section.getString("username"), uuid, section.getLong("time")));
                    } catch (IllegalArgumentException ex) {
                        invalid = true;
                        FapPlugin.LOGGER.warning("Skipping invalid record: " + key);
                    }
                }
                if (invalid && !backupData()) return;
                records.clear();
                recordsMap.clear();
                for (BoardEntry entry : entries) update(entry.getName(), entry.getUUID(), entry.getTime());
                saveEnabled = true;
            }
        }
    }

    private boolean backupData() {
        try {
            Path backup = Files.createTempFile(dataFile.toPath().getParent(), dataFile.getName() + ".invalid-", ".bak");
            Files.copy(dataFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            FapPlugin.LOGGER.warning("Original records copied to " + backup);
            return true;
        } catch (IOException ex) {
            FapPlugin.LOGGER.log(Level.WARNING, "Could not back up records; saving is disabled", ex);
            return false;
        }
    }

}
