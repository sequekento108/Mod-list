package com.example.modlist;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.fml.ModList;

import java.util.Comparator;
import java.util.List;

/** A mod id + display name + version. Kept tiny because serverbound play packets are capped at 32 KB. */
public record ModEntry(String id, String displayName, String version) {
    public static final StreamCodec<ByteBuf, ModEntry> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModEntry::id,
            ByteBufCodecs.STRING_UTF8, ModEntry::displayName,
            ByteBufCodecs.STRING_UTF8, ModEntry::version,
            ModEntry::new);

    /** Mods loaded in the current JVM (client mods on a client, server mods on a server). */
    public static List<ModEntry> localMods() {
        return ModList.get().getMods().stream()
                .map(m -> new ModEntry(m.getModId(), m.getDisplayName(), m.getVersion().toString()))
                .sorted(Comparator.comparing(ModEntry::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
