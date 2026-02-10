package net.bitbylogic.module;

import co.aikar.commands.BaseCommand;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class ModuleCommand<M> extends BaseCommand {

    private M module;

}
