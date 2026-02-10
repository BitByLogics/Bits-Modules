package net.bitbylogic.module;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the metadata of a module with an identifier, name, and description.
 * <p></p>
 * This record is immutable and provides a concise way to store module-related
 * information. Each instance of this record guarantees non-null values for all
 * its components.
 *
 * @param id          The unique identifier of the module. Must not be null.
 * @param name        The name of the module. Must not be null.
 * @param description A brief description of the module. Must not be null.
 */
public record ModuleData(@NotNull String id, @NotNull String name, @NotNull String description) {

}
