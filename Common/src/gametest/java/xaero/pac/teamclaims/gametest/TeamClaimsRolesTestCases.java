package xaero.pac.teamclaims.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.result.api.AreaClaimResult;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.localization.api.IAdaptiveLocalizerAPI;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.TeamClaimsOverview;
import xaero.pac.teamclaims.TeamRoles;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamRole;

import java.util.List;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.CapturingCommandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.commandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.localized;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.assertLine;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.canRun;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.lines;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.removePlayerQuiet;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.run;

/**
 * Dev/test-only, loader-neutral test bodies for the team roles ({@link TeamRoles}, {@code /teamclaims roles}). Same
 * conventions as {@link TeamClaimsLogicTestCases} (offline players, a chunk offset of 1000 per test starting at 40000,
 * cleanup on every path, every test registered with the loader's empty structure template and the default timeout).
 * <p>
 * The claim actions go through OPAC's own non-forced API, so they take the same path as the claim commands and the
 * claim UI: {@code tryToClaim}/{@code tryToUnclaim}/{@code tryToForceload} for one chunk, and the synchronous area
 * variants, which run the same area task as the commands and the UI. A rejection must be
 * {@link ClaimResult.Type#ADDON_FORBIDS} with the localized role reason, which OPAC shows to the player itself.
 */
public final class TeamClaimsRolesTestCases {

    private static final String KEY = "gui.xaero_pac_team_claims_";

    private TeamClaimsRolesTestCases() {}

    // ==================== Tests ====================

    /**
     * With the default roles (all {@code member}), a MEMBER may make a team claim, unclaim a teammate's team claim and
     * their own, and turn the forceload of a teammate's team claim on and off, exactly as without roles.
     */
    public static void defaultRolesKeepBehaviour(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T40_Owner");
        GameProfile memberProfile = profile("T40_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 40000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
            for (TeamAction action : TeamAction.values())
                helper.assertTrue(teamConfig.getRequiredRole(action) == TeamRole.MEMBER,
                        "expected a new team to require member for " + action + ", got " + teamConfig.getRequiredRole(action));
            String subId = teamConfig.getSubConfigId();
            int ownerSub = teamSubIndexOf(server, subId, ownerId);
            int memberSub = teamSubIndexOf(server, subId, memberId);

            assertType(helper, doClaim(server, memberId, memberSub, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "the member's team claim");
            assertType(helper, doClaim(server, ownerId, ownerSub, x0 + 2, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "the owner's team claim");
            assertType(helper, doForceload(server, memberId, x0 + 2, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "the member's forceload of the owner's team claim");
            assertType(helper, doForceload(server, memberId, x0 + 2, 0, false), ClaimResult.Type.SUCCESSFUL_UNFORCELOAD,
                    "the member's unforceload of the owner's team claim");
            assertType(helper, doForceload(server, memberId, x0, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "the member's forceload of their own team claim");
            assertType(helper, doForceload(server, memberId, x0, 0, false), ClaimResult.Type.SUCCESSFUL_UNFORCELOAD,
                    "the member's unforceload of their own team claim");
            assertType(helper, doUnclaim(server, memberId, x0 + 2, 0), ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                    "the member's unclaim of the owner's team claim");
            assertType(helper, doUnclaim(server, memberId, x0, 0), ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                    "the member's unclaim of their own team claim");
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0, 0) == null && claimsAPI(server).get(OVERWORLD, x0 + 2, 0) == null,
                    "expected both team claims to be gone");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 2, 0);
            cleanup(server, party);
        }
    }

    /**
     * {@code claim = moderator}: a MEMBER's team claim is rejected with the role reason and changes nothing, for one
     * chunk and for an area; the same MEMBER's personal claim still works, and a MODERATOR's team claim works.
     */
    public static void claimRoleRejectsLowerRanks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T41_Owner");
        GameProfile memberProfile = profile("T41_Member");
        GameProfile moderatorProfile = profile("T41_Moderator");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        UUID moderatorId = moderatorProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 41000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile, moderatorProfile));
            party.setRank(party.getMemberInfo(moderatorId), PartyMemberRank.MODERATOR);
            setRoleAsOwner(helper, server, ownerId, TeamAction.CLAIM, TeamRole.MODERATOR);
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            int memberSub = teamSubIndexOf(server, subId, memberId);

            int memberCountBefore = claimsAPI(server).getPlayerInfo(memberId).getClaimCount();
            assertRoleDenied(helper, server, doClaim(server, memberId, memberSub, x0, 0), TeamAction.CLAIM, TeamRole.MODERATOR,
                    "the member's team claim");
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0, 0) == null, "expected the chunk to stay unclaimed");
            int memberCountAfter = claimsAPI(server).getPlayerInfo(memberId).getClaimCount();
            helper.assertTrue(memberCountAfter == memberCountBefore,
                    "expected the member's claim count to stay " + memberCountBefore + ", got " + memberCountAfter);

            @SuppressWarnings("deprecation")
            AreaClaimResult area = claimsAPI(server).tryToClaimArea(OVERWORLD, memberId, memberSub, x0 + 6, 0, x0 + 6, 0, x0 + 7, 0, false);
            assertAreaRoleDenied(helper, server, area, TeamAction.CLAIM, TeamRole.MODERATOR, "the member's area team claim");
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0 + 6, 0) == null && claimsAPI(server).get(OVERWORLD, x0 + 7, 0) == null,
                    "expected the area to stay unclaimed");

            assertType(helper, doClaim(server, memberId, -1, x0 + 2, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "the member's personal claim");
            assertType(helper, doClaim(server, moderatorId, teamSubIndexOf(server, subId, moderatorId), x0 + 4, 0),
                    ClaimResult.Type.SUCCESSFUL_CLAIM, "the moderator's team claim");
            helper.assertTrue(TeamClaimsCommon.getClaimManager().isTeamClaim(claimsAPI(server).get(OVERWORLD, x0 + 4, 0)),
                    "expected the moderator's claim to be a team claim");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 2, 0, x0 + 4, 0, x0 + 6, 0, x0 + 7, 0);
            cleanup(server, party);
        }
    }

    /**
     * {@code unclaim = admin}: a MEMBER can unclaim neither a teammate's team claim nor their own, for one chunk or an
     * area, nor take their own team claim away from the team by claiming it with their personal sub-config; their
     * personal claim they can still unclaim. An ADMIN can unclaim both team claims.
     */
    public static void unclaimRoleRejectsLowerRanks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T42_Owner");
        GameProfile adminProfile = profile("T42_Admin");
        GameProfile memberProfile = profile("T42_Member");
        UUID ownerId = ownerProfile.getId();
        UUID adminId = adminProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 42000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(adminProfile, memberProfile));
            party.setRank(party.getMemberInfo(adminId), PartyMemberRank.ADMIN);
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            assertType(helper, doClaim(server, ownerId, teamSubIndexOf(server, subId, ownerId), x0, 0),
                    ClaimResult.Type.SUCCESSFUL_CLAIM, "the owner's team claim");
            assertType(helper, doClaim(server, memberId, teamSubIndexOf(server, subId, memberId), x0 + 2, 0),
                    ClaimResult.Type.SUCCESSFUL_CLAIM, "the member's team claim");
            assertType(helper, doClaim(server, memberId, -1, x0 + 4, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "the member's personal claim");
            setRoleAsOwner(helper, server, ownerId, TeamAction.UNCLAIM, TeamRole.ADMIN);

            assertRoleDenied(helper, server, doUnclaim(server, memberId, x0, 0), TeamAction.UNCLAIM, TeamRole.ADMIN,
                    "the member's unclaim of the owner's team claim");
            assertRoleDenied(helper, server, doUnclaim(server, memberId, x0 + 2, 0), TeamAction.UNCLAIM, TeamRole.ADMIN,
                    "the member's unclaim of their own team claim");
            assertRoleDenied(helper, server, doClaim(server, memberId, -1, x0 + 2, 0), TeamAction.UNCLAIM, TeamRole.ADMIN,
                    "the member's personal claim over their own team claim");
            @SuppressWarnings("deprecation")
            AreaClaimResult area = claimsAPI(server).tryToUnclaimArea(OVERWORLD, memberId, x0, 0, x0, 0, x0 + 2, 0, false);
            assertAreaRoleDenied(helper, server, area, TeamAction.UNCLAIM, TeamRole.ADMIN, "the member's area unclaim");
            helper.assertTrue(TeamClaimsCommon.getClaimManager().isTeamClaim(claimsAPI(server).get(OVERWORLD, x0, 0))
                            && TeamClaimsCommon.getClaimManager().isTeamClaim(claimsAPI(server).get(OVERWORLD, x0 + 2, 0)),
                    "expected both team claims to still be team claims");

            assertType(helper, doUnclaim(server, memberId, x0 + 4, 0), ClaimResult.Type.SUCCESSFUL_UNCLAIM, "the member's unclaim of their personal claim");
            assertType(helper, doUnclaim(server, adminId, x0, 0), ClaimResult.Type.SUCCESSFUL_UNCLAIM, "the admin's unclaim of the owner's team claim");
            assertType(helper, doUnclaim(server, adminId, x0 + 2, 0), ClaimResult.Type.SUCCESSFUL_UNCLAIM, "the admin's unclaim of the member's team claim");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 2, 0, x0 + 4, 0);
            cleanup(server, party);
        }
    }

    /**
     * {@code forceload = owner}: an ADMIN can turn the forceload of neither their own nor the owner's team claim on, nor
     * off again; the owner can. The ADMIN's personal claim is not affected.
     */
    public static void forceloadRoleRejectsBelowOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T43_Owner");
        GameProfile adminProfile = profile("T43_Admin");
        UUID ownerId = ownerProfile.getId();
        UUID adminId = adminProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 43000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(adminProfile));
            party.setRank(party.getMemberInfo(adminId), PartyMemberRank.ADMIN);
            setRoleAsOwner(helper, server, ownerId, TeamAction.FORCELOAD, TeamRole.OWNER);
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            assertType(helper, doClaim(server, adminId, teamSubIndexOf(server, subId, adminId), x0, 0),
                    ClaimResult.Type.SUCCESSFUL_CLAIM, "the admin's team claim");
            assertType(helper, doClaim(server, ownerId, teamSubIndexOf(server, subId, ownerId), x0 + 2, 0),
                    ClaimResult.Type.SUCCESSFUL_CLAIM, "the owner's team claim");

            assertRoleDenied(helper, server, doForceload(server, adminId, x0, 0, true), TeamAction.FORCELOAD, TeamRole.OWNER,
                    "the admin's forceload of their own team claim");
            assertRoleDenied(helper, server, doForceload(server, adminId, x0 + 2, 0, true), TeamAction.FORCELOAD, TeamRole.OWNER,
                    "the admin's forceload of the owner's team claim");
            helper.assertTrue(!claimsAPI(server).get(OVERWORLD, x0, 0).isForceloadable(), "expected the admin's team claim not to be forceloaded");
            assertType(helper, doForceload(server, ownerId, x0, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "the owner's forceload of the admin's team claim");
            assertRoleDenied(helper, server, doForceload(server, adminId, x0, 0, false), TeamAction.FORCELOAD, TeamRole.OWNER,
                    "the admin's unforceload of their own team claim");
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0, 0).isForceloadable(), "expected the team claim to stay forceloaded");
            assertType(helper, doForceload(server, ownerId, x0, 0, false), ClaimResult.Type.SUCCESSFUL_UNFORCELOAD,
                    "the owner's unforceload of the admin's team claim");

            assertType(helper, doClaim(server, adminId, -1, x0 + 4, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "the admin's personal claim");
            assertType(helper, doForceload(server, adminId, x0 + 4, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "the admin's forceload of their personal claim");
            assertType(helper, doForceload(server, adminId, x0 + 4, 0, false), ClaimResult.Type.SUCCESSFUL_UNFORCELOAD,
                    "the admin's unforceload of their personal claim");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 2, 0, x0 + 4, 0);
            cleanup(server, party);
        }
    }

    /**
     * With every action at {@code owner}, forced actions (OPAC's admin mode / replace) of a MEMBER still claim with the
     * team sub-config, forceload and unclaim: roles never apply to them.
     */
    public static void forcedActionsBypassRoles(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T44_Owner");
        GameProfile memberProfile = profile("T44_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 44000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            for (TeamAction action : TeamAction.values()) setRoleAsOwner(helper, server, ownerId, action, TeamRole.OWNER);
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            int memberSub = teamSubIndexOf(server, subId, memberId);

            assertRoleDenied(helper, server, doClaim(server, memberId, memberSub, x0, 0), TeamAction.CLAIM, TeamRole.OWNER,
                    "the member's non-forced team claim");
            assertType(helper, claimsAPI(server).tryToClaim(OVERWORLD, memberId, memberSub, OVERWORLD, x0, 0, x0, 0, true),
                    ClaimResult.Type.SUCCESSFUL_CLAIM, "the member's forced team claim");
            helper.assertTrue(TeamClaimsCommon.getClaimManager().isTeamClaim(claimsAPI(server).get(OVERWORLD, x0, 0)),
                    "expected the forced claim to be a team claim");
            assertRoleDenied(helper, server, doForceload(server, memberId, x0, 0, true), TeamAction.FORCELOAD, TeamRole.OWNER,
                    "the member's non-forced forceload");
            assertType(helper, claimsAPI(server).tryToForceload(OVERWORLD, memberId, OVERWORLD, x0, 0, x0, 0, true, true),
                    ClaimResult.Type.SUCCESSFUL_FORCELOAD, "the member's forced forceload");
            assertType(helper, claimsAPI(server).tryToForceload(OVERWORLD, memberId, OVERWORLD, x0, 0, x0, 0, false, true),
                    ClaimResult.Type.SUCCESSFUL_UNFORCELOAD, "the member's forced unforceload");
            assertRoleDenied(helper, server, doUnclaim(server, memberId, x0, 0), TeamAction.UNCLAIM, TeamRole.OWNER,
                    "the member's non-forced unclaim");
            assertType(helper, claimsAPI(server).tryToUnclaim(OVERWORLD, memberId, OVERWORLD, x0, 0, x0, 0, true),
                    ClaimResult.Type.SUCCESSFUL_UNCLAIM, "the member's forced unclaim");
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0, 0) == null, "expected the chunk to be unclaimed");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0);
            cleanup(server, party);
        }
    }

    /**
     * {@code /teamclaims roles} as the vanilla mock player, a MEMBER: it shows the three levels, but setting one is
     * rejected and changes nothing. The owner's change works (also when repeated), and the mock player can change one
     * once promoted to ADMIN. {@code /teamclaims info} shows the roles line. The levels survive a JSON round trip, and
     * a team config without {@code roles} (or with unknown values) loads with the defaults.
     */
    public static void rolesCommandAndPersistence(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GameProfile ownerProfile = profile("T45_Owner");
        UUID ownerId = ownerProfile.getId();
        IServerPartyAPI party = null;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(player.getGameProfile()));
            TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
            String teamName = teamConfig.getTeamName() == null || teamConfig.getTeamName().isBlank()
                    ? TeamClaimsCommon.getTeamConfigManager().resolvePartyName(party) : teamConfig.getTeamName();

            helper.assertTrue(canRun(server, player, 0, "teamclaims roles") && canRun(server, player, 0, "teamclaims roles forceload owner"),
                    "expected /teamclaims roles to need no permission");
            helper.assertTrue(!canRun(server, player, 0, "teamclaims roles claim boss") && !canRun(server, player, 0, "teamclaims roles fly admin"),
                    "expected unknown actions and levels not to parse");

            CapturingCommandSource show = run(server, player, 0, "teamclaims roles");
            List<String> shown = lines(show);
            assertLine(helper, shown, localized(server, KEY + "roles_header", teamName));
            for (TeamAction action : TeamAction.values())
                assertLine(helper, shown, localized(server, KEY + "roles_entry", action.displayName(), TeamRole.MEMBER.displayName()));
            helper.assertTrue(!show.received(localized(server, KEY + "roles_hint")), "expected no change hint for a member, got " + shown);

            CapturingCommandSource denied = run(server, player, 0, "teamclaims roles claim admin");
            helper.assertTrue(denied.received(localized(server, KEY + "roles_admin_only")),
                    "expected a member's change to be rejected, got " + denied.all());
            helper.assertTrue(teamConfig.getRequiredRole(TeamAction.CLAIM) == TeamRole.MEMBER,
                    "expected the rejected change to change nothing, got " + teamConfig.getRequiredRole(TeamAction.CLAIM));

            setRoleAsOwner(helper, server, ownerId, TeamAction.CLAIM, TeamRole.ADMIN);
            helper.assertTrue(teamConfig.getRequiredRole(TeamAction.CLAIM) == TeamRole.ADMIN,
                    "expected the owner's change to set claim to admin, got " + teamConfig.getRequiredRole(TeamAction.CLAIM));
            CapturingCommandSource again = new CapturingCommandSource();
            int againResult = TeamRoles.setRole(commandSource(helper, again), null, ownerId, TeamAction.CLAIM, TeamRole.ADMIN);
            helper.assertTrue(againResult == 1 && again.received(localized(server, KEY + "roles_unchanged",
                            TeamAction.CLAIM.displayName(), TeamRole.ADMIN.displayName())),
                    "expected setting the same level again to report it unchanged, got " + again.all());

            party.setRank(party.getMemberInfo(player.getUUID()), PartyMemberRank.ADMIN);
            CapturingCommandSource adminSet = run(server, player, 0, "teamclaims roles unclaim moderator");
            helper.assertTrue(adminSet.received(localized(server, KEY + "roles_set", TeamAction.UNCLAIM.displayName(),
                            TeamRole.MODERATOR.displayName())),
                    "expected an admin's change to be confirmed, got " + adminSet.all());
            helper.assertTrue(teamConfig.getRequiredRole(TeamAction.UNCLAIM) == TeamRole.MODERATOR,
                    "expected the admin's change to set unclaim to moderator, got " + teamConfig.getRequiredRole(TeamAction.UNCLAIM));
            helper.assertTrue(run(server, player, 0, "teamclaims roles").received(localized(server, KEY + "roles_hint")),
                    "expected the change hint for an admin");

            CapturingCommandSource info = new CapturingCommandSource();
            TeamClaimsOverview.showInfo(commandSource(helper, info), null, ownerId, null);
            assertLine(helper, lines(info), localized(server, KEY + "info_roles", TeamRole.ADMIN.displayName(),
                    TeamRole.MODERATOR.displayName(), TeamRole.MEMBER.displayName()));

            TeamConfig copy = TeamConfig.fromJson(JsonParser.parseString(teamConfig.toJsonString()).getAsJsonObject());
            for (TeamAction action : TeamAction.values())
                helper.assertTrue(copy.getRequiredRole(action) == teamConfig.getRequiredRole(action),
                        "expected " + action + " to survive the JSON round trip as " + teamConfig.getRequiredRole(action)
                                + ", got " + copy.getRequiredRole(action) + " from " + teamConfig.toJsonString());

            UUID oldId = UUID.randomUUID();
            TeamConfig old = TeamConfig.fromJson(JsonParser.parseString("{\"partyId\":\"" + oldId + "\",\"teamName\":\"Old\","
                    + "\"subConfigId\":\"team_old\",\"members\":[],\"settings\":{}}").getAsJsonObject());
            for (TeamAction action : TeamAction.values())
                helper.assertTrue(old.getRequiredRole(action) == TeamRole.MEMBER,
                        "expected a team config without roles to require member for " + action + ", got " + old.getRequiredRole(action));
            TeamConfig odd = TeamConfig.fromJson(JsonParser.parseString("{\"partyId\":\"" + oldId + "\",\"teamName\":\"Odd\","
                    + "\"roles\":{\"claim\":\"boss\",\"unclaim\":\"admin\",\"fly\":\"OWNER\",\"forceload\":5}}").getAsJsonObject());
            helper.assertTrue(odd.getRequiredRole(TeamAction.CLAIM) == TeamRole.MEMBER && odd.getRequiredRole(TeamAction.UNCLAIM) == TeamRole.ADMIN
                            && odd.getRequiredRole(TeamAction.FORCELOAD) == TeamRole.MEMBER,
                    "expected unknown role values to fall back to member and known ones to load, got " + odd.toJsonString());
            helper.succeed();
        } finally {
            removePlayerQuiet(server, player);
            cleanup(server, party);
        }
    }

    // ==================== Helpers ====================

    /** Sets a role through {@link TeamRoles#setRole} as the party owner, the way the command does it. */
    private static void setRoleAsOwner(GameTestHelper helper, MinecraftServer server, UUID ownerId, TeamAction action, TeamRole role) {
        CapturingCommandSource capture = new CapturingCommandSource();
        int result = TeamRoles.setRole(commandSource(helper, capture), null, ownerId, action, role);
        helper.assertTrue(result == 1 && capture.received(localized(server, KEY + "roles_set", action.displayName(), role.displayName())),
                "expected the owner to set " + action + " to " + role + ", got result " + result + " and " + capture.all());
    }

    private static void assertType(GameTestHelper helper, ClaimResult<?> result, ClaimResult.Type expected, String what) {
        helper.assertTrue(result.getResultType() == expected, "expected " + what + " to give " + expected + ", got " + result.getResultType());
    }

    /** The server-side text of the role reason, which must be a real translation, not a key. */
    private static String roleReason(GameTestHelper helper, MinecraftServer server, TeamAction action, TeamRole required) {
        String reason = localized(server, KEY + "role_denied", action.displayName(), required.displayName());
        helper.assertTrue(!reason.contains("gui.xaero_pac"), "expected the role reason to be translated, got " + reason);
        return reason;
    }

    private static String serverText(MinecraftServer server, Component component) {
        IAdaptiveLocalizerAPI localizer = OpenPACServerAPI.get(server).getAdaptiveTextLocalizer();
        return localizer.getFor(null, component).getString();
    }

    /**
     * A single chunk rejection by a role: {@link ClaimResult.Type#ADDON_FORBIDS} with the role reason, which also ends
     * up in the message OPAC's claim commands send ({@link ClaimResult#getMessage()}).
     */
    private static void assertRoleDenied(GameTestHelper helper, MinecraftServer server, ClaimResult<?> result,
            TeamAction action, TeamRole required, String what) {
        assertType(helper, result, ClaimResult.Type.ADDON_FORBIDS, what);
        String expected = roleReason(helper, server, action, required);
        String message = serverText(server, result.getMessage());
        helper.assertTrue(message.contains(expected), "expected the message of " + what + " to contain '" + expected + "', got '" + message + "'");
        String reason = result.getCustomReason() == null ? null : serverText(server, result.getCustomReason());
        helper.assertTrue(expected.equals(reason), "expected the reason of " + what + " to be '" + expected + "', got '" + reason + "'");
    }

    /** An area rejection by a role: the result types have ADDON_FORBIDS and the reasons the role reason. */
    private static void assertAreaRoleDenied(GameTestHelper helper, MinecraftServer server, AreaClaimResult result,
            TeamAction action, TeamRole required, String what) {
        helper.assertTrue(result.getResultTypesStream().anyMatch(type -> type == ClaimResult.Type.ADDON_FORBIDS)
                        && result.getResultTypesStream().noneMatch(type -> type.success),
                "expected " + what + " to be forbidden and to do nothing, got " + result.getResultTypesStream().toList());
        String expected = roleReason(helper, server, action, required);
        List<String> reasons = result.getCustomReasons().stream().map(reason -> serverText(server, reason)).toList();
        helper.assertTrue(reasons.contains(expected), "expected the reasons of " + what + " to contain '" + expected + "', got " + reasons);
    }

    private static void cleanup(MinecraftServer server, IServerPartyAPI party) {
        if (party == null) return;
        TeamClaimsCommon.getClaimManager().deactivateTeamForceLoads(party.getId());
        disbandPartyQuiet(server, party.getId());
    }
}
