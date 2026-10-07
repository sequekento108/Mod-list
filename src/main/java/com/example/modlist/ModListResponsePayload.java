package com.example.modlist;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Client -> server: the client's mod list. */
public record ModListResponsePayload(UUID requestId, List<ModEntry> mods) implements CustomPacketPayload {
    public static final Type<ModListResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModListMod.MODID, "response"));

    public static final StreamCodec<FriendlyByteBuf, ModListResponsePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ModListResponsePayload::requestId,
            ModEntry.CODEC.apply(ByteBufCodecs.list(4096)), ModListResponsePayload::mods,
            ModListResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
