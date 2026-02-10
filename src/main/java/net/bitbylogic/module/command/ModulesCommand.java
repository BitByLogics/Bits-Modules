package net.bitbylogic.module.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import net.bitbylogic.module.BitsModule;
import net.bitbylogic.module.ModuleManager;
import net.bitbylogic.module.event.ModuleReloadEvent;
import net.bitbylogic.module.message.ModuleMessages;
import net.bitbylogic.module.scheduler.ModuleTask;
import net.bitbylogic.utils.message.MessageUtil;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CommandAlias("module|mdl|modules|mdls")
@CommandPermission("bitsmodules.command.module")
public class ModulesCommand extends BaseCommand {

    @Dependency
    private ModuleManager moduleManager;

    @Default
    public void onDefault(CommandSender sender) {
        ModuleMessages.HELP.send(MessageUtil.asAudience(sender));
    }

    @Subcommand("list")
    @CommandPermission("bitsmodules.command.module.list")
    public void onList(CommandSender sender, @Default("1") int page) {
        displayPage(sender, page);
    }

    @Subcommand("reload")
    @CommandPermission("bitsmodules.command.module.reload")
    @CommandCompletion("@moduleIds")
    public void onReload(CommandSender sender, String moduleId) {
        Optional<BitsModule> optionalModule = moduleManager.getModuleByID(moduleId);
        if (optionalModule.isEmpty()) {
            ModuleMessages.INVALID_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        BitsModule module = optionalModule.get();

        if (!module.isEnabled()) {
            ModuleMessages.MODULE_NOT_ENABLED.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        ModuleMessages.RELOADING_MODULE.send(MessageUtil.asAudience(sender),
                Placeholder.unparsed("name", module.getModuleData().name()),
                Placeholder.unparsed("id", module.getModuleData().id())
        );

        module.reloadConfig();
        module.loadConfigPaths();
        module.onReload();
        Bukkit.getPluginManager().callEvent(new ModuleReloadEvent(module));
    }

    @Subcommand("enable")
    @CommandPermission("bitsmodules.command.module.enable")
    @CommandCompletion("@moduleIds")
    public void onEnable(CommandSender sender, String moduleId) {
        Optional<BitsModule> optionalModule = moduleManager.getModuleByID(moduleId);
        if (optionalModule.isEmpty()) {
            ModuleMessages.INVALID_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        BitsModule module = optionalModule.get();

        if (module.isEnabled()) {
            ModuleMessages.MODULE_ALREADY_ENABLED.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        ModuleMessages.ENABLING_MODULE.send(MessageUtil.asAudience(sender),
                Placeholder.unparsed("name", module.getModuleData().name()),
                Placeholder.unparsed("id", module.getModuleData().id())
        );

        moduleManager.enableModule(module.getModuleData().id());
    }

    @Subcommand("disable")
    @CommandPermission("bitsmodules.command.module.disable")
    @CommandCompletion("@moduleIds")
    public void onDisable(CommandSender sender, String moduleId) {
        Optional<BitsModule> optionalModule = moduleManager.getModuleByID(moduleId);
        if (optionalModule.isEmpty()) {
            ModuleMessages.INVALID_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        BitsModule module = optionalModule.get();

        if (!module.isEnabled()) {
            ModuleMessages.MODULE_NOT_ENABLED.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        ModuleMessages.DISABLING_MODULE.send(MessageUtil.asAudience(sender),
                Placeholder.unparsed("name", module.getModuleData().name()),
                Placeholder.unparsed("id", module.getModuleData().id())
        );

        moduleManager.disableModule(module.getModuleData().id());
    }

    @Subcommand("debug")
    @CommandPermission("bitsmodules.command.module.debug")
    @CommandCompletion("@moduleIds")
    public void onDebug(CommandSender sender, String moduleId) {
        Optional<BitsModule> optionalModule = moduleManager.getModuleByID(moduleId);
        if (optionalModule.isEmpty()) {
            ModuleMessages.INVALID_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        BitsModule module = optionalModule.get();

        if (!module.isDebug()) {
            module.setDebug(true);
            ModuleMessages.DEBUG_ENABLE_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("name", module.getModuleData().name()),
                    Placeholder.unparsed("id", module.getModuleData().id()));
        } else {
            module.setDebug(false);
            ModuleMessages.DEBUG_DISABLE_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("name", module.getModuleData().name()),
                    Placeholder.unparsed("id", module.getModuleData().id()));
        }
    }

    @Subcommand("toggle")
    @CommandPermission("bitsmodules.command.module.toggle")
    @CommandCompletion("@moduleIds")
    public void onToggle(CommandSender sender, String moduleId) {
        Optional<BitsModule> optionalModule = moduleManager.getModuleByID(moduleId);
        if (optionalModule.isEmpty()) {
            ModuleMessages.INVALID_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        BitsModule module = optionalModule.get();

        if (module.isEnabled()) {
            ModuleMessages.DISABLING_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("name", module.getModuleData().name()),
                    Placeholder.unparsed("id", module.getModuleData().id()));
            moduleManager.disableModule(module.getModuleData().id());
        } else {
            ModuleMessages.ENABLING_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("name", module.getModuleData().name()),
                    Placeholder.unparsed("id", module.getModuleData().id()));
            moduleManager.enableModule(module.getModuleData().id());
        }
    }

    @Subcommand("tasks")
    @CommandPermission("bitsmodules.command.module.tasks")
    @CommandCompletion("@moduleIds")
    public void onTasks(CommandSender sender, String moduleId, @Default("1") int page) {
        Optional<BitsModule> optionalModule = moduleManager.getModuleByID(moduleId);
        if (optionalModule.isEmpty()) {
            ModuleMessages.INVALID_MODULE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", moduleId));
            return;
        }

        BitsModule module = optionalModule.get();

        if (module.getScheduler().getTasks().isEmpty()) {
            ModuleMessages.NO_TASKS.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("id", module.getModuleData().name()));
            return;
        }

        List<TextComponent> taskComponents = new ArrayList<>();

        for (ModuleTask task : module.getScheduler().getTasks()) {
            if (!task.isActive()) {
                continue;
            }

            taskComponents.add((TextComponent) ModuleMessages.TASK_LINE.get(Placeholder.unparsed("name", task.getId()), Placeholder.unparsed("type", task.getType().name())));
        }

        sendPagedComponents(sender, module.getModuleData().name() + "'s Tasks", taskComponents, page);
    }

    private void displayPage(CommandSender sender, int page) {
        List<BitsModule> modules = new ArrayList<>(moduleManager.getModulesById().values());
        int pages = (int) Math.ceil(modules.size() / 10.0);

        if (page <= 0 || page > pages) {
            ModuleMessages.INVALID_PAGE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("page", String.valueOf(page)));
            return;
        }

        ModuleMessages.MODULE_LIST_HEADER.send(MessageUtil.asAudience(sender));

        int start = (page - 1) * 10;
        int end = Math.min(start + 10, modules.size());

        for (int i = start; i < end; i++) {
            BitsModule module = modules.get(i);

            TextComponent line = (TextComponent) ModuleMessages.MODULE_LIST_ENTRY.get(
                            Placeholder.unparsed("name", module.getModuleData().name()),
                            Placeholder.unparsed("id", module.getModuleData().id()),
                            Placeholder.unparsed("status", module.isEnabled() ? "Enabled" : "Disabled")
                    ).hoverEvent(HoverEvent.showText(MessageUtil.deserialize("<gray>" + module.getModuleData().description())))
                    .clickEvent(ClickEvent.runCommand("/module toggle " + module.getModuleData().id()));

            MessageUtil.send(sender, line);
        }

        ModuleMessages.MODULE_LIST_FOOTER.send(MessageUtil.asAudience(sender),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));
    }

    private void sendPagedComponents(CommandSender sender, String title, List<TextComponent> components, int page) {
        int perPage = 10;
        int pages = (int) Math.ceil(components.size() / (double) perPage);

        if (page <= 0 || page > pages) {
            ModuleMessages.INVALID_PAGE.send(MessageUtil.asAudience(sender),
                    Placeholder.unparsed("page", String.valueOf(page)));
            return;
        }

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, components.size());

        for (int i = start; i < end; i++) {
            MessageUtil.send(sender, components.get(i));
        }

        ModuleMessages.MODULE_LIST_FOOTER.send(MessageUtil.asAudience(sender),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));
    }

}