package net.bitbylogic.module.scheduler;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.bitbylogic.module.BitsModule;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ModuleScheduler {

    @Getter
    private final List<ModuleTask> tasks = new ArrayList<>();

    private final BitsModule module;

    public int runTask(@NonNull String id, @NonNull ModuleRunnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.SINGLE, runnable) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTask(module.getPlugin()).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTask(@NonNull String id, @NonNull Runnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.SINGLE) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTask(module.getPlugin()).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskAsync(@NonNull String id, @NonNull ModuleRunnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.SINGLE_ASYNC, runnable) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskAsynchronously(module.getPlugin()).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskAsync(@NonNull String id, @NonNull Runnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.SINGLE_ASYNC) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskAsynchronously(module.getPlugin()).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskLater(@NonNull String id, long delay, @NonNull ModuleRunnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.DELAYED, runnable) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskLater(module.getPlugin(), delay).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskLater(@NonNull String id, long delay, @NonNull Runnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.DELAYED) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskLater(module.getPlugin(), delay).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskTimer(@NonNull String id, long delay, long repeat, @NonNull ModuleRunnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.TIMER, runnable) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskTimer(module.getPlugin(), delay, repeat).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskTimer(@NonNull String id, long delay, long repeat, @NonNull Runnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.TIMER) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskTimer(module.getPlugin(), delay, repeat).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskLaterAsync(@NonNull String id, long delay, @NonNull ModuleRunnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.DELAYED_ASYNC, runnable) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskLaterAsynchronously(module.getPlugin(), delay).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskLaterAsync(@NonNull String id, long delay, @NonNull Runnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.DELAYED_ASYNC) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskLaterAsynchronously(module.getPlugin(), delay).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskTimerAsync(@NonNull String id, long delay, long repeat, @NonNull ModuleRunnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.TIMER_ASYNC, runnable) {
            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public void cancel() {
                super.cancel();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskTimerAsynchronously(module.getPlugin(), delay, repeat).getTaskId());
        return moduleTask.getTaskId();
    }

    public int runTaskTimerAsync(@NonNull String id, long delay, long repeat, @NonNull Runnable runnable) {
        ModuleTask moduleTask = new ModuleTask(id, ModuleTask.ModuleTaskType.TIMER_ASYNC) {
            @Override
            public void run() {
                runnable.run();
            }
        };

        moduleTask.setModuleInstance(module);
        synchronized (tasks) {
            tasks.add(moduleTask);
        }

        moduleTask.setTaskId(moduleTask.getBukkitRunnable().runTaskTimerAsynchronously(module.getPlugin(), delay, repeat).getTaskId());
        return moduleTask.getTaskId();
    }

    public Set<ModuleTask> getTasksById(@NonNull String id) {
        synchronized (tasks) {
            return tasks.stream().filter(moduleTask -> moduleTask.getId().equalsIgnoreCase(id)).collect(Collectors.toUnmodifiableSet());
        }
    }

    public void cancelTask(@NonNull String id) {
        synchronized (tasks) {
            tasks.removeIf(task -> {
                if (task.getBukkitRunnable() != null) {
                    task.getBukkitRunnable().cancel();
                }

                if (task.getTaskId() != -1) {
                    Bukkit.getScheduler().cancelTask(task.getTaskId());
                }

                return task.getId().equalsIgnoreCase(id);
            });
        }
    }

}
