package net.bitbylogic.module.message;

import net.bitbylogic.utils.message.messages.MessageGroup;
import net.bitbylogic.utils.message.messages.MessageKey;

import java.util.List;

public class ModuleMessages extends MessageGroup {

    public static MessageKey HELP;
    public static MessageKey INVALID_MODULE;
    public static MessageKey MODULE_NOT_ENABLED;
    public static MessageKey MODULE_ALREADY_ENABLED;

    public static MessageKey RELOADING_MODULE;
    public static MessageKey ENABLING_MODULE;
    public static MessageKey DISABLING_MODULE;
    public static MessageKey DEBUG_ENABLE_MODULE;
    public static MessageKey DEBUG_DISABLE_MODULE;

    public static MessageKey INVALID_PAGE;
    public static MessageKey MODULE_LIST_HEADER;
    public static MessageKey MODULE_LIST_ENTRY;
    public static MessageKey MODULE_LIST_FOOTER;
    public static MessageKey NO_TASKS;
    public static MessageKey TASK_LINE;

    public ModuleMessages() {
        super("Bits-Modules");
    }

    @Override
    public void register() {
        HELP = register("Help", List.of(
                "<#C9D4E4>Module Commands",
                "",
                "<#E5E9F0>/module list <page> <#8A8F99>• <#B4BCC8>List all modules",
                "<#E5E9F0>/module reload <id> <#8A8F99>• <#B4BCC8>Reload the specified module's config.",
                "<#E5E9F0>/module enable <id> <#8A8F99>• <#B4BCC8>Enable the specified module.",
                "<#E5E9F0>/module disable <id> <#8A8F99>• <#B4BCC8>Disable the specified module.",
                "<#E5E9F0>/module toggle <id> <#8A8F99>• <#B4BCC8>Toggles the specified module."
        ));

        INVALID_MODULE = register("Invalid-Module", "<#FF6B6B>Invalid module: <id>");

        MODULE_NOT_ENABLED = register("Module-Not-Enabled", "<#FF6B6B>Module <id> isn't enabled.");

        MODULE_ALREADY_ENABLED = register("Module-Already-Enabled", "<#FF6B6B>Module <id> isn't disabled.");

        RELOADING_MODULE = register("Reloading-Module",
                "<#7ED957>Reloading module <#8A8F99>(<#B4BCC8>Name:</#B4BCC8> <name><#8A8F99>, <#B4BCC8>ID:</#B4BCC8> <id><#8A8F99>)");

        ENABLING_MODULE = register("Enabling-Module",
                "<#7ED957>Enabling module <#8A8F99>(<#B4BCC8>Name:</#B4BCC8> <name><#8A8F99>, <#B4BCC8>ID:</#B4BCC8> <id><#8A8F99>)");

        DISABLING_MODULE = register("Disabling-Module",
                "<#7ED957>Disabling module <#8A8F99>(<#B4BCC8>Name:</#B4BCC8> <name><#8A8F99>, <#B4BCC8>ID:</#B4BCC8> <id><#8A8F99>)");

        DEBUG_ENABLE_MODULE = register("Debug-Enable",
                "<#7ED957>Enabling debug for module <#8A8F99>(<#B4BCC8>Name:</#B4BCC8> <name><#8A8F99>, <#B4BCC8>ID:</#B4BCC8> <id><#8A8F99>)");

        DEBUG_DISABLE_MODULE = register("Debug-Disable",
                "<#7ED957>Disabling debug for module <#8A8F99>(<#B4BCC8>Name:</#B4BCC8> <name><#8A8F99>, <#B4BCC8>ID:</#B4BCC8> <id><#8A8F99>)");

        INVALID_PAGE = register("Invalid-Page",
                "<#FF6B6B>Invalid page: <page>");

        MODULE_LIST_HEADER = register("Module-List-Header",
                "<#8A8F99><st>─────</st> <#C9D4E4><bold>MODULE LIST</bold> <#8A8F99><st>─────</st>");

        MODULE_LIST_ENTRY = register("Module-List-Entry",
                "<#8A8F99>- <#C9D4E4><name> <#8A8F99>(<#B4BCC8>ID:</#B4BCC8> <id><#8A8F99>, <#B4BCC8>Status:</#B4BCC8> <status><#8A8F99>)");

        MODULE_LIST_FOOTER = register("Module-List-Footer",
                "<#8A8F99><st>────────</st> <#B4BCC8>Page:</#B4BCC8> <page>/<pages> <#8A8F99><st>────────</st>");

        NO_TASKS = register("No-Tasks",
                "<#FF6B6B>Module <id> has no active tasks.");
        TASK_LINE = register("Task-Line", "<#8A8F99>- <#B4BCC8><name> <#8A8F99>(<#B4BCC8>Type:</#B4BCC8> <type><#8A8F99>)");
    }
}
