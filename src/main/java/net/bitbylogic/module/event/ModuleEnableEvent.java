package net.bitbylogic.module.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.bitbylogic.module.BitsModule;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public class ModuleEnableEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BitsModule module;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

}
