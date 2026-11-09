package net.bitbylogic.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.bitbylogic.module.scheduler.ModuleScheduler;
import net.bitbylogic.utils.color.ColorUtil;
import net.bitbylogic.utils.config.configurable.Configurable;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;

@Getter
public abstract class BitsModule extends Configurable implements ModuleInterface, Listener {

    private final JavaPlugin plugin;
    private final ModuleManager moduleManager;

    private final File dataFolder;
    private final File configFile;

    private final ModuleScheduler scheduler;

    @Getter(AccessLevel.NONE)
    private final List<ModuleCommand<?>> commands = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    private final List<Listener> listeners = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    private final List<Configurable> configurables;

    @Setter(AccessLevel.PACKAGE)
    private volatile boolean enabled = false;

    private volatile boolean debug = false;

    private volatile YamlConfiguration config;

    @Getter(AccessLevel.NONE)
    private boolean initialized = false;

    public BitsModule(JavaPlugin plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;

        this.scheduler = new ModuleScheduler(this);

        ModuleData moduleData = getModuleData();
        String moduleDir = moduleData.id().toLowerCase(Locale.ROOT).replace(" ", "_");

        this.dataFolder = new File(plugin.getDataFolder() + File.separator + moduleDir);
        this.configFile = new File(getDataFolder() + File.separator + "config.yml");
        this.configurables = new ArrayList<>();
    }

    public List<ModuleCommand<?>> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public List<Listener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public List<Configurable> getConfigurables() {
        return Collections.unmodifiableList(configurables);
    }

    void restoreRegistrations(List<ModuleCommand<?>> restoredCommands, List<Listener> restoredListeners) {
        commands.clear();
        commands.addAll(restoredCommands);
        listeners.clear();
        listeners.addAll(restoredListeners);
    }

    void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;

        loadConfiguration();

        setConfigFile(configFile);
        loadConfigPaths();
    }

    private void loadConfiguration() {
        ModuleData moduleData = getModuleData();
        String moduleDir = moduleData.id().toLowerCase(Locale.ROOT).replace(" ", "_");

        if (!configFile.exists()) {
            InputStream configStream = plugin.getResource(moduleDir + "/config.yml");

            if (configStream != null) {
                plugin.saveResource(moduleDir + "/config.yml", false);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save configuration file for module '" + getModuleData().id() + "'.", e);
        }
    }

    public <T> T getConfigValueOrDefault(@NonNull String path, @NonNull T defaultValue) {
        return getConfigValueOrDefault(path, defaultValue, true);
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfigValueOrDefault(@NonNull String path, @NonNull T defaultValue, boolean save) {
        Object actualValue = getConfig().get(path);

        if (actualValue == null && save) {
            config.set(path, defaultValue);
            saveConfig();
        }

        if (actualValue == null) {
            return defaultValue;
        }

        if (!defaultValue.getClass().isInstance(actualValue)) {
            log(Level.SEVERE, String.format("Config value at '%s' is a %s, expected %s. Using default.",
                    path, actualValue.getClass().getSimpleName(), defaultValue.getClass().getSimpleName()));
            return defaultValue;
        }

        return (T) actualValue;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void registerCommand(ModuleCommand... commands) {
        for (ModuleCommand command : commands) {
            if (this.commands.contains(command)) {
                continue;
            }

            command.setModule(this);
            this.commands.add(command);
        }
    }

    protected void registerModuleListener(Listener... listeners) {
        for (Listener listener : listeners) {
            if (this.listeners.contains(listener)) {
                return;
            }

            moduleManager.getDependencyManager().injectDependencies(listener, true);
            this.listeners.add(listener);

            if(enabled) {
                Bukkit.getServer().getPluginManager().registerEvents(listener, plugin);
            }

            debug(Level.INFO, String.format("Successfully registered listener: %s", listener.getClass().getSimpleName()));
        }
    }

    protected void registerConfigurable(Configurable configurable) {
        if (configurables.contains(configurable)) {
            return;
        }

        configurables.add(configurable);
    }

    private void ensureDirectory(File directory) {
        if (directory == null || directory.exists() || directory.mkdirs()) {
            return;
        }

        plugin.getLogger().log(Level.WARNING, "Unable to create directory '" + directory.getPath() + "' for module '" + getModuleData().id() + "'.");
    }

    public File getModuleFile(@NonNull String name) {
        ensureDirectory(getDataFolder());

        File file = new File(getDataFolder() + File.separator + name);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, String.format("Unable to create module file '%s' for module '%s'!", name, getModuleData().id()), e);
            }
        }

        return file;
    }

    /**
     * Return a configuration file from the module's folder.
     *
     * @param name The files name (do not include .yml)
     * @return The newly created configuration file instance.
     */
    public YamlConfiguration getModuleConfig(String name) {
        ensureDirectory(getDataFolder());

        File tempConfigFile = new File(getDataFolder() + File.separator + name + ".yml");

        if (!tempConfigFile.exists()) {
            ensureDirectory(tempConfigFile.getParentFile());

            try {
                tempConfigFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Unable to create module config '" + name + ".yml' for module '" + getModuleData().id() + "'.", e);
            }
        }

        return YamlConfiguration.loadConfiguration(tempConfigFile);
    }

    public void saveModuleConfig(YamlConfiguration config, String fileName) {
        File tempConfigFile = new File(getDataFolder() + File.separator + fileName + ".yml");

        try {
            config.save(tempConfigFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save module config '" + fileName + ".yml' for module '" + getModuleData().id() + "'.", e);
        }
    }

    public void log(Level level, String message) {
        switch (level.getName()) {
            case "SEVERE":
                plugin.getLogger().severe(ColorUtil.colorForConsole(String.format("&8[&9%s&8] &c%s", getModuleData().name(), message)));
                break;
            case "WARNING":
                plugin.getLogger().warning(ColorUtil.colorForConsole(String.format("&8[&9%s&8] &e%s", getModuleData().name(), message)));
                break;
            case "INFO":
                plugin.getLogger().info(ColorUtil.colorForConsole(String.format("&8[&9%s&8] &2%s", getModuleData().name(), message)));
                break;
            default:
                plugin.getLogger().log(level, ColorUtil.colorForConsole(String.format("&8[&9%s&8] &7%s", getModuleData().name(), message)));
                break;
        }
    }

    public void debug(Level level, String message) {
        if (!debug) {
            return;
        }

        switch (level.getName()) {
            case "SEVERE":
                plugin.getLogger().severe(ColorUtil.colorForConsole(String.format("&c[DEBUG] &8[&9%s&8] &c%s", getModuleData().name(), message)));
                break;
            case "WARNING":
                plugin.getLogger().warning(ColorUtil.colorForConsole(String.format("&c[DEBUG] &8[&9%s&8] &e%s", getModuleData().name(), message)));
                break;
            case "INFO":
                plugin.getLogger().info(ColorUtil.colorForConsole(String.format("&c[DEBUG] &8[&9%s&8] &2%s", getModuleData().name(), message)));
                break;
            default:
                plugin.getLogger().log(level, ColorUtil.colorForConsole(String.format("&c[DEBUG] &8[&9%s&8] &7%s", getModuleData().name(), message)));
                break;
        }
    }

    public void debugBroadcast(String message) {
        if (!debug) {
            return;
        }

        Bukkit.broadcast("(" + getModuleData().name() + ") [DEBUG]: " + message, "bitsmodules.debuglog");
    }

    public <T extends BitsModule> void addDependencyTask(Class<T> dependency, Consumer<T> consumer) {
        moduleManager.addDependencyTask(dependency, consumer);
    }

    @Override
    public void loadConfigPaths() {
        super.loadConfigPaths();
        //configurables.forEach(Configurable::loadConfigPaths);
    }

    public void setDebug(boolean debug) {
        this.debug = debug;

        String moduleId = getModuleData().id();
        List<String> debugModules = new ArrayList<>(plugin.getConfig().getStringList("Debug-Modules"));
        boolean listed = debugModules.stream().anyMatch(moduleId::equalsIgnoreCase);

        if (listed == debug) {
            return;
        }

        if (debug) {
            debugModules.add(moduleId);
        } else {
            debugModules.removeIf(moduleId::equalsIgnoreCase);
        }

        plugin.getConfig().set("Debug-Modules", debugModules);
        plugin.saveConfig();
    }
}
