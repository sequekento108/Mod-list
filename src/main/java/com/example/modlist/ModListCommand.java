package com.example.modlist;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/**
 * /mods                      - server mod list
 * /mods server               - server mod list
 * /mods client               - your own client's mod list
 * /mods client <player>      - another player's client mod list   (op level 2)
 * /mods compare              - compare your client against the server
 * /mods compare <player>     - compare another player's client    (op level 2)
 */
public final class ModListCommand {
    private static final long TIMEOUT_MS = 10_000;

    private enum Mode { LIST, COMPARE }

    private record Pending(CommandSourceStack source, UUID target, String targetName, Mode mode, long expiresAt) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private ModListCommand() {}

    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("mods")
                .executes(ctx -> showServer(ctx.getSource()))
                .then(Commands.literal("server")
                        .executes(ctx -> showServer(ctx.getSource())))
                .then(Commands.literal("client")
                        .executes(ctx -> queryClient(ctx.getSource(), ctx.getSource().getPlayerOrException(), Mode.LIST))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> queryClient(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), Mode.LIST))))
                .then(Commands.literal("compare")
                        .executes(ctx -> queryClient(ctx.getSource(), ctx.getSource().getPlayerOrException(), Mode.COMPARE))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> queryClient(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), Mode.COMPARE)))));
    }

    // ---------------------------------------------------------------- server list

    private static int showServer(CommandSourceStack src) {
        List<ModEntry> mods = ModEntry.localMods();
        src.sendSuccess(() -> formatList("Server mods", mods), false);
        return mods.size();
    }

    // ---------------------------------------------------------------- client query

    private static int queryClient(CommandSourceStack src, ServerPlayer target, Mode mode) {
        boolean local = target.connection.getConnection().isMemoryConnection();
        if (!local && !target.connection.hasChannel(ModListRequestPayload.TYPE)) {
            src.sendFailure(Component.literal(target.getGameProfile().getName()
                    + " doesn't have the Modlist mod installed, so their client mod list can't be queried."));
            return 0;
        }

        UUID id = UUID.randomUUID();
        PENDING.put(id, new Pending(src, target.getUUID(), target.getGameProfile().getName(),
                mode, System.currentTimeMillis() + TIMEOUT_MS));
        PacketDistributor.sendToPlayer(target, new ModListRequestPayload(id));
        return 1;
    }

    /** Runs on the server when a client answers. */
    public static void onResponse(ModListResponsePayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sender)) return;

        Pending req = PENDING.get(payload.requestId());
        // Ignore unknown ids and players answering requests that weren't sent to them.
        if (req == null || !req.target().equals(sender.getUUID())) return;
        PENDING.remove(payload.requestId());

        List<ModEntry> clientMods = payload.mods();
        switch (req.mode()) {
            case LIST -> req.source().sendSuccess(
                    () -> formatList(req.targetName() + "'s client mods", clientMods), false);
            case COMPARE -> req.source().sendSuccess(
                    () -> formatCompare(req.targetName(), ModEntry.localMods(), clientMods), false);
        }
    }

    /** Expire requests whose client never answered. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0 || PENDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(p -> {
            if (p.expiresAt() > now) return false;
            p.source().sendFailure(Component.literal("No response from " + p.targetName()
                    + " (timed out). Their client may not have the Modlist mod."));
            return true;
        });
    }

    // ---------------------------------------------------------------- formatting

    private static MutableComponent formatList(String title, List<ModEntry> mods) {
        MutableComponent out = Component.literal(title + " (" + mods.size() + "):")
                .withStyle(ChatFormatting.GOLD);
        for (ModEntry m : mods) {
            out.append(Component.literal("\n - ").withStyle(ChatFormatting.DARK_GRAY))
               .append(Component.literal(m.displayName()).withStyle(ChatFormatting.WHITE))
               .append(Component.literal(" (" + m.id() + ")").withStyle(ChatFormatting.GRAY))
               .append(Component.literal(" " + m.version()).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static MutableComponent formatCompare(String player, List<ModEntry> server, List<ModEntry> client) {
        Map<String, ModEntry> s = toEntryMap(server);
        Map<String, ModEntry> c = toEntryMap(client);

        List<String> missingOnClient = new ArrayList<>();
        List<String> missingOnServer = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();

        for (var e : s.entrySet()) {
            ModEntry cv = c.get(e.getKey());
            ModEntry sv = e.getValue();
            if (cv == null) missingOnClient.add(formatModEntry(sv));
            else if (!cv.version().equals(sv.version())) mismatched.add(formatModEntry(sv) + " (server " + sv.version() + ", client " + cv.version() + ")");
        }
        for (var e : c.entrySet()) {
            if (!s.containsKey(e.getKey())) missingOnServer.add(formatModEntry(e.getValue()));
        }

        MutableComponent out = Component.literal("Server vs " + player + "'s client:")
                .withStyle(ChatFormatting.GOLD);
        if (missingOnClient.isEmpty() && missingOnServer.isEmpty() && mismatched.isEmpty()) {
            return out.append(Component.literal("\n Identical mod lists (" + s.size() + " mods).")
                    .withStyle(ChatFormatting.GREEN));
        }
        section(out, "On server only", ChatFormatting.RED, missingOnClient);
        section(out, "On client only", ChatFormatting.YELLOW, missingOnServer);
        section(out, "Version mismatch", ChatFormatting.LIGHT_PURPLE, mismatched);
        return out;
    }

    private static void section(MutableComponent out, String title, ChatFormatting color, List<String> lines) {
        if (lines.isEmpty()) return;
        out.append(Component.literal("\n" + title + " (" + lines.size() + "):").withStyle(color));
        for (String l : lines) {
            out.append(Component.literal("\n - " + l).withStyle(ChatFormatting.GRAY));
        }
    }

    private static Map<String, ModEntry> toEntryMap(List<ModEntry> mods) {
        Map<String, ModEntry> m = new TreeMap<>();
        for (ModEntry e : mods) m.put(e.id(), e);
        return m;
    }

    private static String formatModEntry(ModEntry e) {
        return e.displayName() + " (" + e.id() + ") " + e.version();
    }
}
