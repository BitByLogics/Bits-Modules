package net.bitbylogic.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.bitbylogic.module.scheduler.ModuleScheduler;
import net.bitbylogic.module.task.ModulePendingTask;
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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;

@Getter
@Setter
public abstract class BitsModule extends Configurable implements ModuleInterface, Listener {

    private final JavaPlugin plugin;
    private final ModuleManager moduleManager;

    private final File dataFolder;
    private final File configFile;

    private final ModuleScheduler scheduler;

    private final List<ModuleCommand> commands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    private final List<Configurable> configurables;

    private boolean enabled = false;

    @Setter(AccessLevel.NONE)
    private boolean debug = false;

    private YamlConfiguration config;

    public BitsModule(JavaPlugin plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;

        this.scheduler = new ModuleScheduler(this);

        ModuleData moduleData = getModuleData();
        String moduleDir = moduleData.id().toLowerCase(Locale.ROOT).replace(" ", "_");

        this.dataFolder = new File(plugin.getDataFolder() + File.separator + moduleDir);
        this.configFile = new File(getDataFolder() + File.separator + "config.yml");
        this.configurables = new ArrayList<>();

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
            log(Level.SEVERE, "Unable to save configuration file.");
            e.printStackTrace();
        }
    }

    public <T> T getConfigValueOrDefault(@NonNull String path, @NonNull T defaultValue) {
        return getConfigValueOrDefault(path, defaultValue, true);
    }

    public <T> T getConfigValueOrDefault(@NonNull String path, @NonNull T defaultValue, boolean save) {
        Object actualValue = getConfig().get(path);

        if (actualValue == null && save) {
            config.set(path, defaultValue);
            saveConfig();
        }

        try {
            return actualValue == null ? defaultValue : (T) actualValue;
        } catch (ClassCastException e) {
            log(Level.SEVERE, "Unable to cast config value");
            e.printStackTrace();
        }

        return defaultValue;
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

            Bukkit.getServer().getPluginManager().registerEvents(listener, plugin);
            debug(Level.INFO, String.format("Successfully registered listener: %s", listener.getClass().getSimpleName()));
        }
    }

    protected void registerConfigurable(Configurable configurable) {
        if (configurables.contains(configurable)) {
            return;
        }

        configurables.add(configurable);
    }

    public File getModuleFile(@NonNull String name) {
        if(!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        File file = new File(getDataFolder() + File.separator + name);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                log(Level.WARNING, String.format("Unable to create module file '%s' for module '%s'!", name, getModuleData().id()));
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
        if(!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        File tempConfigFile = new File(getDataFolder() + File.separator + name + ".yml");

        if (!tempConfigFile.exists()) {
            tempConfigFile.getParentFile().mkdirs();

            try {
                tempConfigFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return YamlConfiguration.loadConfiguration(tempConfigFile);
    }

    public void saveModuleConfig(YamlConfiguration config, String fileName) {
        File tempConfigFile = new File(getDataFolder() + File.separator + fileName + ".yml");

        try {
            config.save(tempConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void log(Level level, String message) {
        switch (level.getName()) {
            case "SEVERE":
                plugin.getLogger().severe(ColorUtil.colorForConsole(String.format("&8[&9%s&8] &c%s", getModuleData().name(), message)));
                break;
            case "WARNING":
                plugin.getLogger().warning(ColorUtil.colorForConsole(String.format("&8[&9%s&8] &e%s", getModuleData().name(), message)));
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

        Bukkit.broadcast("(" + getModuleData().name() + ") [DEBUG]: " + message, "apibylogic.module.debuglog");
    }

    public <T extends BitsModule> void addDependencyTask(Class<T> dependency, Consumer<T> consumer) {
        BitsModule existingModule = (BitsModule) moduleManager.getDependencyManager().getDependencies().get(dependency);

        if (existingModule != null) {
            consumer.accept(dependency.cast(existingModule));
            return;
        }

        moduleManager.getPendingTasksByModule()
                .computeIfAbsent(dependency, k -> new ArrayList<>())
                .add(new ModulePendingTask<T>(dependency) {
                    @Override
                    public void accept(T module) {
                        consumer.accept(module);
                    }
                });
    }

    @Override
    public void loadConfigPaths() {
        super.loadConfigPaths();
        //configurables.forEach(Configurable::loadConfigPaths);
    }

    public void setDebug(boolean debug) {
        this.debug = debug;

        List<String> debugModules = plugin.getConfig().getStringList("Debug-Modules");

        if(debugModules.contains(getModuleData().id()) && debug || !debugModules.contains(getModuleData().id()) && !debug) {
            return;
        }

        if(!debug) {
            debugModules.remove(getModuleData().id());
        } else {
            debugModules.add(getModuleData().id());
        }

        plugin.getConfig().set("Debug-Modules", debugModules);
        plugin.saveConfig();
    }
}
