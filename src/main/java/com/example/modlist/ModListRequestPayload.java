package com.example.modlist;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Server -> client: "tell me what mods you have". */
public record ModListRequestPayload(UUID requestId) implements CustomPacketPayload {
    public static final Type<ModListRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModListMod.MODID, "request"));

    public static final StreamCodec<FriendlyByteBuf, ModListRequestPayload> STREAM_CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, ModListRequestPayload::requestId, ModListRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
