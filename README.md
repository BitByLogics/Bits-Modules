<div align="center">
## Bit's Modules
[![Build](https://img.shields.io/github/actions/workflow/status/BitByLogics/Bits-Modules/maven.yml?branch=master)](https://github.com/BitByLogics/Bits-Modules/actions)
![Issues](https://img.shields.io/github/issues-raw/BitByLogics/Bits-Modules)
[![Stars](https://img.shields.io/github/stars/BitByLogics/Bits-Modules)](https://github.com/BitByLogics/Bits-Modules/stargazers)

A lightweight module system for Paper plugins. Split a plugin into modules that can be enabled, disabled, reloaded and debugged at runtime.
</div>

## Features
* Module lifecycle: `onRegister`, `onEnable`, `onReload`, `onDisable`, with a rollback if `onEnable` throws.
* Module-to-module dependencies. A module that needs another module waits until it is registered (and is failed with an error if that module fails to register), and circular dependencies are detected and rejected. Disabling a module also disables the modules that depend on it, and enabling it again brings them back.
* Each module gets its own data folder and `config.yml`.
* Commands and listeners registered through a module are added on enable and removed on disable.
* Module-owned scheduler. Tasks are tracked per module and cancelled when the module is disabled.
* Enable, disable and reload events: `ModuleEnableEvent`, `ModuleDisableEvent`, `ModuleReloadEvent`.
* `/module` command for managing modules in game.

## Requirements
* Java 21
* Paper 1.21.11 or Folia
* [Bit's Utils](https://github.com/BitByLogics/Bits-Utils) and [ACF](https://github.com/aikar/commands) (Paper)

## Installation

```xml
<repository>
    <id>minebit</id>
    <url>https://repo.minebit.dev/releases</url>
</repository>

<dependency>
    <groupId>net.bitbylogic</groupId>
    <artifactId>bits-modules</artifactId>
    <version>2.0.0</version>
</dependency>
```

The library is not shaded. Its dependencies (ACF, Bit's Utils, FoliaLib) come in transitively, so your build controls their versions. Shade this library and those dependencies into your plugin, and relocate `co.aikar`, `net.bitbylogic.utils` and `com.tcoded.folialib` to your own package.

## Quick start

Create the manager in your plugin:

```java
public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        PaperCommandManager commandManager = new PaperCommandManager(this);
        DependencyManager dependencyManager = new DependencyManager();

        ModuleManager moduleManager = new ModuleManager(this, commandManager, dependencyManager);
        moduleManager.registerModule(ChatModule.class, EconomyModule.class);
    }

}
```

Create a module. Every module needs a constructor taking `(JavaPlugin, ModuleManager)`:

```java
public class ChatModule extends BitsModule {

    public ChatModule(JavaPlugin plugin, ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onRegister() {
        registerModuleListener(new ChatListener());
        registerCommand(new ChatCommand());
    }

    @Override
    public void onEnable() {
        getScheduler().runTaskTimer("announcements", 20L, 6000L, this::announce);
    }

    @Override
    public void onReload() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public ModuleData getModuleData() {
        return new ModuleData("chat", "Chat", "Handles chat formatting.");
    }

}
```

Module IDs are case-insensitive and must be unique. They must start with a letter or number and may only contain letters, numbers, spaces, `.`, `_` and `-`, because they become folder names. A module's files live in `plugins/<YourPlugin>/<module_id>/`, and a `<module_id>/config.yml` bundled in your jar is copied there on first load.

### Depending on another module

Declare the other module as an injected dependency and it is registered first:

```java
public class ShopModule extends BitsModule {

    @Dependency
    private EconomyModule economyModule;

}
```

`addDependencyTask(EconomyModule.class, economy -> { ... })` runs code as soon as a module that may not be registered yet becomes available.

### Scheduling

`getScheduler()` returns the module's scheduler. Its tasks are tracked and cancelled when the module is disabled.

```java
getScheduler().runTask("id", () -> { });                       // global / main thread
getScheduler().runTaskAsync("id", () -> { });                  // async
getScheduler().runTaskLater("id", 20L, () -> { });
getScheduler().runTaskTimer("id", 0L, 20L, () -> { });

getScheduler().runTaskAtEntity("id", entity, () -> { });       // the entity's region
getScheduler().runTaskAtEntityLater("id", entity, 20L, () -> { });
getScheduler().runTaskAtEntityTimer("id", entity, 0L, 20L, () -> { });

getScheduler().runTaskAtLocation("id", location, () -> { });   // the location's region
getScheduler().runTaskAtLocationLater("id", location, 20L, () -> { });
getScheduler().runTaskAtLocationTimer("id", location, 0L, 20L, () -> { });
```

Use the entity and location variants whenever you touch an entity or block. On Paper they run on the main thread, on Folia they run on the owning region's thread. An entity task is cancelled automatically if the entity is removed. Delays and periods below 1 tick are raised to 1.

Module config is loaded after construction and before `onRegister()`, so use `getConfig()` from `onRegister()` onward, not from your constructor.

## Plugin config

Module state is stored in your plugin's `config.yml`:

```yaml
Disabled-Modules:
  - chat
Debug-Modules:
  - economy
```

## Commands

`/module` (aliases `mdl`, `modules`, `mdls`). Base permission: `bitsmodules.command.module`.

| Command | Permission |
|---|---|
| `/module list [page]` | `bitsmodules.command.module.list` |
| `/module enable <id>` | `bitsmodules.command.module.enable` |
| `/module disable <id>` | `bitsmodules.command.module.disable` |
| `/module toggle <id>` | `bitsmodules.command.module.toggle` |
| `/module reload <id>` | `bitsmodules.command.module.reload` |
| `/module debug <id>` | `bitsmodules.command.module.debug` |
| `/module tasks <id> [page]` | `bitsmodules.command.module.tasks` |

Players with `bitsmodules.debuglog` receive a module's `debugBroadcast` messages while debug is on for that module.

## Notes
* A repeating task that throws is cancelled and the error is logged. Call `getScheduler().setCancelTimersOnError(false)` to keep repeating tasks running after an exception.
* `enableModule`, `disableModule`, `reloadModule` and `registerModule` are serialised, so they are safe to call from any thread, including Folia region threads. Do not block on another thread that is waiting for one of them from inside `onEnable` or `onDisable`.
* A failing `/module reload` is logged and reported to the sender, and the module keeps running.
* Scheduling goes through [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib), so tasks run on Paper and Folia. `folia-supported: true` in your `plugin.yml` is only needed if you actually run on Folia. Paper servers ignore it.

## Contributing and security
See [CONTRIBUTING.md](CONTRIBUTING.md). Report vulnerabilities privately to security@minebit.dev, as described in [SECURITY.md](SECURITY.md).

## License
MIT. See [LICENSE](LICENSE).

## Building

```
mvn clean verify
```

`verify` runs the tests, a JaCoCo coverage report (`target/site/jacoco`) and SpotBugs, and builds the main, sources and Javadoc jars.

## Releasing

Set the version in `pom.xml`, then push a tag named `v<version>` (for example `v2.0.0`). The release workflow checks the tag matches the pom, deploys to the Minebit repository and creates a GitHub release. It needs the repository secrets `MINEBIT_USERNAME` and `MINEBIT_PASSWORD`.
