package com.example.modlist;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModListNetwork {
    private ModListNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): clients/servers without this mod can still connect.
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(ModListRequestPayload.TYPE, ModListRequestPayload.STREAM_CODEC, ModListNetwork::onRequest);
        registrar.playToServer(ModListResponsePayload.TYPE, ModListResponsePayload.STREAM_CODEC, ModListCommand::onResponse);
    }

    /** Runs on the client: answer with our local mod list. */
    private static void onRequest(ModListRequestPayload payload, IPayloadContext ctx) {
        ctx.reply(new ModListResponsePayload(payload.requestId(), ModEntry.localMods()));
    }
}
