package net.bitbylogic.module;

import co.aikar.commands.BaseCommand;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class ModuleCommand extends BaseCommand {

    private BitsModule module;

    public <O extends BitsModule> O getModuleAs(Class<O> moduleClass) {
        return (O) module;
    }

}
