package com.elysium.core.util;

import com.elysium.core.ElysiumCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Very small wrapper around a single YAML file ({@code data.yml}).
 *
 * <p>Every manager mutates the in-memory {@link YamlConfiguration} directly and calls
 * {@link #markDirty()} when something changed. The file is written on a timer (when dirty)
 * and on shutdown, so a crash can cost at most one autosave interval of data.</p>
 *
 * <p>Load/save are synchronized because the autosave task and shutdown hook both write.</p>
 */
public class DataStore {

    private final ElysiumCore plugin;
    private final File file;
    private YamlConfiguration data;

    /** Set to true whenever a manager changes something that must reach disk. */
    private boolean dirty;

    public DataStore(ElysiumCore plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    /** Loads the file from disk, creating an empty configuration if it does not exist. */
    public synchronized void load() {
        if (!file.exists()) {
            // Only reached if the bundled resource could not be extracted.
            data = new YamlConfiguration();
            return;
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    /** The live configuration all managers write into. */
    public YamlConfiguration getData() {
        return data;
    }

    /**
     * Replaces a whole section with the given entries. An empty map is written as
     * {@code section: {}} instead of being dropped, so data.yml keeps its documented shape.
     */
    public void setSection(String path, Map<String, ?> values) {
        if (values.isEmpty()) {
            data.set(path, new LinkedHashMap<String, Object>());
            return;
        }
        data.set(path, null);
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            data.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    /** Flags the data as changed; the next autosave (or shutdown) writes it. */
    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Writes the file if anything changed since the last save. */
    public void saveIfDirty() {
        if (dirty) {
            saveNow();
        }
    }

    /** Writes the file immediately and clears the dirty flag. */
    public synchronized void saveNow() {
        if (data == null) {
            return;
        }
        try {
            data.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save data.yml: " + exception.getMessage());
        }
    }
}
