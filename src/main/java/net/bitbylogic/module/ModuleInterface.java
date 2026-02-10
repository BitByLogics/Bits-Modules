package net.bitbylogic.module;

public interface ModuleInterface {

    /**
     * Invoked when the module is registered within the system.
     * This method is typically used to perform initialization tasks
     * or to prepare the module for subsequent operations such as enabling
     * or reloading. Implementations should contain logic specific to
     * the requirements of the module during the registration phase.
     */
    void onRegister();

    /**
     * Invoked to enable the module's functionality within the system.
     * This method is typically called after the module has been properly registered
     * and prepared for operation.
     *<p></p>
     * Implementations should include any initialization logic or resource allocation
     * required to activate the module. Ensure to handle any necessary state
     * transitions or dependency setup during this process.
     */
    void onEnable();

    /**
     * Invoked to reload the current module.
     * This method should be implemented to redefine or reinitialize
     * parts of the module as necessary while it is already active.
     * Common use cases include refreshing configuration, resources,
     * or state without fully disabling and re-enabling the module.
     */
    void onReload();

    /**
     * Invoked when the module is disabled. This method is intended to handle
     * cleanup operations, release resources, or perform any necessary shutdown
     * logic required when the module is no longer active.
     */
    void onDisable();

    /**
     * Retrieves the module data containing details such as the module's ID, name, and description.
     *
     * @return an instance of ModuleData that holds the module's metadata.
     */
    ModuleData getModuleData();

}
