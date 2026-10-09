package xaero.pac.teamclaims;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.mode.api.ClaimingModes;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.IServerClaimsManager;
import xaero.pac.common.server.claims.command.ClaimsClaimCommands;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.common.server.player.localization.AdaptiveLocalizer;
import xaero.pac.teamclaims.TeamClaimManager.BudgetInfo;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /teamclaims convert <toteam|topersonal> [radius]}: turns the player's own claims in the square of chunks
 * around their current chunk into team claims (the player's team sub-config) or back into personal claims (their
 * selected sub-claim, or the main config if that is the team sub-config), without unclaiming them and keeping their
 * forceload state. Claims of anybody else, including teammates and the server, are never touched.
 * <p>
 * Every conversion is a normal non-forced, non-server CLAIM through OPAC's {@code tryToClaimHelper} by the claim owner
 * over their own claim, so everything that applies to claiming applies here too: the claim action listeners of
 * addons, the team roles and both budgets, and OPAC's own checks. A conversion moves a claim from one budget into the
 * other, so it needs room in the one it moves into:
 * <ul>
 *     <li>{@code toteam}: the claim level (plus the forceload level for a forceloaded claim) and the <b>team's</b>
 *     budget, the team claim limit and, for a forceloaded claim, the team forceload limit
 *     ({@link TeamClaimsBridgeHandler#interceptClaim}). The player's private numbers don't matter;</li>
 *     <li>{@code topersonal}: the unclaim level and the converting player's <b>private</b> budget, their own claim
 *     limit (OPAC's limit check, on their private claim count) and, for a forceloaded claim, their own forceload limit
 *     ({@link TeamClaimsBridgeHandler#interceptClaim}). The team's numbers don't matter.</li>
 * </ul>
 * The claims tracker callbacks ({@link TeamClaimManager#onChunkChange}) move the team tracking, the team forceload
 * tickets and the counts. OPAC's own forceload ticket of the claim is removed and added again by the replacement itself.
 * <p>
 * The player is the one OPAC's claim commands would claim for: the impersonated player while impersonating. OPAC's
 * admin mode is deliberately not used: it only makes OPAC force a claim, which would skip the budgets and roles,
 * and a conversion only ever replaces the player's own claims, so there is nothing admin mode would be needed for.
 * <p>
 * Server-thread only.
 */
public final class TeamClaimsConvert {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "gui.xaero_pac_team_claims_";

    private TeamClaimsConvert() {}

    /**
     * What one conversion did. A chunk without a claim counts nowhere. After a rejection that stops the conversion
     * (e.g. the claim limit of the budget the claims move into), the remaining claims that would have been converted
     * count under the same reason.
     *
     * @param converted    claims converted
     * @param forceloaded  how many of the converted claims are forceloaded
     * @param notYours     claims of anybody else (teammates, other players, the server)
     * @param alreadyDone  own claims that already were in the target state
     * @param roleDenied   own claims the player's team rank does not allow converting
     * @param overBudget   own claims that did not fit into the claim or forceload limit of the budget they would move
     *                     into: the team's for {@code toteam}, the player's private one for {@code topersonal}
     * @param other        own claims rejected for any other reason (an addon, OPAC)
     */
    public record Summary(int converted, int forceloaded, int notYours, int alreadyDone, int roleDenied, int overBudget,
            int other) {}

    // ==================== Command node ====================

    /** {@code convert toteam [radius]} and {@code convert topersonal [radius]}, the radius 0 (only the current chunk) by default. */
    static LiteralArgumentBuilder<CommandSourceStack> convertNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("convert");
        for (boolean toTeam : new boolean[]{true, false}) {
            // No upper bound in the argument: the configured maximum is checked when the command is used, with a message
            node.then(Commands.literal(toTeam ? "toteam" : "topersonal")
                    .executes(context -> execute(context, toTeam, 0))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                            .executes(context -> execute(context, toTeam, IntegerArgumentType.getInteger(context, "radius")))));
        }
        return node;
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean toTeam, int radius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TeamClaimsCommands.requirePlayer(source);
        if (player == null) return 0;
        IServerData<?, ?> serverData = ServerData.from(source.getServer());
        if (serverData == null) return 0;
        // The same player OPAC's claim commands claim for (resets an impersonation the player may no longer do)
        UUID playerId = ClaimsClaimCommands.getClaimInputPlayerId(context, player, null, null, serverData, false,
                ClaimingModes.PLAYER);
        if (playerId == null) return 0;
        Integer impersonationSubIndex = playerId.equals(player.getUUID()) ? null
                : ((ServerPlayerData) ServerPlayerData.from(player)).getClaimsImpersonationInfo().getSubIndex(ClaimingModes.PLAYER);
        Summary summary = convert(source, player, playerId, impersonationSubIndex, player.level().dimension().location(),
                player.chunkPosition().x, player.chunkPosition().z, radius, toTeam);
        return summary == null ? 0 : summary.converted();
    }

    // ==================== Conversion ====================

    /**
     * Converts the claims of {@code playerId} in the square of {@code radius} chunks around the centre chunk and
     * reports the result to {@code source}.
     *
     * @param viewer                 the player running the command, null when there is none (the texts then use the
     *                               server's default translation)
     * @param impersonationSubIndex  the sub-claim the viewer selected for the player they impersonate, null when not
     *                               impersonating (the player's own selected sub-claim is used then)
     * @return what was done, null if nothing could be tried (the reason was sent to {@code source})
     */
    @Nullable
    public static Summary convert(CommandSourceStack source, @Nullable ServerPlayer viewer, UUID playerId,
            @Nullable Integer impersonationSubIndex, ResourceLocation dimension, int centerX, int centerZ, int radius,
            boolean toTeam) {
        MinecraftServer server = source.getServer();
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        TeamConfigManager configManager = TeamClaimsCommon.getTeamConfigManager();
        IServerData<?, ?> serverData = ServerData.from(server);
        if (claimManager == null || configManager == null || serverData == null) return null;
        AdaptiveLocalizer localizer = serverData.getAdaptiveLocalizer();

        int maxRadius = TeamClaimsServerConfig.CONFIG.convertMaxRadius.get();//read every time, it may have been edited
        if (radius < 0 || radius > maxRadius) {
            fail(source, localizer, viewer, KEY + "convert_radius_too_large", String.valueOf(maxRadius));
            return null;
        }
        if (configManager.getTeamConfigForPlayer(playerId) == null) {
            fail(source, localizer, viewer, KEY + "info_no_team");
            return null;
        }
        claimManager.ensureTeamSubConfig(playerId);//normally there already, but a conversion to team claims needs it
        int teamSubIndex = claimManager.getTeamSubIndex(playerId);
        if (teamSubIndex == -1) {
            fail(source, localizer, viewer, KEY + "convert_no_team_sub_config");
            return null;
        }

        // The guards of OPAC's tryToClaimTyped, once for the whole area. The distance and dimension checks don't apply:
        // the area is around the player, in their dimension, and only has claims they already own.
        IServerClaimsManager<?, ?, ?> claimsManager = serverData.getServerClaimsManager();
        ClaimResult.Type guard = null;
        if (!ServerConfig.CONFIG.claimsEnabled.get())
            guard = ClaimResult.Type.CLAIMS_ARE_DISABLED;
        else if (!claimsManager.isClaimable(dimension))
            guard = ClaimResult.Type.UNCLAIMABLE_DIMENSION;
        else {
            IServerPlayerClaimInfo<?> playerInfo = claimsManager.getPlayerInfo(playerId);
            if (playerInfo.isAreaClaimTaskInProgress())
                guard = ClaimResult.Type.AREA_ACTION_IN_PROGRESS;
            else if (playerInfo.isTransferInProgress())
                guard = ClaimResult.Type.TRANSFER_IN_PROGRESS;
            else if (playerInfo.isReplacementInProgress())
                guard = ClaimResult.Type.REPLACEMENT_IN_PROGRESS;
        }
        if (guard != null) {
            source.sendFailure(localizer.getFor(viewer, guard.message).copy().withStyle(ChatFormatting.RED));
            return null;
        }

        String targetSubId;
        int targetSubIndex;
        if (toTeam) {
            targetSubIndex = teamSubIndex;
            targetSubId = null;//not shown
        } else {
            // The sub-claim OPAC's claim commands would use for a new claim of the player, unless that is the team one
            IPlayerConfigAPI playerConfig = serverData.getPlayerConfigManager().getLoadedConfig(playerId);
            IPlayerConfigAPI used = impersonationSubIndex != null ? playerConfig.getEffectiveSubConfig(impersonationSubIndex)
                    : playerConfig.getEffectiveSubConfig(playerConfig.getEffective(PlayerConfigOptions.USED_SUBCLAIM));
            if (used.getSubIndex() == teamSubIndex) used = playerConfig;//the main config, sub index -1
            targetSubIndex = used.getSubIndex();
            targetSubId = used.getSubId() == null ? PlayerConfig.MAIN_SUB_ID : used.getSubId();
        }
        int claimLimit = claimsManager.getPlayerFullClaimLimit(playerId);
        // A team claim of the own team being replaced with a personal claim needs the unclaim level, a team claim the claim
        // level, and a forceloaded one the forceload level as well
        TeamAction roleAction = toTeam ? TeamAction.CLAIM : TeamAction.UNCLAIM;

        // At most (2 * 16 + 1)^2 = 1089 chunks (the largest convertMaxRadius), each a few map lookups plus, for an own
        // claim, one claim: cheap enough to do in one go on the server thread instead of OPAC's spread-out area task.
        int converted = 0, forceloaded = 0, notYours = 0, alreadyDone = 0, roleDenied = 0, overBudget = 0, other = 0;
        Component roleReason = null;
        Component otherMessage = null;
        ClaimResult.Type stoppedBy = null;
        boolean forceloadLimitReached = false;
        // The summary below says what did not fit, instead of one chat line per rejected claim
        claimManager.setBudgetMessagesSuppressed(true);
        try {
            // Ring by ring from the centre, so that the claims nearest to the player come first when the budget runs out
            for (int ring = 0; ring <= radius; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    // Inside the ring only its first and last row
                    int dzStep = Math.abs(dx) == ring ? 1 : 2 * ring;
                    for (int dz = -ring; dz <= ring; dz += dzStep) {
                        int x = centerX + dx;
                        int z = centerZ + dz;
                        IPlayerChunkClaimAPI claim = claimsManager.get(dimension, x, z);
                        if (claim == null) continue;
                        if (!playerId.equals(claim.getPlayerId())) {
                            notYours++;
                            continue;
                        }
                        if (claimManager.isTeamClaim(claim) == toTeam) {
                            alreadyDone++;
                            continue;
                        }
                        if (stoppedBy != null) {
                            //would be rejected the same way
                            if (isClaimLimit(stoppedBy)) overBudget++;
                            else other++;
                            continue;
                        }
                        boolean claimForceloaded = claim.isForceloadable();
                        if (claimForceloaded && forceloadLimitReached) {
                            //the forceload limit of the target budget is reached, claims that are not forceloaded may still fit
                            overBudget++;
                            continue;
                        }
                        ClaimResult<PlayerChunkClaim> result = claimsManager.tryToClaimHelper(dimension, playerId, targetSubIndex,
                                centerX, centerZ, x, z, claimForceloaded, false, false, claimLimit, ClaimingAction.CLAIM);
                        ClaimResult.Type type = result.getResultType();
                        if (type.success) {
                            converted++;
                            if (claimForceloaded) forceloaded++;
                            continue;
                        }
                        if (type == ClaimResult.Type.FORCELOAD_LIMIT_REACHED) {
                            // The forceload limit of the budget the claim would move into (the team's or the player's
                            // private one), which only stands in the way of the forceloaded claims
                            overBudget++;
                            forceloadLimitReached = true;
                            continue;
                        }
                        if (isClaimLimit(type)) {
                            // The claim limit of the budget the claim would move into
                            overBudget++;
                        } else if ((type == ClaimResult.Type.ADDON_FORBIDS || type == ClaimResult.Type.ADDON_INTERRUPTS)
                                && (TeamRoles.denyReason(server, playerId, toTeam ? null : playerId, roleAction) != null
                                // a forceloaded team claim also needs the forceload level (TeamRoles.checkClaim)
                                || toTeam && claimForceloaded
                                && TeamRoles.denyReason(server, playerId, null, TeamAction.FORCELOAD) != null)) {
                            roleDenied++;
                            if (roleReason == null) roleReason = result.getCustomReason();
                        } else {
                            other++;
                            if (otherMessage == null) otherMessage = result.getMessage();
                        }
                        // Like OPAC's area claims: a rejection that would be the same for every further claim ends it
                        if (type.interruptsAreaAction) stoppedBy = type;
                    }
                }
            }
        } finally {
            claimManager.setBudgetMessagesSuppressed(false);
        }

        Summary summary = new Summary(converted, forceloaded, notYours, alreadyDone, roleDenied, overBudget, other);
        report(source, localizer, viewer, summary, toTeam, targetSubId, roleReason, otherMessage,
                stoppedBy != null && isClaimLimit(stoppedBy), forceloadLimitReached, claimManager.getBudgetInfo(playerId));
        LOGGER.info("[TeamClaims] {} converted {} claim(s) of {} around [{}, {}] in {} to {} claims ({})", source.getTextName(),
                converted, playerId, centerX, centerZ, dimension, toTeam ? "team" : "personal", summary);
        return summary;
    }

    /** OPAC's two rejections for a claim limit: at the limit, and already above it. */
    private static boolean isClaimLimit(ClaimResult.Type type) {
        return type == ClaimResult.Type.CLAIM_LIMIT_REACHED || type == ClaimResult.Type.OVER_CLAIM_LIMIT;
    }

    /**
     * The summary for the player: what was converted, what was skipped and why (only the reasons that occurred), which
     * limit of the target budget stood in the way (with its numbers after the conversion), and the first role reason or
     * other rejection message. Two to five lines.
     */
    private static void report(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer viewer,
            Summary summary, boolean toTeam, @Nullable String targetSubId, @Nullable Component roleReason,
            @Nullable Component otherMessage, boolean stoppedAtClaimLimit, boolean forceloadLimitReached, BudgetInfo budget) {
        ChatFormatting doneStyle = summary.converted() > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        if (toTeam)
            send(source, localizer.getFor(viewer, KEY + "convert_done_team", String.valueOf(summary.converted()),
                    String.valueOf(summary.forceloaded())).withStyle(doneStyle));
        else
            send(source, localizer.getFor(viewer, KEY + "convert_done_personal", String.valueOf(summary.converted()),
                    String.valueOf(summary.forceloaded()), targetSubId).withStyle(doneStyle));

        List<Component> skipped = new ArrayList<>();
        addSkipped(skipped, localizer, viewer, "not_yours", summary.notYours());
        addSkipped(skipped, localizer, viewer, toTeam ? "already_team" : "already_personal", summary.alreadyDone());
        addSkipped(skipped, localizer, viewer, "role", summary.roleDenied());
        addSkipped(skipped, localizer, viewer, "budget", summary.overBudget());
        addSkipped(skipped, localizer, viewer, "other", summary.other());
        if (!skipped.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < skipped.size(); i++) {
                if (i > 0) list.append(", ");
                list.append(skipped.get(i));
            }
            send(source, localizer.getFor(viewer, KEY + "convert_skipped", list).withStyle(ChatFormatting.GRAY));
        }

        if (stoppedAtClaimLimit) {
            if (toTeam)
                send(source, localizer.getFor(viewer, KEY + "convert_stopped_team_limit", String.valueOf(budget.teamClaims()),
                        String.valueOf(budget.teamClaimLimit())).withStyle(ChatFormatting.RED));
            else
                send(source, localizer.getFor(viewer, KEY + "convert_stopped_private_limit", String.valueOf(budget.privateClaims()),
                        String.valueOf(budget.privateClaimLimit())).withStyle(ChatFormatting.RED));
        }
        if (forceloadLimitReached) {
            if (toTeam)
                send(source, localizer.getFor(viewer, KEY + "convert_forceload_team_limit", String.valueOf(budget.teamForceloads()),
                        String.valueOf(budget.teamForceloadLimit())).withStyle(ChatFormatting.RED));
            else
                send(source, localizer.getFor(viewer, KEY + "convert_forceload_private_limit", String.valueOf(budget.privateForceloads()),
                        String.valueOf(budget.privateForceloadLimit())).withStyle(ChatFormatting.RED));
        }
        if (roleReason != null)
            send(source, localizer.getFor(viewer, KEY + "convert_reason", roleReason).withStyle(ChatFormatting.RED));
        if (otherMessage != null)
            send(source, localizer.getFor(viewer, KEY + "convert_reason", otherMessage).withStyle(ChatFormatting.RED));
    }

    private static void addSkipped(List<Component> skipped, AdaptiveLocalizer localizer, @Nullable ServerPlayer viewer,
            String reason, int count) {
        if (count > 0) skipped.add(localizer.getFor(viewer, KEY + "convert_skipped_" + reason, String.valueOf(count)));
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void fail(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer viewer,
            String key, Object... args) {
        source.sendFailure(localizer.getFor(viewer, key, args).withStyle(ChatFormatting.RED));
    }
}
