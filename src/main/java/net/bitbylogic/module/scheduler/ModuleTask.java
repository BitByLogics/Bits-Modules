package net.bitbylogic.module.scheduler;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.bitbylogic.module.BitsModule;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@Getter
public abstract class ModuleTask extends ModuleRunnable {

    private static final AtomicInteger NEXT_TASK_ID = new AtomicInteger(1);

    private final String id;
    private final ModuleTaskType type;
    private final int taskId = NEXT_TASK_ID.getAndIncrement();

    private final @Nullable ModuleRunnable runnable;

    @Setter(AccessLevel.PROTECTED)
    private BitsModule moduleInstance;

    @Getter(AccessLevel.NONE)
    private volatile WrappedTask wrappedTask;

    private volatile boolean cancelled = false;
    private volatile boolean finished = false;

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
    }

    protected Runnable createExecutable() {
        return () -> {
            if (cancelled) {
                return;
            }

            if (moduleInstance == null) {
                cancel();
                return;
            }

            try {
                if (runnable != null) {
                    runnable.run();
                    return;
                }

                ModuleTask.this.run();
            } catch (Exception e) {
                boolean keepRunning = !isSingleRunTask() && !moduleInstance.getScheduler().isCancelTimersOnError();
                String outcome = " and was cancelled";

                if (keepRunning) {
                    outcome = ", it will keep running";
                }

                moduleInstance.getPlugin().getLogger().log(Level.SEVERE, "Exception in ModuleTask '" + id + "'" + outcome, e);

                if (!keepRunning) {
                    cancel();
                }
            } finally {
                if (isSingleRunTask()) {
                    finished = true;
                    markForCleanup();
                }
            }
        };
    }

    protected void setWrappedTask(@Nullable WrappedTask wrappedTask) {
        this.wrappedTask = wrappedTask;

        if (cancelled && wrappedTask != null) {
            wrappedTask.cancel();
        }
    }

    @Override
    public void cancel() {
        cancelled = true;

        WrappedTask task = wrappedTask;

        if (task != null) {
            task.cancel();
        }

        markForCleanup();
    }

    public boolean isActive() {
        return !cancelled && !finished;
    }

    private void markForCleanup() {
        if (moduleInstance == null) {
            return;
        }

        moduleInstance.getModuleManager().scheduleCleanup(this);
    }

    private boolean isSingleRunTask() {
        return type == ModuleTaskType.SINGLE
                || type == ModuleTaskType.DELAYED
                || type == ModuleTaskType.SINGLE_ASYNC
                || type == ModuleTaskType.DELAYED_ASYNC;
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
