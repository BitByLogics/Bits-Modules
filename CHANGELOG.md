# Changelog

## 2.0.0

### Breaking
- `BitsModule.setEnabled` is package-private and `BitsModule.setConfig` is removed.
- `BitsModule` no longer loads its configuration in the constructor. It is loaded after construction and before `onRegister()`, so `getConfig()` is `null` inside a module's constructor.
- `ModuleTask` no longer uses Bukkit task IDs. `setTaskId` and the protected `getBukkitRunnable` are removed, and task IDs come from the library.
- `ModuleRunnable.run()` is abstract.
- `ModuleManager.getModulesById()` returns a `Map` instead of a `HashMap`, and the other registries are concurrent maps.
- The library is no longer shaded. Dependencies are transitive.
- `ModuleManager` only exposes read-only views of its registries. `getModulesById()`, `getModulesByClass()`, `getDisabledModules()` and `getDebugModules()` are unmodifiable, and `getPendingModules()`, `getPendingTasksByModule()` and `getCleanupQueue()` are removed. Use `ModuleManager.addDependencyTask` instead of the pending task map.
- `BitsModule.getCommands()`, `getListeners()` and `getConfigurables()` return read-only lists, and `ModuleScheduler.getTasks()` is removed. Use `getTaskSnapshot()` to read tasks and `removeTask()` to drop one.
- Module IDs must start with a letter or number and may only contain letters, numbers, spaces, `.`, `_` and `-`.
- The debug broadcast permission is now `bitsmodules.debuglog` instead of `apibylogic.module.debuglog`.

### Added
- Folia support through FoliaLib. Scheduling goes through its scheduler.
- Entity and location scheduling: `runTaskAtEntity`, `runTaskAtEntityLater`, `runTaskAtEntityTimer`, `runTaskAtLocation`, `runTaskAtLocationLater`, `runTaskAtLocationTimer`.
- Circular module dependencies are detected and rejected.
- A module that fails to register (exception in `onRegister`, invalid or duplicate ID, circular dependency) now fails every module waiting on it, directly or transitively, with a SEVERE log naming the broken dependency.
- Disabling a module disables the modules that depend on it, and they are re-enabled with it.
- `ModuleManager.reloadModule`, which logs failures instead of throwing, and a `Reload-Failed` message.
- `ModuleScheduler.getTaskSnapshot()` and `REJECTED_TASK_ID`.
- MIT license, unit tests, a GitHub Actions build and a real README.

- `ModuleScheduler.setCancelTimersOnError` to keep repeating tasks running after an exception.
- `/module list` is sorted by ID and says so when there are no modules. The help text lists `debug` and `tasks`.
- Lifecycle calls (`registerModule`, `enableModule`, `disableModule`, `reloadModule`, `addDependencyTask`) are serialised.
- Sources and Javadoc jars, JaCoCo coverage, SpotBugs analysis, Dependabot, a dependency review check on pull requests and a tag-driven release workflow.

### Fixed
- A task scheduled at the moment its module was being disabled could keep running. It is now rejected.
- `Debug-Modules` matched module IDs case-sensitively in one place and case-insensitively in another, which could duplicate entries.
- `addDependencyTask` could miss a module that finished registering at the same moment.
- Unchecked `mkdirs()` results are now reported.
- Warnings were logged twice.
- Tasks that failed to schedule stayed listed as active.
- `enabled`, `debug` and `config` are now volatile.
- Config values of the wrong type fall back to the default instead of failing silently.
- Exceptions are logged through the plugin logger instead of `printStackTrace`.
