package net.bitbylogic.module;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.entity.Entity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitsModuleTest {

    private static class TestModule extends BitsModule {

        TestModule(JavaPlugin plugin, ModuleManager moduleManager) {
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
            return new ModuleData("test", "Test", "Test module");
        }

    }

    @TempDir
    File dataFolder;

    private Logger logger;
    private YamlConfiguration pluginConfig;
    private ModuleManager moduleManager;
    private PlatformScheduler platformScheduler;
    private TestModule module;

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);

        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(logger);
        pluginConfig = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(pluginConfig);

        platformScheduler = mock(PlatformScheduler.class);

        FoliaLib foliaLib = mock(FoliaLib.class);
        when(foliaLib.getScheduler()).thenReturn(platformScheduler);

        moduleManager = mock(ModuleManager.class);
        when(moduleManager.getFoliaLib()).thenReturn(foliaLib);

        module = new TestModule(plugin, moduleManager);
        module.initialize();
    }

    @Test
    void warningIsLoggedOnceAsWarningOnly() {
        module.log(Level.WARNING, "careful");

        verify(logger).warning(anyString());
        verify(logger, never()).info(anyString());
    }

    @Test
    void infoIsLoggedOnceAsInfoOnly() {
        module.log(Level.INFO, "hello");

        verify(logger).info(anyString());
        verify(logger, never()).warning(anyString());
    }

    @Test
    void debugWarningIsLoggedOnceWhenDebugging() {
        module.setDebug(true);
        module.debug(Level.WARNING, "careful");

        verify(logger).warning(anyString());
        verify(logger, never()).info(anyString());
    }

    @Test
    void newModuleIsDisabled() {
        assertFalse(module.isEnabled());
    }

    @Test
    void schedulerRejectsTasksWhileModuleIsDisabled() {
        int taskId = module.getScheduler().runTask("task", () -> {
        });

        assertEquals(net.bitbylogic.module.scheduler.ModuleScheduler.REJECTED_TASK_ID, taskId);
        assertTrue(module.getScheduler().getTaskSnapshot().isEmpty());
    }

    @Test
    void timerIsScheduledThroughFoliaLibAndCancelled() {
        WrappedTask wrappedTask = mock(WrappedTask.class);
        when(platformScheduler.runTimer(any(Runnable.class), anyLong(), anyLong())).thenReturn(wrappedTask);
        module.setEnabled(true);

        int taskId = module.getScheduler().runTaskTimer("timer", 0L, 20L, () -> {
        });

        assertTrue(taskId > 0);
        verify(platformScheduler).runTimer(any(Runnable.class), eq(1L), eq(20L));
        assertEquals(1, module.getScheduler().getTaskSnapshot().size());

        module.getScheduler().cancelTask("timer");

        verify(wrappedTask).cancel();
        assertTrue(module.getScheduler().getTaskSnapshot().isEmpty());
    }

    @Test
    void entityTimerIsScheduledOnEntitySchedulerAndRetiredCancelsTask() {
        WrappedTask wrappedTask = mock(WrappedTask.class);
        Entity entity = mock(Entity.class);
        ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
        when(platformScheduler.runAtEntityTimer(eq(entity), any(Runnable.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(wrappedTask);
        module.setEnabled(true);

        module.getScheduler().runTaskAtEntityTimer("entity", entity, 0L, 10L, () -> {
        });

        verify(platformScheduler).runAtEntityTimer(eq(entity), any(Runnable.class), retired.capture(), eq(1L), eq(10L));
        assertEquals(1, module.getScheduler().getTaskSnapshot().size());

        retired.getValue().run();

        verify(wrappedTask).cancel();
        assertFalse(module.getScheduler().getTaskSnapshot().get(0).isActive());
    }

    @Test
    void failingTimerIsCancelledByDefault() {
        WrappedTask wrappedTask = mock(WrappedTask.class);
        ArgumentCaptor<Runnable> executable = ArgumentCaptor.forClass(Runnable.class);
        when(platformScheduler.runTimer(any(Runnable.class), anyLong(), anyLong())).thenReturn(wrappedTask);
        module.setEnabled(true);

        module.getScheduler().runTaskTimer("failing", 1L, 1L, () -> {
            throw new IllegalStateException("boom");
        });

        verify(platformScheduler).runTimer(executable.capture(), eq(1L), eq(1L));
        executable.getValue().run();

        verify(wrappedTask).cancel();
        assertFalse(module.getScheduler().getTaskSnapshot().get(0).isActive());
    }

    @Test
    void failingTimerKeepsRunningWhenConfigured() {
        WrappedTask wrappedTask = mock(WrappedTask.class);
        ArgumentCaptor<Runnable> executable = ArgumentCaptor.forClass(Runnable.class);
        when(platformScheduler.runTimer(any(Runnable.class), anyLong(), anyLong())).thenReturn(wrappedTask);
        module.setEnabled(true);
        module.getScheduler().setCancelTimersOnError(false);

        module.getScheduler().runTaskTimer("failing", 1L, 1L, () -> {
            throw new IllegalStateException("boom");
        });

        verify(platformScheduler).runTimer(executable.capture(), eq(1L), eq(1L));
        executable.getValue().run();

        verify(wrappedTask, never()).cancel();
        assertTrue(module.getScheduler().getTaskSnapshot().get(0).isActive());
    }

    @Test
    void cancelledTaskDoesNotRun() {
        ArgumentCaptor<Runnable> executable = ArgumentCaptor.forClass(Runnable.class);
        AtomicBoolean ran = new AtomicBoolean(false);
        module.setEnabled(true);

        module.getScheduler().runTaskLater("later", 20L, () -> ran.set(true));
        verify(platformScheduler).runLater(executable.capture(), eq(20L));

        module.getScheduler().cancelTask("later");
        executable.getValue().run();

        assertFalse(ran.get());
    }

    @Test
    void schedulingFailureLeavesNoOrphanedTask() {
        when(platformScheduler.runTimer(any(Runnable.class), anyLong(), anyLong())).thenThrow(new IllegalStateException("no scheduler"));
        module.setEnabled(true);

        int taskId = module.getScheduler().runTaskTimer("broken", 1L, 1L, () -> {
        });

        assertEquals(net.bitbylogic.module.scheduler.ModuleScheduler.REJECTED_TASK_ID, taskId);
        assertTrue(module.getScheduler().getTaskSnapshot().isEmpty());
    }

    @Test
    void debugListMatchesModuleIdIgnoringCase() {
        pluginConfig.set("Debug-Modules", List.of("TEST"));

        module.setDebug(true);
        assertEquals(List.of("TEST"), pluginConfig.getStringList("Debug-Modules"));

        module.setDebug(false);
        assertTrue(pluginConfig.getStringList("Debug-Modules").isEmpty());
    }

    @Test
    void taskScheduledWhileTheModuleIsBeingDisabledIsRejected() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(logger);

        AtomicInteger checks = new AtomicInteger();
        TestModule flipping = new TestModule(plugin, moduleManager) {
            @Override
            public boolean isEnabled() {
                return checks.incrementAndGet() == 1;
            }
        };

        int taskId = flipping.getScheduler().runTask("late", () -> {
        });

        assertEquals(net.bitbylogic.module.scheduler.ModuleScheduler.REJECTED_TASK_ID, taskId);
        assertTrue(flipping.getScheduler().getTaskSnapshot().isEmpty());
        verify(platformScheduler, never()).runNextTick(any());
    }

    @Test
    void registrationListsAreReadOnly() {
        assertThrows(UnsupportedOperationException.class, () -> module.getCommands().clear());
        assertThrows(UnsupportedOperationException.class, () -> module.getListeners().clear());
        assertThrows(UnsupportedOperationException.class, () -> module.getConfigurables().clear());
    }

    @Test
    void constructorDoesNotLoadConfiguration() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        TestModule fresh = new TestModule(plugin, moduleManager);

        assertEquals(null, fresh.getConfig());
        assertFalse(new File(dataFolder, "test").exists());
    }

    @Test
    void configValueOfWrongTypeFallsBackToDefault() {
        module.getConfig().set("value", "not a number");

        int value = module.getConfigValueOrDefault("value", 5, false);

        assertEquals(5, value);
    }

    @Test
    void configValueOfMatchingTypeIsReturned() {
        module.getConfig().set("value", 12);

        int value = module.getConfigValueOrDefault("value", 5, false);

        assertEquals(12, value);
    }

}
