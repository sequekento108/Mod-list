package com.example.modlist;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ModListMod.MODID)
public class ModListMod {
    public static final String MODID = "modlist";

    public ModListMod(IEventBus modBus) {
        modBus.addListener(ModListNetwork::register);
        NeoForge.EVENT_BUS.addListener(ModListCommand::registerCommands);
        NeoForge.EVENT_BUS.addListener(ModListCommand::onServerTick);
    }
}
