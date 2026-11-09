package net.bitbylogic.module;

import co.aikar.commands.PaperCommandManager;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import net.bitbylogic.utils.dependency.DependencyManager;
import net.bitbylogic.utils.dependency.annotation.Dependency;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleManagerLifecycleTest {

    static class EconomyModule extends BitsModule {

        boolean reloadFails = false;

        EconomyModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onReload() {
            if (reloadFails) {
                throw new IllegalStateException("reload failed");
            }
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("economy", "Economy", "Economy module");
        }

    }

    static class ShopModule extends BitsModule {

        @Dependency
        private EconomyModule economyModule;

        ShopModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onReload() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("shop", "Shop", "Shop module");
        }

    }

    static class FailingModule extends BitsModule {

        FailingModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
        }

        @Override
        public void onEnable() {
            throw new IllegalStateException("enable failed");
        }

        @Override
        public void onReload() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("failing", "Failing", "Fails to enable");
        }

    }

    static class FirstCircularModule extends BitsModule {

        @Dependency
        private SecondCircularModule other;

        FirstCircularModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onReload() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("first", "First", "Circular");
        }

    }

    static class SecondCircularModule extends BitsModule {

        @Dependency
        private FirstCircularModule other;

        SecondCircularModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onReload() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("second", "Second", "Circular");
        }

    }

    static class BrokenModule extends BitsModule {

        BrokenModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
            throw new IllegalStateException("register failed");
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onReload() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("broken", "Broken", "Fails to register");
        }

    }

    static class NeedsBrokenModule extends BitsModule {

        @Dependency
        private BrokenModule broken;

        NeedsBrokenModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onReload() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("needs_broken", "Needs Broken", "Depends on a broken module");
        }

    }

    static class NeedsNeedsBrokenModule extends BitsModule {

        @Dependency
        private NeedsBrokenModule needsBroken;

        NeedsNeedsBrokenModule(JavaPlugin plugin, ModuleManager moduleManager) {
            super(plugin, moduleManager);
        }

        @Override
        public void onRegister() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onReload() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public ModuleData getModuleData() {
            return new ModuleData("needs_needs_broken", "Needs Needs Broken", "Transitively depends on a broken module");
        }

    }

    @TempDir
    File dataFolder;

    private final List<String> severeMessages = new ArrayList<>();

    private MockedStatic<Bukkit> bukkit;
    private YamlConfiguration pluginConfig;
    private ModuleManager manager;

    @BeforeEach
    void setUp() {
        bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

        pluginConfig = new YamlConfiguration();

        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.SEVERE) {
                    severeMessages.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(pluginConfig);

        FoliaLib foliaLib = mock(FoliaLib.class);
        when(foliaLib.getScheduler()).thenReturn(mock(PlatformScheduler.class));

        manager = new ModuleManager(plugin, mock(PaperCommandManager.class), new DependencyManager(), false, foliaLib);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void moduleIsEnabledOnRegister() {
        manager.registerModule(EconomyModule.class);

        assertTrue(manager.getModuleByID("economy").orElseThrow().isEnabled());
    }

    @Test
    void dependentRegistersOnceItsDependencyIsRegistered() {
        manager.registerModule(ShopModule.class);

        assertTrue(manager.getModuleByID("shop").isEmpty());

        manager.registerModule(EconomyModule.class);

        assertTrue(manager.getModuleByID("shop").orElseThrow().isEnabled());
        assertTrue(manager.getModuleByID("economy").orElseThrow().isEnabled());
    }

    @Test
    void circularModulesAreNotRegistered() {
        manager.registerModule(FirstCircularModule.class, SecondCircularModule.class);

        assertTrue(manager.getModuleByID("first").isEmpty());
        assertTrue(manager.getModuleByID("second").isEmpty());
    }

    @Test
    void dependentsOfAModuleThatFailsToRegisterAreFailedLoudly() {
        manager.registerModule(NeedsBrokenModule.class, NeedsNeedsBrokenModule.class);

        assertTrue(manager.getModuleByID("needs_broken").isEmpty());

        manager.registerModule(BrokenModule.class);

        assertTrue(manager.getModuleByID("broken").isEmpty());
        assertTrue(manager.getModuleByID("needs_broken").isEmpty());
        assertTrue(manager.getModuleByID("needs_needs_broken").isEmpty());
        assertTrue(severeMessages.stream().anyMatch(message -> message.contains("NeedsBrokenModule") && message.contains("BrokenModule")));
        assertTrue(severeMessages.stream().anyMatch(message -> message.contains("NeedsNeedsBrokenModule") && message.contains("NeedsBrokenModule")));
    }

    @Test
    void dependencyTaskRunsOnceTheModuleIsRegistered() {
        AtomicReference<EconomyModule> received = new AtomicReference<>();

        manager.addDependencyTask(EconomyModule.class, received::set);
        assertNull(received.get());

        manager.registerModule(EconomyModule.class);

        assertNotNull(received.get());
    }

    @Test
    void dependencyTaskRunsImmediatelyForRegisteredModule() {
        AtomicReference<EconomyModule> received = new AtomicReference<>();
        manager.registerModule(EconomyModule.class);

        manager.addDependencyTask(EconomyModule.class, received::set);

        assertNotNull(received.get());
    }

    @Test
    void registryViewsAreReadOnly() {
        manager.registerModule(EconomyModule.class);

        assertThrows(UnsupportedOperationException.class, () -> manager.getModulesById().clear());
        assertThrows(UnsupportedOperationException.class, () -> manager.getModulesByClass().clear());
        assertThrows(UnsupportedOperationException.class, () -> manager.getDisabledModules().add("x"));
    }

    @Test
    void failedEnableRollsTheModuleBack() {
        manager.registerModule(FailingModule.class);

        BitsModule module = manager.getModuleByID("failing").orElseThrow();

        assertFalse(module.isEnabled());
        assertFalse(pluginConfig.getStringList("Disabled-Modules").contains("failing"));
    }

    @Test
    void disablingADependencyDisablesItsDependentsWithoutPersistingThem() {
        manager.registerModule(EconomyModule.class, ShopModule.class);

        manager.disableModule("economy");

        assertFalse(manager.getModuleByID("economy").orElseThrow().isEnabled());
        assertFalse(manager.getModuleByID("shop").orElseThrow().isEnabled());
        assertTrue(pluginConfig.getStringList("Disabled-Modules").contains("economy"));
        assertFalse(pluginConfig.getStringList("Disabled-Modules").contains("shop"));
    }

    @Test
    void enablingADependencyBringsBackCascadeDisabledDependents() {
        manager.registerModule(EconomyModule.class, ShopModule.class);
        manager.disableModule("economy");

        manager.enableModule("economy");

        assertTrue(manager.getModuleByID("economy").orElseThrow().isEnabled());
        assertTrue(manager.getModuleByID("shop").orElseThrow().isEnabled());
    }

    @Test
    void dependentCannotBeEnabledWhileItsDependencyIsDisabled() {
        manager.registerModule(EconomyModule.class, ShopModule.class);
        manager.disableModule("economy");

        manager.enableModule("shop");

        assertFalse(manager.getModuleByID("shop").orElseThrow().isEnabled());
    }

    @Test
    void explicitlyDisabledDependentStaysDisabledWhenDependencyReturns() {
        manager.registerModule(EconomyModule.class, ShopModule.class);
        manager.disableModule("economy");
        manager.disableModule("shop");

        manager.enableModule("economy");

        assertTrue(manager.getModuleByID("economy").orElseThrow().isEnabled());
        assertFalse(manager.getModuleByID("shop").orElseThrow().isEnabled());
        assertTrue(pluginConfig.getStringList("Disabled-Modules").contains("shop"));
    }

    @Test
    void reloadReportsFailureInsteadOfThrowing() {
        manager.registerModule(EconomyModule.class);

        EconomyModule economy = manager.getModuleInstance(EconomyModule.class).orElseThrow();
        economy.reloadFails = true;

        assertFalse(manager.reloadModule(economy));
        assertTrue(economy.isEnabled());
    }

    @Test
    void reloadSucceedsForHealthyModule() {
        manager.registerModule(EconomyModule.class);

        assertTrue(manager.reloadModule(manager.getModuleInstance(EconomyModule.class).orElseThrow()));
    }

}
