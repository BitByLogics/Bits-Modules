package net.bitbylogic.module.task;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.bitbylogic.module.BitsModule;

/**
 * Represents an abstract task that is pending execution for a specific module type.
 * This class provides a mechanism to associate a task with a module of a given type
 * and ensures the task implementation defines how the module should be processed.
 *
 * @param <T> the type of the module this task is associated with, which must extend {@link BitsModule}
 */
@Getter
@RequiredArgsConstructor
public abstract class ModulePendingTask<T extends BitsModule> {

    private final Class<T> clazz;

    /**
     * Executes the pending task logic using the specified module.
     *
     * @param module the module of type {@code T} to process the pending task,
     *               must not be {@code null} and should match the expected type of this task
     */
    public abstract void accept(T module);

}
