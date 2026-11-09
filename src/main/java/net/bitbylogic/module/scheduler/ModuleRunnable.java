package net.bitbylogic.module.scheduler;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class ModuleRunnable {

    @Setter(AccessLevel.PROTECTED)
    private ModuleTask task;

    public abstract void run();

    public void cancel() {
        if (task == null) {
            return;
        }

        task.cancel();
    }

}
