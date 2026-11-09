package net.bitbylogic.module.scheduler;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.bitbylogic.module.BitsModule;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ModuleScheduler {

    public static final int REJECTED_TASK_ID = -1;

    private final List<ModuleTask> tasks = new ArrayList<>();

    private final BitsModule module;

    @Getter
    @Setter
    private volatile boolean cancelTimersOnError = true;

    public int runTask(@NonNull String id, @NonNull ModuleRunnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.SINGLE, runnable, runnable::run,
                executable -> {
                    platform().runNextTick(task -> executable.run());
                    return null;
                });
    }

    public int runTask(@NonNull String id, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.SINGLE, null, runnable,
                executable -> {
                    platform().runNextTick(task -> executable.run());
                    return null;
                });
    }

    public int runTaskAsync(@NonNull String id, @NonNull ModuleRunnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.SINGLE_ASYNC, runnable, runnable::run,
                executable -> {
                    platform().runAsync(task -> executable.run());
                    return null;
                });
    }

    public int runTaskAsync(@NonNull String id, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.SINGLE_ASYNC, null, runnable,
                executable -> {
                    platform().runAsync(task -> executable.run());
                    return null;
                });
    }

    public int runTaskLater(@NonNull String id, long delay, @NonNull ModuleRunnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.DELAYED, runnable, runnable::run,
                executable -> platform().runLater(executable, ticks(delay)));
    }

    public int runTaskLater(@NonNull String id, long delay, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.DELAYED, null, runnable,
                executable -> platform().runLater(executable, ticks(delay)));
    }

    public int runTaskTimer(@NonNull String id, long delay, long repeat, @NonNull ModuleRunnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.TIMER, runnable, runnable::run,
                executable -> platform().runTimer(executable, ticks(delay), ticks(repeat)));
    }

    public int runTaskTimer(@NonNull String id, long delay, long repeat, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.TIMER, null, runnable,
                executable -> platform().runTimer(executable, ticks(delay), ticks(repeat)));
    }

    public int runTaskLaterAsync(@NonNull String id, long delay, @NonNull ModuleRunnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.DELAYED_ASYNC, runnable, runnable::run,
                executable -> platform().runLaterAsync(executable, ticks(delay)));
    }

    public int runTaskLaterAsync(@NonNull String id, long delay, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.DELAYED_ASYNC, null, runnable,
                executable -> platform().runLaterAsync(executable, ticks(delay)));
    }

    public int runTaskTimerAsync(@NonNull String id, long delay, long repeat, @NonNull ModuleRunnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.TIMER_ASYNC, runnable, runnable::run,
                executable -> platform().runTimerAsync(executable, ticks(delay), ticks(repeat)));
    }

    public int runTaskTimerAsync(@NonNull String id, long delay, long repeat, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.TIMER_ASYNC, null, runnable,
                executable -> platform().runTimerAsync(executable, ticks(delay), ticks(repeat)));
    }

    public int runTaskAtEntity(@NonNull String id, @NonNull Entity entity, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.SINGLE, null, runnable,
                (executable, moduleTask) -> {
                    platform().runAtEntityWithFallback(entity, task -> executable.run(), moduleTask::cancel);
                    return null;
                });
    }

    public int runTaskAtEntityLater(@NonNull String id, @NonNull Entity entity, long delay, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.DELAYED, null, runnable,
                (executable, moduleTask) -> platform().runAtEntityLater(entity, executable, moduleTask::cancel, ticks(delay)));
    }

    public int runTaskAtEntityTimer(@NonNull String id, @NonNull Entity entity, long delay, long repeat, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.TIMER, null, runnable,
                (executable, moduleTask) -> platform().runAtEntityTimer(entity, executable, moduleTask::cancel, ticks(delay), ticks(repeat)));
    }

    public int runTaskAtLocation(@NonNull String id, @NonNull Location location, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.SINGLE, null, runnable,
                executable -> {
                    platform().runAtLocation(location, task -> executable.run());
                    return null;
                });
    }

    public int runTaskAtLocationLater(@NonNull String id, @NonNull Location location, long delay, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.DELAYED, null, runnable,
                executable -> platform().runAtLocationLater(location, executable, ticks(delay)));
    }

    public int runTaskAtLocationTimer(@NonNull String id, @NonNull Location location, long delay, long repeat, @NonNull Runnable runnable) {
        return schedule(id, ModuleTask.ModuleTaskType.TIMER, null, runnable,
                executable -> platform().runAtLocationTimer(location, executable, ticks(delay), ticks(repeat)));
    }

    private int schedule(String id, ModuleTask.ModuleTaskType type, @Nullable ModuleRunnable moduleRunnable,
                         Runnable body, Function<Runnable, WrappedTask> starter) {
        return schedule(id, type, moduleRunnable, body, (executable, moduleTask) -> starter.apply(executable));
    }

    private int schedule(String id, ModuleTask.ModuleTaskType type, @Nullable ModuleRunnable moduleRunnable,
                         Runnable body, BiFunction<Runnable, ModuleTask, WrappedTask> starter) {
        if (!module.isEnabled()) {
            module.log(Level.WARNING, "Cannot run task '" + id + "' because module is disabled.");
            return REJECTED_TASK_ID;
        }

        ModuleTask moduleTask = new ModuleTask(id, type, moduleRunnable) {
            @Override
            public void run() {
                body.run();
            }
        };

        moduleTask.setModuleInstance(module);

        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        if (!module.isEnabled()) {
            moduleTask.cancel();
            removeTask(moduleTask);
            module.log(Level.WARNING, "Cannot run task '" + id + "' because module is disabled.");
            return REJECTED_TASK_ID;
        }

        try {
            moduleTask.setWrappedTask(starter.apply(moduleTask.createExecutable(), moduleTask));
        } catch (RuntimeException exception) {
            moduleTask.cancel();

            removeTask(moduleTask);

            module.getPlugin().getLogger().log(Level.SEVERE, "Unable to schedule task '" + id + "' for module '" + module.getModuleData().id() + "'", exception);
            return REJECTED_TASK_ID;
        }

        return moduleTask.getTaskId();
    }

    private PlatformScheduler platform() {
        return module.getModuleManager().getFoliaLib().getScheduler();
    }

    private long ticks(long ticks) {
        return Math.max(1L, ticks);
    }

    public boolean removeTask(@NonNull ModuleTask task) {
        synchronized (tasks) {
            return tasks.remove(task);
        }
    }

    public List<ModuleTask> getTaskSnapshot() {
        synchronized (tasks) {
            return new ArrayList<>(tasks);
        }
    }

    public Set<ModuleTask> getTasksById(@NonNull String id) {
        synchronized (tasks) {
            return tasks.stream().filter(moduleTask -> moduleTask.getId().equalsIgnoreCase(id)).collect(Collectors.toUnmodifiableSet());
        }
    }

    public void cancelTask(@NonNull String id) {
        synchronized (tasks) {
            tasks.removeIf(task -> {
                if (!task.getId().equalsIgnoreCase(id)) {
                    return false;
                }

                task.cancel();
                return true;
            });
        }
    }

}
