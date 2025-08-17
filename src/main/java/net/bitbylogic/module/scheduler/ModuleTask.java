package net.bitbylogic.module.scheduler;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.bitbylogic.module.BitsModule;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Getter
public abstract class ModuleTask extends ModuleRunnable {

    private final String id;
    private final ModuleTaskType type;

    @Getter(AccessLevel.PROTECTED)
    private final BukkitRunnable bukkitRunnable;

    private final @Nullable ModuleRunnable runnable;

    @Setter(AccessLevel.PROTECTED)
    private BitsModule moduleInstance;

    @Setter
    private int taskId = -1;

    public ModuleTask(@NonNull String id, @NonNull ModuleTaskType type) {
        this(id, type, null);
    }

    public ModuleTask(@NonNull String id, @NonNull ModuleTaskType type, @Nullable ModuleRunnable runnable) {
        this.id = id;
        this.type = type;
        this.runnable = runnable;

        if (runnable != null) {
            runnable.setTask(this);
        }

        this.bukkitRunnable = createBukkitRunnable();
    }

    private BukkitRunnable createBukkitRunnable() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    taskId = getTaskId();

                    if (moduleInstance == null) {
                        cancel();
                        return;
                    }

                    if (runnable != null) {
                        runnable.run();
                        return;
                    }

                    ModuleTask.this.run();
                } catch (Exception e) {
                    moduleInstance.getPlugin().getLogger().severe("Exception in ModuleTask '" + id + "': " + e.getMessage());
                    e.printStackTrace();
                    cancel();
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                ModuleTask.this.cancel();
            }
        };
    }

    @Override
    public void cancel() {
        if (Bukkit.getScheduler().isCurrentlyRunning(taskId) || Bukkit.getScheduler().isQueued(taskId)) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        markForCleanup();
    }

    public boolean isActive() {
        if (taskId == -1) {
            return true;
        }

        return Bukkit.getScheduler().isCurrentlyRunning(taskId) || Bukkit.getScheduler().isQueued(taskId);
    }

    private void markForCleanup() {
        if(moduleInstance == null) {
            return;
        }

        moduleInstance.getModuleManager().scheduleCleanup(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleTask that)) return false;
        return id.equals(that.id) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    public enum ModuleTaskType {

        SINGLE,
        DELAYED,
        TIMER,
        SINGLE_ASYNC,
        DELAYED_ASYNC,
        TIMER_ASYNC;

    }

}
