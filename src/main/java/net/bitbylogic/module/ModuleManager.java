package net.bitbylogic.module;

import co.aikar.commands.PaperCommandManager;
import lombok.Getter;
import lombok.NonNull;
import net.bitbylogic.module.command.ModulesCommand;
import net.bitbylogic.module.event.ModuleDisableEvent;
import net.bitbylogic.module.event.ModuleEnableEvent;
import net.bitbylogic.module.message.ModuleMessages;
import net.bitbylogic.module.scheduler.ModuleTask;
import net.bitbylogic.module.task.ModulePendingTask;
import net.bitbylogic.utils.color.ColorUtil;
import net.bitbylogic.utils.dependency.DependencyManager;
import net.bitbylogic.utils.message.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Getter
public class ModuleManager {

    private final JavaPlugin plugin;

    private final DependencyManager dependencyManager;
    private final PaperCommandManager commandManager;

    private final Set<String> disabledModules;
    private final Set<String> debugModules;

    private final Map<Class<? extends BitsModule>, BitsModule> modulesByClass = new HashMap<>();
    private final HashMap<String, BitsModule> modulesById = new HashMap<>();

    private final HashMap<Class<?>, List<Class<?>>> pendingModules = new HashMap<>();

    private final Set<ModuleTask> cleanupQueue = ConcurrentHashMap.newKeySet();

    private final Map<Class<? extends BitsModule>, List<ModulePendingTask<? extends BitsModule>>> pendingTasksByModule = new HashMap<>();

    public ModuleManager(@NotNull JavaPlugin plugin, @NotNull PaperCommandManager commandManager, @NotNull DependencyManager dependencyManager) {
        this.plugin = plugin;

        this.commandManager = commandManager;
        this.dependencyManager = dependencyManager;

        Messages.registerGroup(new ModuleMessages());

        this.disabledModules = new HashSet<>(plugin.getConfig().getStringList("Disabled-Modules"));
        this.debugModules = new HashSet<>(plugin.getConfig().getStringList("Debug-Modules"));

        commandManager.registerDependency(getClass(), this);
        dependencyManager.registerDependency(getClass(), this);

        ModulesCommand modulesCommand = new ModulesCommand();
        dependencyManager.injectDependencies(modulesCommand, true);
        commandManager.registerCommand(modulesCommand);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<ModuleTask> iterator = cleanupQueue.iterator();

            while (iterator.hasNext()) {
                ModuleTask task = iterator.next();
                BitsModule module = task.getModuleInstance();

                if (module != null) {
                    synchronized (module.getScheduler().getTasks()) {
                        module.getScheduler().getTasks().remove(task);
                    }
                }

                iterator.remove();
            }
        }, 0L, 20L * 30L);
    }

    /**
     * Register a Module.
     *
     * @param classes The classes to register.
     */
    @SafeVarargs
    public final void registerModule(Class<? extends BitsModule>... classes) {
        for (Class<? extends BitsModule> moduleClass : classes) {
            if (modulesByClass.get(moduleClass) != null) {
                plugin.getLogger().log(Level.WARNING,
                        ColorUtil.colorForConsole("&8[&9Module Manager&8] &cModule '&4" + moduleClass.getName() + "&c' is already registered."));
                continue;
            }

            try {
                BitsModule module = moduleClass.getDeclaredConstructor(JavaPlugin.class, ModuleManager.class).newInstance(plugin, this);
                boolean missingModule = false;

                StringBuilder missingModules = new StringBuilder();

                for (Class<?> dependency : dependencyManager.getDependencies(module, true)) {
                    if (!BitsModule.class.isAssignableFrom(dependency) || dependencyManager.isDependencyRegistered(dependency)) {
                        continue;
                    }

                    pendingModules.computeIfAbsent(dependency, k -> new ArrayList<>())
                            .add(moduleClass);
                    missingModule = true;

                    missingModules.append(", ").append(dependency.getSimpleName());
                }

                if(missingModule) {
                    plugin.getLogger().log(Level.WARNING,
                            ColorUtil.colorForConsole("&8[&9Module Manager&8] &eWaiting to register module: '&6" + moduleClass.getName() + "&E', it requires the following dependencies:"));
                    plugin.getLogger().log(Level.WARNING,
                            ColorUtil.colorForConsole("&8[&9Module Manager&8] &e" + missingModules.toString().replaceFirst("&8, &e", "")));
                    continue;
                }

                registerModuleData(module);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                plugin.getLogger().log(Level.SEVERE,
                        ColorUtil.colorForConsole("&8[&9Module Manager&8] &cCouldn't create new instance of module class '&4" + moduleClass.getName() + "&c'"));
                e.printStackTrace();
            }
        }
    }

    private void registerModuleData(@NonNull BitsModule module) {
        Class<? extends BitsModule> moduleClass = module.getClass();
        long startTime = System.nanoTime();

        commandManager.registerDependency(moduleClass, module);
        dependencyManager.registerDependency(moduleClass, module);
        dependencyManager.injectDependencies(module, true);

        if(debugModules.contains(module.getModuleData().id())) {
            module.setDebug(true);
        }

        modulesByClass.put(moduleClass, module);
        modulesById.put(module.getModuleData().id().toLowerCase(Locale.ROOT), module);

        module.onRegister();
        module.getCommands().forEach(command -> dependencyManager.injectDependencies(command, true));

        if (!disabledModules.contains(module.getModuleData().id())) {
            module.setEnabled(true);
            module.onEnable();
            module.getCommands().forEach(commandManager::registerCommand);
            Bukkit.getPluginManager().registerEvents(module, plugin);

            ModuleEnableEvent enableEvent = new ModuleEnableEvent(module);
            Bukkit.getPluginManager().callEvent(enableEvent);
        }

        pendingTasksByModule
                .getOrDefault(moduleClass, Collections.emptyList())
                .forEach(task -> {
                    @SuppressWarnings("unchecked")
                    ModulePendingTask<BitsModule> castedTask = (ModulePendingTask<BitsModule>) task;

                    castedTask.accept(module);
                });

        pendingTasksByModule.remove(moduleClass);

        long endTime = System.nanoTime();
        plugin.getLogger().log(Level.INFO,
                ColorUtil.colorForConsole("&8[&9" + module.getModuleData().name() + "&8] &2Successfully registered in &a" + (endTime - startTime) / 1000000d + "&2ms"));
    }

    /**
     * Check if a Module is registered.
     *
     * @param clazz The Module's class.
     * @return {@code true} if the Module is registered.
     */
    public boolean isRegistered(Class<? extends BitsModule> clazz) {
        return modulesByClass.containsKey(clazz);
    }

    /**
     * Get a Module instance by it class.
     *
     * @param clazz The Module's class.
     * @return An instance of the Module.
     */
    public <T extends BitsModule> Optional<T> getModuleInstance(Class<T> clazz) {
        return Optional.ofNullable((T) modulesByClass.get(clazz));
    }

    /**
     * Enable a Module.
     *
     * @param moduleID The Module's ID.
     */
    public void enableModule(String moduleID) {
        Optional<BitsModule> optionalModule = getModuleByID(moduleID);

        if (optionalModule.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, ColorUtil.colorForConsole("&8[&9Module Manager&8] &cInvalid Module ID '&4" + moduleID + "&c'."));
            return;
        }

        BitsModule module = optionalModule.get();

        if (module.isEnabled()) {
            return;
        }

        disabledModules.remove(module.getModuleData().id());

        plugin.getConfig().set("Disabled-Modules", disabledModules);
        plugin.saveConfig();

        module.setEnabled(true);
        module.reloadConfig();
        module.loadConfigPaths();
        module.onEnable();
        module.getCommands().forEach(commandManager::registerCommand);
        module.getListeners().forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, plugin));
        Bukkit.getPluginManager().registerEvents(module, plugin);

        ModuleEnableEvent enableEvent = new ModuleEnableEvent(module);
        Bukkit.getPluginManager().callEvent(enableEvent);
    }

    /**
     * Disable a Module.
     *
     * @param moduleID The Module's ID.
     */
    public void disableModule(String moduleID) {
        Optional<BitsModule> optionalModule = getModuleByID(moduleID);

        if (optionalModule.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, ColorUtil.colorForConsole("&8[&9Module Manager&8] &cInvalid Module ID '&4" + moduleID + "&c'."));
            return;
        }

        BitsModule module = optionalModule.get();

        if (!module.isEnabled()) {
            return;
        }

        disabledModules.add(module.getModuleData().id());

        plugin.getConfig().set("Disabled-Modules", disabledModules);
        plugin.saveConfig();

        module.setEnabled(false);
        module.onDisable();
        new ArrayList<>(module.getScheduler().getTasks()).forEach(ModuleTask::cancel);
        module.getListeners().forEach(HandlerList::unregisterAll);
        module.getCommands().forEach(commandManager::unregisterCommand);
        HandlerList.unregisterAll(module);

        ModuleDisableEvent disableEvent = new ModuleDisableEvent(module);
        Bukkit.getPluginManager().callEvent(disableEvent);
    }

    public Optional<BitsModule> getModuleByID(@NonNull String id) {
        return Optional.ofNullable(modulesById.get(id.toLowerCase(Locale.ROOT)));
    }

    public void scheduleCleanup(@NonNull ModuleTask task) {
        cleanupQueue.add(task);
    }

}
