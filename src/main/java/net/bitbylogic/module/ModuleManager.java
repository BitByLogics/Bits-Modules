package net.bitbylogic.module;

import co.aikar.commands.PaperCommandManager;
import com.tcoded.folialib.FoliaLib;
import lombok.Getter;
import lombok.NonNull;
import net.bitbylogic.module.command.ModulesCommand;
import net.bitbylogic.module.event.ModuleDisableEvent;
import net.bitbylogic.module.event.ModuleEnableEvent;
import net.bitbylogic.module.event.ModuleReloadEvent;
import net.bitbylogic.module.message.ModuleMessages;
import net.bitbylogic.module.scheduler.ModuleTask;
import net.bitbylogic.module.task.ModulePendingTask;
import net.bitbylogic.utils.color.ColorUtil;
import net.bitbylogic.utils.dependency.DependencyManager;
import net.bitbylogic.utils.message.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class ModuleManager {

    private static final Pattern MODULE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._-]*");

    @Getter
    private final JavaPlugin plugin;

    @Getter
    private final DependencyManager dependencyManager;

    @Getter
    private final FoliaLib foliaLib;

    @Getter
    private final PaperCommandManager commandManager;

    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private final List<String> disabledModules;
    private final List<String> debugModules;

    private final Map<Class<? extends BitsModule>, BitsModule> modulesByClass = new ConcurrentHashMap<>();
    private final Map<String, BitsModule> modulesById = new ConcurrentHashMap<>();

    private final Map<Class<?>, List<Class<?>>> pendingModules = new ConcurrentHashMap<>();

    private final Set<ModuleTask> cleanupQueue = ConcurrentHashMap.newKeySet();

    private final Set<String> cascadeDisabledModules = ConcurrentHashMap.newKeySet();

    private final Map<Class<? extends BitsModule>, List<ModulePendingTask<? extends BitsModule>>> pendingTasksByModule = new ConcurrentHashMap<>();

    public ModuleManager(@NotNull JavaPlugin plugin, @NotNull PaperCommandManager commandManager, @NotNull DependencyManager dependencyManager) {
        this(plugin, commandManager, dependencyManager, true);
    }

    public ModuleManager(@NotNull JavaPlugin plugin, @NotNull PaperCommandManager commandManager, @NotNull DependencyManager dependencyManager, boolean registerCommand) {
        this(plugin, commandManager, dependencyManager, registerCommand, new FoliaLib(plugin));
    }

    ModuleManager(@NotNull JavaPlugin plugin, @NotNull PaperCommandManager commandManager, @NotNull DependencyManager dependencyManager,
                  boolean registerCommand, @NotNull FoliaLib foliaLib) {
        this.plugin = plugin;
        this.foliaLib = foliaLib;

        this.commandManager = commandManager;
        this.dependencyManager = dependencyManager;

        Messages.registerGroup(new ModuleMessages());

        this.disabledModules = plugin.getConfig().getStringList("Disabled-Modules");
        this.debugModules = plugin.getConfig().getStringList("Debug-Modules");

        commandManager.registerDependency(getClass(), this);
        dependencyManager.registerDependency(getClass(), this);

        if (registerCommand) {
            ModulesCommand modulesCommand = new ModulesCommand();
            dependencyManager.injectDependencies(modulesCommand, true);
            commandManager.registerCommand(modulesCommand);
        }

        foliaLib.getScheduler().runTimer(() -> {
            Iterator<ModuleTask> iterator = cleanupQueue.iterator();

            while (iterator.hasNext()) {
                ModuleTask task = iterator.next();
                BitsModule module = task.getModuleInstance();

                if (module != null) {
                    module.getScheduler().removeTask(task);
                }

                iterator.remove();
            }
        }, 20L * 30L, 20L * 30L);
    }

    /**
     * Register a Module.
     *
     * @param classes The classes to register.
     */
    @SafeVarargs
    public final void registerModule(Class<? extends BitsModule>... classes) {
        lifecycleLock.lock();

        try {
            registerModuleClasses(classes);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void registerModuleClasses(Class<? extends BitsModule>[] classes) {
        for (Class<? extends BitsModule> moduleClass : classes) {
            if (modulesByClass.containsKey(moduleClass)) {
                plugin.getLogger().log(Level.WARNING,
                        ColorUtil.colorForConsole("&8[&9Module Manager&8] &cModule '&4" + moduleClass.getName() + "&c' is already registered."));
                continue;
            }

            try {
                BitsModule module = moduleClass.getDeclaredConstructor(JavaPlugin.class, ModuleManager.class).newInstance(plugin, this);
                List<String> missingModules = new ArrayList<>();
                List<Class<? extends BitsModule>> missingDependencies = new ArrayList<>();
                Class<?> circularDependency = null;

                for (Class<?> dependency : dependencyManager.getDependencies(module, true)) {
                    if (!BitsModule.class.isAssignableFrom(dependency) || dependencyManager.isDependencyRegistered(dependency)) {
                        continue;
                    }

                    if (hasDependencyCycle(pendingModules, moduleClass, dependency)) {
                        circularDependency = dependency;
                        break;
                    }

                    missingDependencies.add(dependency.asSubclass(BitsModule.class));
                    missingModules.add(dependency.getSimpleName());
                }

                if (circularDependency != null) {
                    plugin.getLogger().log(Level.SEVERE,
                            ColorUtil.colorForConsole("&8[&9Module Manager&8] &cModule '&4" + moduleClass.getName()
                                    + "&c' has a circular dependency on '&4" + circularDependency.getName() + "&c' and will not be registered."));
                    failPendingModules(moduleClass);
                    continue;
                }

                for (Class<? extends BitsModule> dependencyClass : missingDependencies) {
                    List<Class<?>> dependentModules = pendingModules.computeIfAbsent(dependencyClass, key -> new CopyOnWriteArrayList<>());

                    if (!dependentModules.contains(moduleClass)) {
                        dependentModules.add(moduleClass);
                    }
                }

                if (!missingModules.isEmpty()) {
                    plugin.getLogger().log(Level.WARNING,
                            ColorUtil.colorForConsole("&8[&9Module Manager&8] &eWaiting to register module '&6" + moduleClass.getName() + "&e'; it requires:"));
                    plugin.getLogger().log(Level.WARNING,
                            ColorUtil.colorForConsole("&8[&9Module Manager&8] &e" + String.join(", ", missingModules)));
                    continue;
                }

                registerModuleData(module);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                plugin.getLogger().log(Level.SEVERE,
                        ColorUtil.colorForConsole("&8[&9Module Manager&8] &cCouldn't create new instance of module class '&4" + moduleClass.getName() + "&c'"), e);
                failPendingModules(moduleClass);
            }
        }
    }

    private void registerModuleData(@NonNull BitsModule module) {
        Class<? extends BitsModule> moduleClass = module.getClass();
        ModuleData moduleData = module.getModuleData();
        long startTime = System.nanoTime();

        if (moduleData == null || moduleData.id() == null || !isValidModuleId(moduleData.id())) {
            plugin.getLogger().log(Level.SEVERE,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &cModule '&4" + moduleClass.getName()
                            + "&c' has an invalid ID. IDs must start with a letter or number and may only contain letters, numbers, spaces, '.', '_' and '-'."));
            failPendingModules(moduleClass);
            return;
        }

        String normalizedId = normalizeModuleId(moduleData.id());
        BitsModule existingModule = modulesById.get(normalizedId);

        if (existingModule != null) {
            plugin.getLogger().log(Level.SEVERE,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &cModule ID '&4" + moduleData.id()
                            + "&c' is already used by '&4" + existingModule.getClass().getName() + "&c'."));
            failPendingModules(moduleClass);
            return;
        }

        modulesByClass.put(moduleClass, module);
        modulesById.put(normalizedId, module);

        boolean commandDependencyRegistered = false;

        try {
            commandManager.registerDependency(moduleClass, module);
            commandDependencyRegistered = true;
            dependencyManager.injectDependencies(module, true);
            module.initialize();

            if (containsModuleId(debugModules, moduleData.id())) {
                module.setDebug(true);
            }

            module.onRegister();
            module.getCommands().forEach(command -> dependencyManager.injectDependencies(command, true));
            dependencyManager.registerDependency(moduleClass, module);
        } catch (RuntimeException exception) {
            modulesByClass.remove(moduleClass, module);
            modulesById.remove(normalizedId, module);
            dependencyManager.getDependencies().remove(moduleClass, module);

            if (commandDependencyRegistered) {
                commandManager.unregisterDependency(moduleClass);
            }

            plugin.getLogger().log(Level.SEVERE,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &cFailed to register module '&4" + moduleClass.getName() + "&c'."),
                    exception);
            failPendingModules(moduleClass);
            return;
        }

        if (!containsModuleId(disabledModules, moduleData.id())) {
            enableModule(module, false, false);
        }

        pendingTasksByModule
                .getOrDefault(moduleClass, Collections.emptyList())
                .forEach(task -> {
                    @SuppressWarnings("unchecked")
                    ModulePendingTask<BitsModule> castedTask = (ModulePendingTask<BitsModule>) task;

                    try {
                        castedTask.accept(module);
                    } catch (RuntimeException exception) {
                        plugin.getLogger().log(Level.SEVERE,
                                ColorUtil.colorForConsole("&8[&9Module Manager&8] &cFailed to run a pending task for module '&4"
                                        + moduleClass.getName() + "&c'."), exception);
                    }
                });

        pendingTasksByModule.remove(moduleClass);
        registerPendingModules(moduleClass);

        long endTime = System.nanoTime();
        plugin.getLogger().log(Level.INFO,
                ColorUtil.colorForConsole("&8[&9" + moduleData.name() + "&8] &2Successfully registered in &a" + (endTime - startTime) / 1000000d + "&2ms"));
    }

    /**
     * Check whether making {@code module} wait on {@code dependency} would create a circular wait.
     *
     * @param pendingModules Waiting modules, keyed by the dependency they are waiting for.
     * @param module         The module that is about to wait.
     * @param dependency     The dependency the module would wait for.
     * @return {@code true} if the dependency is the module itself, or already waits on it directly or indirectly.
     */
    static boolean hasDependencyCycle(Map<Class<?>, List<Class<?>>> pendingModules, Class<?> module, Class<?> dependency) {
        if (module.equals(dependency)) {
            return true;
        }

        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(module);

        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();

            if (!visited.add(current)) {
                continue;
            }

            for (Class<?> waiting : pendingModules.getOrDefault(current, Collections.emptyList())) {
                if (waiting.equals(dependency)) {
                    return true;
                }

                queue.add(waiting);
            }
        }

        return false;
    }

    static boolean isValidModuleId(String moduleId) {
        return MODULE_ID_PATTERN.matcher(moduleId).matches() && !moduleId.contains("..");
    }

    public <T extends BitsModule> void addDependencyTask(@NonNull Class<T> dependency, @NonNull Consumer<T> consumer) {
        lifecycleLock.lock();

        try {
            Object existingModule = dependencyManager.getDependencies().get(dependency);

            if (existingModule != null) {
                consumer.accept(dependency.cast(existingModule));
                return;
            }

            pendingTasksByModule
                    .computeIfAbsent(dependency, key -> new CopyOnWriteArrayList<>())
                    .add(new ModulePendingTask<T>(dependency) {
                        @Override
                        public void accept(T module) {
                            consumer.accept(module);
                        }
                    });
        } finally {
            lifecycleLock.unlock();
        }
    }

    public Map<String, BitsModule> getModulesById() {
        return Collections.unmodifiableMap(modulesById);
    }

    public Map<Class<? extends BitsModule>, BitsModule> getModulesByClass() {
        return Collections.unmodifiableMap(modulesByClass);
    }

    public List<String> getDisabledModules() {
        return List.copyOf(disabledModules);
    }

    public List<String> getDebugModules() {
        return List.copyOf(debugModules);
    }

    private void failPendingModules(Class<?> failedClass) {
        List<Class<?>> waitingClasses = pendingModules.remove(failedClass);

        if (waitingClasses == null) {
            return;
        }

        for (Class<?> waitingClass : waitingClasses) {
            plugin.getLogger().log(Level.SEVERE,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &cModule '&4" + waitingClass.getName()
                            + "&c' will not be registered because its dependency '&4" + failedClass.getName() + "&c' failed to register."));

            pendingModules.values().forEach(classes -> classes.remove(waitingClass));
            pendingModules.values().removeIf(List::isEmpty);
            failPendingModules(waitingClass);
        }
    }

    private void registerPendingModules(Class<? extends BitsModule> dependencyClass) {
        List<Class<?>> pendingModuleClasses = pendingModules.remove(dependencyClass);

        if (pendingModuleClasses == null) {
            return;
        }

        for (Class<?> pendingModuleClass : pendingModuleClasses) {
            if (!BitsModule.class.isAssignableFrom(pendingModuleClass)) {
                continue;
            }

            Class<? extends BitsModule> moduleClass = pendingModuleClass.asSubclass(BitsModule.class);
            registerModule(moduleClass);
        }
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
        return Optional.ofNullable(modulesByClass.get(clazz)).map(clazz::cast);
    }

    /**
     * Enable a Module.
     *
     * @param moduleID The Module's ID.
     */
    public void enableModule(String moduleID) {
        lifecycleLock.lock();

        try {
            enableModuleById(moduleID);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void enableModuleById(String moduleID) {
        Optional<BitsModule> optionalModule = getModuleByID(moduleID);

        if (optionalModule.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, ColorUtil.colorForConsole("&8[&9Module Manager&8] &cInvalid Module ID '&4" + moduleID + "&c'."));
            return;
        }

        BitsModule module = optionalModule.get();

        if (module.isEnabled()) {
            return;
        }

        enableModule(module, true, true);
    }

    private boolean enableModule(BitsModule module, boolean reloadConfiguration, boolean persistConfiguration) {
        String moduleId = module.getModuleData().id();
        List<String> disabledDependencies = getModuleDependencies(module).stream()
                .filter(dependency -> !dependency.isEnabled())
                .map(dependency -> dependency.getModuleData().id())
                .toList();

        if (!disabledDependencies.isEmpty()) {
            plugin.getLogger().log(Level.WARNING,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &eCannot enable module '&6" + moduleId
                            + "&e' because it requires disabled module(s): " + String.join(", ", disabledDependencies)));

            if (!containsModuleId(disabledModules, moduleId)) {
                cascadeDisabledModules.add(normalizeModuleId(moduleId));
            }

            return false;
        }

        if (reloadConfiguration) {
            try {
                module.reloadConfig();
                module.loadConfigPaths();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        ColorUtil.colorForConsole("&8[&9Module Manager&8] &cFailed to reload configuration while enabling module '&4"
                                + module.getModuleData().id() + "&c'."), exception);
                return false;
            }
        }

        List<ModuleCommand<?>> commandsBeforeEnable = new ArrayList<>(module.getCommands());
        List<Listener> listenersBeforeEnable = new ArrayList<>(module.getListeners());

        try {
            module.setEnabled(true);
            module.onEnable();
            module.getCommands().forEach(commandManager::registerCommand);
            listenersBeforeEnable.forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, plugin));
            Bukkit.getPluginManager().registerEvents(module, plugin);
            Bukkit.getPluginManager().callEvent(new ModuleEnableEvent(module));
        } catch (RuntimeException exception) {
            rollbackFailedEnable(module, commandsBeforeEnable, listenersBeforeEnable, exception);
            return false;
        }

        if (persistConfiguration) {
            disabledModules.removeIf(id -> id.equalsIgnoreCase(moduleId));
            saveDisabledModules();
        }

        cascadeDisabledModules.remove(normalizeModuleId(moduleId));
        reenableDependents(module);
        return true;
    }

    private void reenableDependents(BitsModule module) {
        for (BitsModule dependent : getDependentModules(module)) {
            if (dependent.isEnabled() || !cascadeDisabledModules.contains(normalizeModuleId(dependent.getModuleData().id()))) {
                continue;
            }

            plugin.getLogger().log(Level.INFO,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &2Re-enabling module '&a" + dependent.getModuleData().id()
                            + "&2' because '&a" + module.getModuleData().id() + "&2' is enabled again."));
            enableModule(dependent, true, false);
        }
    }

    private List<BitsModule> getModuleDependencies(BitsModule module) {
        List<BitsModule> dependencies = new ArrayList<>();

        for (Class<?> dependencyClass : dependencyManager.getDependencies(module, true)) {
            BitsModule dependency = modulesByClass.get(dependencyClass);

            if (dependency == null || dependency == module) {
                continue;
            }

            dependencies.add(dependency);
        }

        return dependencies;
    }

    private List<BitsModule> getDependentModules(BitsModule module) {
        List<BitsModule> dependents = new ArrayList<>();

        for (BitsModule other : modulesByClass.values()) {
            if (other == module || !getModuleDependencies(other).contains(module)) {
                continue;
            }

            dependents.add(other);
        }

        return dependents;
    }

    /**
     * Reload a Module's configuration and invoke its reload hook.
     *
     * @param module The module to reload.
     * @return {@code true} if the module reloaded without throwing.
     */
    public boolean reloadModule(@NonNull BitsModule module) {
        lifecycleLock.lock();

        try {
            return reloadModuleLocked(module);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private boolean reloadModuleLocked(BitsModule module) {
        try {
            module.reloadConfig();
            module.loadConfigPaths();
            module.onReload();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &cFailed to reload module '&4"
                            + module.getModuleData().id() + "&c'."), exception);
            return false;
        }

        Bukkit.getPluginManager().callEvent(new ModuleReloadEvent(module));
        return true;
    }

    private void rollbackFailedEnable(BitsModule module, List<ModuleCommand<?>> commandsBeforeEnable,
                                      List<Listener> listenersBeforeEnable, RuntimeException enableException) {
        module.setEnabled(false);

        try {
            module.onDisable();
        } catch (RuntimeException disableException) {
            enableException.addSuppressed(disableException);
        } finally {
            cleanupModuleResources(module);
            module.restoreRegistrations(commandsBeforeEnable, listenersBeforeEnable);
        }

        plugin.getLogger().log(Level.SEVERE,
                ColorUtil.colorForConsole("&8[&9Module Manager&8] &cFailed to enable module '&4"
                        + module.getModuleData().id() + "&c'."), enableException);
    }

    /**
     * Disable a Module.
     *
     * @param moduleID The Module's ID.
     */
    public void disableModule(String moduleID) {
        lifecycleLock.lock();

        try {
            disableModuleById(moduleID);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void disableModuleById(String moduleID) {
        Optional<BitsModule> optionalModule = getModuleByID(moduleID);

        if (optionalModule.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, ColorUtil.colorForConsole("&8[&9Module Manager&8] &cInvalid Module ID '&4" + moduleID + "&c'."));
            return;
        }

        BitsModule module = optionalModule.get();
        boolean cascadeDisabled = cascadeDisabledModules.remove(normalizeModuleId(module.getModuleData().id()));

        if (!module.isEnabled()) {
            if (cascadeDisabled) {
                persistDisabled(module);
            }

            return;
        }

        disableModule(module, true);
    }

    private void disableModule(BitsModule module, boolean persistConfiguration) {
        for (BitsModule dependent : getDependentModules(module)) {
            if (!dependent.isEnabled()) {
                continue;
            }

            plugin.getLogger().log(Level.WARNING,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &eDisabling module '&6" + dependent.getModuleData().id()
                            + "&e' because it requires '&6" + module.getModuleData().id() + "&e'."));
            cascadeDisabledModules.add(normalizeModuleId(dependent.getModuleData().id()));
            disableModule(dependent, false);
        }

        module.setEnabled(false);

        try {
            module.onDisable();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &cModule '&4" + module.getModuleData().id()
                            + "&c' threw an exception while disabling."), exception);
        } finally {
            cleanupModuleResources(module);
        }

        if (persistConfiguration) {
            persistDisabled(module);
        }

        ModuleDisableEvent disableEvent = new ModuleDisableEvent(module);
        Bukkit.getPluginManager().callEvent(disableEvent);
    }

    private void cleanupModuleResources(BitsModule module) {
        List<ModuleTask> tasks = module.getScheduler().getTaskSnapshot();

        tasks.forEach(task -> cleanupResource(module, "task '" + task.getId() + "'", task::cancel));
        module.getListeners().forEach(listener -> cleanupResource(module, "listener '" + listener.getClass().getName() + "'",
                () -> HandlerList.unregisterAll(listener)));
        module.getCommands().forEach(command -> cleanupResource(module, "command '" + command.getClass().getName() + "'",
                () -> commandManager.unregisterCommand(command)));
        cleanupResource(module, "module listener", () -> HandlerList.unregisterAll(module));
    }

    private void cleanupResource(BitsModule module, String resourceName, Runnable cleanupAction) {
        try {
            cleanupAction.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    ColorUtil.colorForConsole("&8[&9Module Manager&8] &eFailed to clean up " + resourceName
                            + " for module '&6" + module.getModuleData().id() + "&e'."), exception);
        }
    }

    private void persistDisabled(BitsModule module) {
        if (!containsModuleId(disabledModules, module.getModuleData().id())) {
            disabledModules.add(module.getModuleData().id());
        }

        saveDisabledModules();
    }

    private void saveDisabledModules() {
        plugin.getConfig().set("Disabled-Modules", disabledModules);
        plugin.saveConfig();
    }

    private boolean containsModuleId(Collection<String> moduleIds, String moduleId) {
        return moduleIds.stream().anyMatch(moduleId::equalsIgnoreCase);
    }

    private String normalizeModuleId(String moduleId) {
        return moduleId.toLowerCase(Locale.ROOT);
    }

    public Optional<BitsModule> getModuleByID(@NonNull String id) {
        return Optional.ofNullable(modulesById.get(normalizeModuleId(id)));
    }

    public void scheduleCleanup(@NonNull ModuleTask task) {
        cleanupQueue.add(task);
    }

}
