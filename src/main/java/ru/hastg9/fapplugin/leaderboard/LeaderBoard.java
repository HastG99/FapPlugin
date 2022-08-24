package ru.hastg9.fapplugin.leaderboard;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderBoard {
    private final TreeSet<BoardEntry> records;
    private final ConcurrentHashMap<String, BoardEntry> recordsMap;
    private final YamlConfiguration configuration;
    private final File dataFile;

    public LeaderBoard(String fileName) {
        records = new TreeSet<>();
        recordsMap = new ConcurrentHashMap<>();

        dataFile = new File(FapPlugin.getInstance().getDataFolder(), fileName + ".yml");

        if(!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        configuration = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void update(Player player, int record) {
        update(player.getName(), player.getUniqueId(), record);
    }

    public synchronized void update(String name, UUID uuid, int record) {
        if (!recordsMap.containsKey(name)) {
            BoardEntry entry = new BoardEntry(name, uuid, record);

            records.add(entry);
            recordsMap.put(name, entry);
            return;
        }

        BoardEntry entry = getEntry(name);
        entry.updateRecord(record);
    }

    public int size() {
        return records.size();
    }

    public synchronized Set<BoardEntry> getTop(int numberOfResults) {
        final Set<BoardEntry> result = new TreeSet<>();
        Iterator<BoardEntry> it = records.iterator();

        for (int i = 1; i < numberOfResults; i++) {
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

    public BoardEntry getEntry(String playerName) {
        return recordsMap.get(playerName);
    }

    public void save() {
        records.forEach(entry -> {
            ConfigurationSection section = configuration.createSection(entry.getUUID().toString());

            section.set("username", entry.getName());
            section.set("time", entry.getTime());
        });

        try {
            configuration.save(dataFile);
        } catch (IOException e) {
            FapPlugin.LOGGER.warning("An error occurred while saving data.");
            e.printStackTrace();
        }
    }

    public void load() {
        Set<String> keys = configuration.getKeys(false);

        keys.forEach(key -> {
            ConfigurationSection section = configuration.getConfigurationSection(key);

            update(section.getString("username"), UUID.fromString(key), section.getInt("time"));
        });
    }

}
