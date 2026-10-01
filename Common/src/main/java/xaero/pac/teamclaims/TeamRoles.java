package xaero.pac.teamclaims;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverrideType;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.localization.AdaptiveLocalizer;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;
import xaero.pac.teamclaims.config.TeamRole;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Team roles: the {@link TeamRole} a team member needs for each {@link TeamAction} on the team claims of their team,
 * as stored in the {@link TeamConfig} and set with {@code /teamclaims roles}. With every action at
 * {@link TeamRole#DEFAULT} nothing is ever rejected here.
 * <p>
 * Enforcement, only ever for non-forced actions (OPAC's admin mode, server claims, forced API calls and Team Claims'
 * own transfers skip both):
 * <ul>
 *     <li>claim: {@link #checkClaim}, from the bridge handler's {@code interceptClaim}, which is the only place that
 *     knows the sub-config of the claim. It also covers replacing a team claim of the own team with a personal claim
 *     ({@link #checkClaimOverTeamClaim}), which takes the claim away from the team just like an unclaim does.</li>
 *     <li>unclaim and forceload: the {@link Listener}, registered with OPAC's claim action listener API, which OPAC
 *     consults for every non-forced UNCLAIM, FORCELOAD and UNFORCELOAD.</li>
 * </ul>
 * Every rejection is {@link ClaimResult.Type#ADDON_FORBIDS} with a localized reason, which OPAC shows itself: in chat
 * for the claim commands (single chunk and area) and in the claim result packet for the claim UI.
 * <p>
 * Server-thread only.
 */
public final class TeamRoles {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "gui.xaero_pac_team_claims_";

    private TeamRoles() {}

    // ==================== Checks ====================

    /**
     * Why {@code playerId} may not do {@code action} on a team claim of their team, or null if they may (also when they
     * are in no team, or when {@code claimOwnerId} is not in their party, so it is no team claim of their team).
     * O(1): two hash lookups in OPAC's party manager and party, one in the team configs.
     *
     * @param claimOwnerId  the owner of the affected team claim, null for a new team claim
     */
    @Nullable
    public static Component denyReason(MinecraftServer server, UUID playerId, @Nullable UUID claimOwnerId, TeamAction action) {
        TeamConfigManager configManager = TeamClaimsCommon.getTeamConfigManager();
        if (configManager == null) return null;
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(playerId);
        if (party == null) return null;
        if (claimOwnerId != null && !claimOwnerId.equals(playerId) && party.getMemberInfo(claimOwnerId) == null) return null;
        TeamConfig teamConfig = configManager.getTeamConfig(party.getId());
        if (teamConfig == null) return null;
        TeamRole required = teamConfig.getRequiredRole(action);
        if (required == TeamRole.MEMBER) return null;//everybody may
        IPartyMemberAPI member = party.getMemberInfo(playerId);
        if (member == null || TeamRole.of(member).isAtLeast(required)) return null;
        // A new instance every time: OPAC's server-side translation for players without the mod rewrites its arguments
        return Component.translatable(KEY + "role_denied", action.displayName(), required.displayName());
    }

    /** A new team claim (claim with the team sub-config) by {@code playerId}: null if allowed, else the rejection. */
    @Nullable
    static ClaimResult<PlayerChunkClaim> checkClaim(MinecraftServer server, UUID playerId) {
        return forbidden(denyReason(server, playerId, null, TeamAction.CLAIM));
    }

    /**
     * A claim that is not a team claim replacing {@code existing}: if that is a team claim of the player's team, it
     * leaves the team, so it needs the unclaim level. Null if allowed (always for any other existing claim).
     */
    @Nullable
    static ClaimResult<PlayerChunkClaim> checkClaimOverTeamClaim(TeamClaimManager claimManager, MinecraftServer server,
            UUID playerId, @Nullable PlayerChunkClaim existing) {
        if (existing == null || !claimManager.isTeamClaim(existing)) return null;
        return forbidden(denyReason(server, playerId, existing.getPlayerId(), TeamAction.UNCLAIM));
    }

    @Nullable
    private static ClaimResult<PlayerChunkClaim> forbidden(@Nullable Component reason) {
        return reason == null ? null : new ClaimResult<>(null, ClaimResult.Type.ADDON_FORBIDS, reason);
    }

    // ==================== Claim action listener ====================

    /**
     * Forbids UNCLAIM, FORCELOAD and UNFORCELOAD on a team claim of the acting player's team when their level is below
     * what the team requires. Registered once per server start by {@link TeamClaimsCommon#onAddonRegister} (OPAC creates
     * a new listener manager for every server). Returns {@code currentOverride} itself in every other case, as OPAC
     * recommends, and does nothing once Team Claims has stopped (the managers are null then).
     */
    static final class Listener implements IClaimActionListenerAPI {

        @Nonnull
        @Override
        public String getName() {
            return "Team Claims roles";
        }

        @Nonnull
        @Override
        public ClaimActionPermissionOverride overrideClaimingActionPermission(@Nonnull UUID playerId,
                @Nonnull ResourceLocation dim, int x, int z, @Nonnull ClaimingAction action,
                @Nonnull IServerClaimsManagerAPI claimsManagerAPI, @Nonnull ClaimActionPermissionOverride currentOverride,
                @Nonnull MinecraftServer server) {
            TeamAction teamAction = switch (action) {
                case UNCLAIM -> TeamAction.UNCLAIM;
                case FORCELOAD, UNFORCELOAD -> TeamAction.FORCELOAD;
                default -> null;//claims are checked in interceptClaim, which knows the sub-config
            };
            if (teamAction == null || currentOverride.getType() == ClaimActionPermissionOverrideType.FORBID)
                return currentOverride;
            TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
            if (claimManager == null) return currentOverride;
            IPlayerChunkClaimAPI claim = claimsManagerAPI.get(dim, x, z);
            if (claim == null || !claimManager.isTeamClaim(claim)) return currentOverride;
            Component reason = denyReason(server, playerId, claim.getPlayerId(), teamAction);
            return reason == null ? currentOverride
                    : new ClaimActionPermissionOverride(ClaimActionPermissionOverrideType.FORBID, reason);
        }

        @Override
        public void handleSuccessfulClaimingAction(@Nonnull UUID playerId, @Nonnull ResourceLocation dim, int x, int z,
                @Nonnull ClaimingAction action, @Nonnull IServerClaimsManagerAPI claimsManagerAPI,
                @Nonnull MinecraftServer server) {
        }
    }

    // ==================== /teamclaims roles ====================

    /**
     * {@code roles} shows the levels of the caller's team (any member), {@code roles <action> <level>} sets one (the
     * party owner and admins only). Both the actions and the levels are literals, so they are completed and nothing
     * else parses.
     */
    static LiteralArgumentBuilder<CommandSourceStack> rolesNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("roles").executes(TeamRoles::executeShow);
        for (TeamAction action : TeamAction.values()) {
            LiteralArgumentBuilder<CommandSourceStack> actionNode = Commands.literal(action.id());
            for (TeamRole role : TeamRole.values())
                actionNode.then(Commands.literal(role.id()).executes(context -> executeSet(context, action, role)));
            node.then(actionNode);
        }
        return node;
    }

    private static int executeShow(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = TeamClaimsCommands.requirePlayer(context.getSource());
        if (player == null) return 0;
        return showRoles(context.getSource(), player, player.getUUID());
    }

    private static int executeSet(CommandContext<CommandSourceStack> context, TeamAction action, TeamRole role) {
        ServerPlayer player = TeamClaimsCommands.requirePlayer(context.getSource());
        if (player == null) return 0;
        return setRole(context.getSource(), player, player.getUUID(), action, role);
    }

    /**
     * Sends the levels of the team of {@code subjectId} to {@code source}, plus how to change them if the subject may.
     *
     * @param viewer  the player running the command, null when there is none
     * @return 1 on success, 0 if the player is in no team
     */
    public static int showRoles(CommandSourceStack source, @Nullable ServerPlayer viewer, UUID subjectId) {
        TeamConfigManager configManager = TeamClaimsCommon.getTeamConfigManager();
        MinecraftServer server = source.getServer();
        IServerData<?, ?> serverData = ServerData.from(server);
        if (configManager == null || serverData == null) return 0;
        AdaptiveLocalizer localizer = serverData.getAdaptiveLocalizer();
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(subjectId);
        TeamConfig teamConfig = party == null ? null : configManager.getTeamConfig(party.getId());
        if (teamConfig == null) {
            fail(source, localizer, viewer, KEY + "info_no_team");
            return 0;
        }
        String teamName = teamConfig.getTeamName() == null || teamConfig.getTeamName().isBlank()
                ? configManager.resolvePartyName(party) : teamConfig.getTeamName();
        send(source, localizer, viewer, KEY + "roles_header", ChatFormatting.GRAY,
                Component.literal(teamName).withStyle(ChatFormatting.GOLD));
        for (TeamAction action : TeamAction.values())
            send(source, localizer, viewer, KEY + "roles_entry", ChatFormatting.GRAY, action.displayName(),
                    teamConfig.getRequiredRole(action).displayName());
        if (configManager.isPlayerTeamAdmin(subjectId))
            send(source, localizer, viewer, KEY + "roles_hint", ChatFormatting.GRAY);
        return 1;
    }

    /**
     * Sets the level the team of {@code actorId} requires for {@code action}, if they are its owner or an admin, then
     * confirms it to {@code source} and tells the other online members.
     *
     * @param viewer  the player running the command, null when there is none
     * @return 1 on success (also when the level already was {@code role}), 0 if not allowed or in no team
     */
    public static int setRole(CommandSourceStack source, @Nullable ServerPlayer viewer, UUID actorId, TeamAction action,
            TeamRole role) {
        TeamConfigManager configManager = TeamClaimsCommon.getTeamConfigManager();
        MinecraftServer server = source.getServer();
        IServerData<?, ?> serverData = ServerData.from(server);
        if (configManager == null || serverData == null) return 0;
        AdaptiveLocalizer localizer = serverData.getAdaptiveLocalizer();
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(actorId);
        TeamConfig teamConfig = party == null ? null : configManager.getTeamConfig(party.getId());
        if (teamConfig == null) {
            fail(source, localizer, viewer, KEY + "info_no_team");
            return 0;
        }
        if (!configManager.isPlayerTeamAdmin(actorId)) {
            fail(source, localizer, viewer, KEY + "roles_admin_only");
            return 0;
        }
        if (!teamConfig.setRequiredRole(action, role)) {
            send(source, localizer, viewer, KEY + "roles_unchanged", ChatFormatting.YELLOW, action.displayName(), role.displayName());
            return 1;
        }
        configManager.markDirty(teamConfig);
        send(source, localizer, viewer, KEY + "roles_set", ChatFormatting.GREEN, action.displayName(), role.displayName());
        IPartyMemberAPI actor = party.getMemberInfo(actorId);
        String actorName = actor == null ? String.valueOf(actorId) : actor.getUsername();
        party.getOnlineMemberStream().filter(member -> !member.getUUID().equals(actorId)).forEach(member ->
                member.sendSystemMessage(localizer.getFor(member, KEY + "roles_notice", actorName, action.displayName(),
                        role.displayName()).withStyle(ChatFormatting.GRAY)));
        LOGGER.info("[TeamClaims] {} set the role needed to {} in party {} to {}", actorName, action.id(), party.getId(), role.id());
        return 1;
    }

    private static void send(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer viewer,
            String key, ChatFormatting style, Object... args) {
        MutableComponent message = localizer.getFor(viewer, key, args).withStyle(style);
        source.sendSuccess(() -> message, false);
    }

    private static void fail(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer viewer,
            String key, Object... args) {
        source.sendFailure(localizer.getFor(viewer, key, args).withStyle(ChatFormatting.RED));
    }
}
