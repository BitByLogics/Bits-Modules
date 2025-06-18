package net.bitbylogic.module;

import co.aikar.commands.PaperCommandManager;
import lombok.Getter;
import lombok.NonNull;
import net.bitbylogic.module.command.ModulesCommand;
import net.bitbylogic.module.task.ModulePendingTask;
import net.bitbylogic.utils.dependency.DependencyManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;

@Getter
public class ModuleManager {

    private final JavaPlugin plugin;
    private final DependencyManager dependencyManager;
    private final PaperCommandManager commandManager;

    private final HashMap<String, BitsModule> modules;
    private final HashMap<Class<?>, List<Class<?>>> pendingModules = new HashMap<>();

    private final List<ModulePendingTask<? extends BitsModule>> pendingModuleTasks;

    public ModuleManager(JavaPlugin plugin, PaperCommandManager commandManager, DependencyManager dependencyManager) {
        this.plugin = plugin;
        this.dependencyManager = dependencyManager;
        this.commandManager = commandManager;

        this.modules = new HashMap<>();
        this.pendingModuleTasks = new ArrayList<>();

        commandManager.registerDependency(getClass(), this);
        dependencyManager.registerDependency(getClass(), this);

        ModulesCommand modulesCommand = new ModulesCommand();
        dependencyManager.injectDependencies(modulesCommand, true);
        commandManager.registerCommand(modulesCommand);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (BitsModule module : modules.values()) {
                if (module.getTasks().isEmpty()) {
                    continue;
                }

                synchronized (module.getTasks()) {
                    Iterator<ModuleTask> moduleTaskIterator = module.getTasks().iterator();

                    while (moduleTaskIterator.hasNext()) {
                        ModuleTask task = moduleTaskIterator.next();

                        if (task == null) {
                            moduleTaskIterator.remove();
                            continue;
                        }

                        if (task.getTaskId() == -1 || task.isActive()) {
                            continue;
                        }

                        moduleTaskIterator.remove();
                    }
                }
            }
        }, 0, 20 * 30);
    }

    /**
     * Register a Module.
     *
     * @param classes The classes to register.
     */
    @SafeVarargs
    public final void registerModule(Class<? extends BitsModule>... classes) {
        for (Class<? extends BitsModule> moduleClass : classes) {
            if (modules.get(moduleClass.getSimpleName()) != null) {
                plugin.getLogger().log(Level.WARNING, "[Module Manager]: Couldn't register module '" + moduleClass.getSimpleName() + "', this module is already registered.");
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
                    plugin.getLogger().log(Level.WARNING, "[Module Manager]: Waiting to register module: '" + moduleClass.getName() + "', it requires the following dependencies:");
                    plugin.getLogger().log(Level.WARNING, "[Module Manager]: " + missingModules.toString().replaceFirst(", ", ""));
                    continue;
                }

                registerModuleData(module);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                plugin.getLogger().log(Level.SEVERE, "[Module Manager]: Couldn't create new instance of module class '" + moduleClass.getName() + "'");
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

        if(plugin.getConfig().getStringList("Debug-Modules").contains(module.getModuleData().getId())) {
            module.setDebug(true);
        }

        modules.put(moduleClass.getSimpleName(), module);

        module.onRegister();
        module.getCommands().forEach(command -> dependencyManager.injectDependencies(command, true));

        if (!plugin.getConfig().getStringList("Disabled-Modules").contains(module.getModuleData().getId())) {
            module.setEnabled(true);
            module.onEnable();
            module.getCommands().forEach(commandManager::registerCommand);
            Bukkit.getPluginManager().registerEvents(module, plugin);
        }

        getPendingTasks(moduleClass).forEach(task -> task.accept(module));
        pendingModuleTasks.removeIf(task -> task.getClazz().equals(moduleClass));

        long endTime = System.nanoTime();
        plugin.getLogger().log(Level.INFO, "[Module Manager]: Registration time for module (" + module.getModuleData().getName() + "): " + (endTime - startTime) / 1000000d + "ms");
    }

    /**
     * Check if a Module is registered.
     *
     * @param clazz The Module's class.
     * @return {@code true} if the Module is registered.
     */
    public boolean isRegistered(Class<? extends BitsModule> clazz) {
        for (BitsModule module : modules.values()) {
            if (module.getClass() == clazz) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get a Module instance by it class.
     *
     * @param clazz The Module's class.
     * @return An instance of the Module.
     */
    public <T extends BitsModule> Optional<T> getModuleInstance(Class<T> clazz) {
        for (BitsModule module : modules.values()) {
            if (module.getClass() != clazz) {
                continue;
            }

            try {
                return Optional.of((T) module);
            } catch (ClassCastException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Enable a Module.
     *
     * @param moduleID The Module's ID.
     */
    public void enableModule(String moduleID) {
        Optional<BitsModule> optionalModule = getModuleByID(moduleID);

        if (optionalModule.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, "[Module Manager]: Invalid Module ID '" + moduleID + "'.");
            return;
        }

        BitsModule module = optionalModule.get();

        if (module.isEnabled()) {
            return;
        }

        List<String> disabledModules = plugin.getConfig().getStringList("Disabled-Modules");
        disabledModules.remove(module.getModuleData().getId());

        plugin.getConfig().set("Disabled-Modules", disabledModules);
        plugin.saveConfig();

        module.setEnabled(true);
        module.reloadConfig();
        module.loadConfigPaths();
        module.onEnable();
        module.getCommands().forEach(commandManager::registerCommand);
        module.getListeners().forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, plugin));
        Bukkit.getPluginManager().registerEvents(module, plugin);
    }

    /**
     * Disable a Module.
     *
     * @param moduleID The Module's ID.
     */
    public void disableModule(String moduleID) {
        Optional<BitsModule> optionalModule = getModuleByID(moduleID);

        if (optionalModule.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, "[Module Manager]: Invalid Module ID '" + moduleID + "'.");
            return;
        }

        BitsModule module = optionalModule.get();

        if (!module.isEnabled()) {
            return;
        }

        List<String> disabledModules = plugin.getConfig().getStringList("Disabled-Modules");
        disabledModules.add(module.getModuleData().getId());

        plugin.getConfig().set("Disabled-Modules", disabledModules);
        plugin.saveConfig();

        module.setEnabled(false);
        module.onDisable();
        new ArrayList<>(module.getTasks()).forEach(ModuleTask::cancel);
        module.getListeners().forEach(HandlerList::unregisterAll);
        module.getCommands().forEach(commandManager::unregisterCommand);
        HandlerList.unregisterAll(module);
    }

    /**
     * Get a Module instance by its ID.
     *
     * @param id The Module's ID.
     * @return The Module instance.
     */
    public Optional<BitsModule> getModuleByID(String id) {
        return modules.values().stream().filter(module -> module.getModuleData().getId().equalsIgnoreCase(id)).findFirst();
    }

    private <T extends BitsModule> List<ModulePendingTask<BitsModule>> getPendingTasks(Class<T> moduleClass) {
        List<ModulePendingTask<BitsModule>> tasks = new ArrayList<>();
        for (ModulePendingTask<? extends BitsModule> task : pendingModuleTasks) {
            if (moduleClass.isAssignableFrom(task.getClazz())) {
                @SuppressWarnings("unchecked")
                ModulePendingTask<BitsModule> castedTask = (ModulePendingTask<BitsModule>) task;
                tasks.add(castedTask);
            }
        }
        return tasks;
    }

}
