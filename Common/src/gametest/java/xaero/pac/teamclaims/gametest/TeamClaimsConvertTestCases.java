package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.BudgetInfo;
import xaero.pac.teamclaims.TeamClaimManager.BudgetSettings;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.TeamForceLoadHandler;
import xaero.pac.teamclaims.TeamRoles;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
import xaero.pac.teamclaims.config.TeamRole;

import java.util.List;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.CapturingCommandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.commandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.localized;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.canRun;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.removePlayerQuiet;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.run;

/**
 * Dev/test-only, loader-neutral test bodies for {@code /teamclaims convert <toteam|topersonal> [radius]}
 * ({@link xaero.pac.teamclaims.TeamClaimsConvert}). Same conventions as {@link TeamClaimsLogicTestCases} (a chunk offset
 * of 1000 per test starting at 46000, cleanup on every path, every test registered with the loader's empty structure
 * template and the default timeout).
 * <p>
 * The converting player is the vanilla mock player, a MEMBER of a team owned by an offline player, standing in chunk
 * {@code (x0, 0)} and running the real command. Being online, they keep the team forceloads active and OPAC's own
 * forceload tickets of their claims enabled. The claims the tests start from are made with OPAC's low-level
 * {@code claim} (no checks), so the setup doesn't depend on the claim rules.
 */
public final class TeamClaimsConvertTestCases {

    private static final String KEY = "gui.xaero_pac_team_claims_";

    private TeamClaimsConvertTestCases() {}

    // ==================== Tests ====================

    /**
     * {@code convert toteam 1}: the player's five personal claims in the 3x3 area become team claims, one of them
     * forceloaded, which keeps its OPAC ticket and is now a team forceload with a team ticket. The player's own team
     * claim there is "already" one, the owner's two claims in the area are skipped and stay as they are, and the
     * player's claim just outside the area stays personal. The converted claims (and the forceload) move from the
     * player's private budget into the team's.
     */
    public static void toTeamConvertsOwnClaims(GameTestHelper helper) {
        Setup s = Setup.create(helper, "T46", 46000);
        int x0 = s.x0;
        try {
            int playerTeamSub = s.teamSub(s.playerId);
            int ownerTeamSub = s.teamSub(s.ownerId);
            s.claim(s.playerId, -1, x0 - 1, -1, false);
            s.claim(s.playerId, -1, x0, -1, false);
            s.claim(s.playerId, -1, x0 + 1, -1, false);
            s.claim(s.playerId, -1, x0 - 1, 0, false);
            s.claim(s.playerId, -1, x0, 0, true);
            s.claim(s.playerId, -1, x0 + 2, 0, false);//outside the area
            s.claim(s.playerId, playerTeamSub, x0 - 1, 1, false);//already a team claim
            s.claim(s.ownerId, -1, x0 + 1, 0, false);//a teammate's personal claim
            s.claim(s.ownerId, ownerTeamSub, x0 + 1, 1, false);//a teammate's team claim
            // the team 2 claims; private: the player 6 claims, 1 of them forceloaded, the owner 1
            s.assertNumbers("before", 2, 0, 6, 1, 1, 0);
            helper.assertTrue(s.opacTicketEnabled(x0, 0), "test precondition: expected OPAC's ticket of the forceloaded claim to be enabled");
            helper.assertTrue(!s.handler.hasTicket(OVERWORLD, x0, 0), "test precondition: expected no team ticket for a personal claim");

            CapturingCommandSource out = run(s.server, s.player, 0, "teamclaims convert toteam 1");
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_done_team", "5", "1")),
                    "expected 5 converted claims, 1 of them forceloaded, got " + out.all());
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_skipped_not_yours", "2"))
                            && out.received(localized(s.server, KEY + "convert_skipped_already_team", "1"))
                            && !out.received(localized(s.server, KEY + "convert_skipped_budget", "")),
                    "expected 2 claims skipped as not yours and 1 as already a team claim, got " + out.all());

            for (int[] pos : new int[][]{{x0 - 1, -1}, {x0, -1}, {x0 + 1, -1}, {x0 - 1, 0}, {x0, 0}, {x0 - 1, 1}})
                s.assertClaim(pos[0], pos[1], s.playerId, playerTeamSub, pos[0] == x0 && pos[1] == 0);
            s.assertClaim(x0 + 2, 0, s.playerId, -1, false);
            s.assertClaim(x0 + 1, 0, s.ownerId, -1, false);
            s.assertClaim(x0 + 1, 1, s.ownerId, ownerTeamSub, false);
            // the team 7 claims, 1 forceloaded; private: the player 1 claim, the owner 1; the forceload moved into the team
            s.assertNumbers("after", 7, 1, 1, 1, 0, 0);
            TeamClaimManager.TeamData teamData = TeamClaimsCommon.getClaimManager().getTeamData(s.party.getId());
            helper.assertTrue(teamData.isForceloaded(new TeamClaimManager.ClaimPos(OVERWORLD, x0, 0))
                            && teamData.getForceloadCountOf(s.playerId) == 1,
                    "expected the converted forceloaded claim to be a tracked team forceload of the player");
            helper.assertTrue(s.opacTicketEnabled(x0, 0), "expected OPAC's ticket of the converted claim to stay enabled");
            helper.assertTrue(s.handler.hasTicket(OVERWORLD, x0, 0), "expected the converted claim to have a team ticket now");
            helper.succeed();
        } finally {
            s.cleanup();
        }
    }

    /**
     * {@code convert topersonal 1} with the team sub-config selected as the player's sub-claim: the player's three team
     * claims in the area become claims of their main config, the forceloaded one stays forceloaded with OPAC's ticket
     * (the team ticket is gone, the chunk is still loaded a few ticks later), and the team totals go down. The owner's
     * team claim in the area and the player's personal claim are skipped.
     */
    public static void toPersonalConvertsBack(GameTestHelper helper) {
        Setup s = Setup.create(helper, "T47", 47000);
        int x0 = s.x0;
        boolean waiting = false;
        try {
            int playerTeamSub = s.teamSub(s.playerId);
            s.claim(s.playerId, playerTeamSub, x0 - 1, 0, false);
            s.claim(s.playerId, playerTeamSub, x0, 0, true);
            s.claim(s.playerId, playerTeamSub, x0 + 1, 0, false);
            s.claim(s.playerId, -1, x0, 1, false);//already personal
            s.claim(s.ownerId, s.teamSub(s.ownerId), x0, -1, false);//the owner's team claim
            IPlayerConfigAPI playerConfig = configManager(s.server).getLoadedConfig(s.playerId);
            String teamSubId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(s.party.getId()).getSubConfigId();
            IPlayerConfigAPI.SetResult used = playerConfig.tryToSet(PlayerConfigOptions.USED_SUBCLAIM, teamSubId);
            helper.assertTrue(used == IPlayerConfigAPI.SetResult.SUCCESS, "expected selecting the team sub-claim to work, got " + used);
            // the team 4 claims, 1 forceloaded; private: the player 1 claim, the owner none
            s.assertNumbers("before", 4, 1, 1, 0, 0, 0);
            helper.assertTrue(s.handler.hasTicket(OVERWORLD, x0, 0) && s.opacTicketEnabled(x0, 0),
                    "test precondition: expected the forceloaded team claim to have a team ticket and OPAC's ticket");

            CapturingCommandSource out = run(s.server, s.player, 0, "teamclaims convert topersonal 1");
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_done_personal", "3", "1", "main")),
                    "expected 3 claims converted into the main config, 1 of them forceloaded, got " + out.all());
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_skipped_not_yours", "1"))
                            && out.received(localized(s.server, KEY + "convert_skipped_already_personal", "1")),
                    "expected 1 claim skipped as not yours and 1 as already personal, got " + out.all());

            for (int dx = -1; dx <= 1; dx++) s.assertClaim(x0 + dx, 0, s.playerId, -1, dx == 0);
            s.assertClaim(x0, -1, s.ownerId, s.teamSub(s.ownerId), false);
            // the team 1 claim; private: the player 4 claims, 1 of them forceloaded; the forceload left the team
            s.assertNumbers("after", 1, 0, 4, 0, 1, 0);
            helper.assertTrue(!s.handler.hasTicket(OVERWORLD, x0, 0), "expected the team ticket of the converted claim to be gone");
            helper.assertTrue(s.opacTicketEnabled(x0, 0), "expected OPAC's ticket of the converted claim to stay enabled");
            waiting = true;
            helper.runAfterDelay(3, () -> {
                try {
                    int level = ticketLevelOf(helper, x0, 0);
                    helper.assertTrue(level <= 31, "expected the converted claim to stay forceloaded (ticket level <= 31), got " + level);
                    helper.succeed();
                } finally {
                    resetUsedSubClaimQuiet(playerConfig);
                    s.cleanup();
                }
            });
        } finally {
            if (!waiting) {
                resetUsedSubClaimQuiet(configManager(s.server).getLoadedConfig(s.playerId));
                s.cleanup();
            }
        }
    }

    /**
     * The team can only take 2 more team claims: {@code convert toteam 1} converts the two claims nearest to the player
     * (the centre first), then stops at the team claim limit, and the remaining two claims count as over the limit
     * without being tried. The team ends up exactly at its limit. The player's private budget does not matter for it:
     * the conversion works although the player is above a private claim limit of 0.
     */
    public static void toTeamStopsAtTeamLimit(GameTestHelper helper) {
        Setup s = Setup.create(helper, "T48", 48000);
        int x0 = s.x0;
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        try {
            cm.setBudgetSettingsOverride(s.party.getId(), new BudgetSettings(2, 2, 0, 10, 0, 168));
            s.claim(s.playerId, -1, x0, 0, false);
            s.claim(s.playerId, -1, x0 - 1, -1, false);
            s.claim(s.playerId, -1, x0 - 1, 0, false);
            s.claim(s.playerId, -1, x0 + 1, 1, false);
            s.setPrivateLimit(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, claimsAPI(s.server).getPlayerFullClaimLimit(s.playerId), 0);

            CapturingCommandSource out = run(s.server, s.player, 0, "teamclaims convert toteam 1");
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_done_team", "2", "0"))
                            && out.received(localized(s.server, KEY + "convert_skipped_budget", "2"))
                            && out.received(localized(s.server, KEY + "convert_stopped_team_limit", "2", "2")),
                    "expected 2 conversions, then a stop at the team claim limit with 2 claims over it, got " + out.all());
            int playerTeamSub = s.teamSub(s.playerId);
            s.assertClaim(x0, 0, s.playerId, playerTeamSub, false);
            s.assertClaim(x0 - 1, -1, s.playerId, playerTeamSub, false);
            s.assertClaim(x0 - 1, 0, s.playerId, -1, false);
            s.assertClaim(x0 + 1, 1, s.playerId, -1, false);
            BudgetInfo budget = cm.getBudgetInfo(s.playerId);
            helper.assertTrue(budget.teamClaims() == 2 && budget.teamClaimLimit() == 2 && budget.privateClaims() == 2,
                    "expected the team to be exactly at its limit of 2 and 2 private claims to be left, got " + budget);
            helper.succeed();
        } finally {
            s.cleanup();
        }
    }

    /**
     * {@code convert topersonal} needs room in the converting player's private budget, whatever the team's is: with a
     * private claim limit of 2 (one private claim already there) and a private forceload limit of 0, the forceloaded
     * team claim in the centre is skipped for the forceload limit (it would become a private forceload), the next team
     * claim is converted, and the one after that stops the conversion at the private claim limit.
     */
    public static void toPersonalStopsAtPrivateLimit(GameTestHelper helper) {
        Setup s = Setup.create(helper, "T52", 52000);
        int x0 = s.x0;
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        try {
            int playerTeamSub = s.teamSub(s.playerId);
            s.claim(s.playerId, playerTeamSub, x0, 0, true);
            s.claim(s.playerId, playerTeamSub, x0 - 1, 0, false);
            s.claim(s.playerId, playerTeamSub, x0 + 1, 0, false);
            s.claim(s.playerId, -1, x0, 1, false);//already personal
            s.setPrivateLimit(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, claimsAPI(s.server).getPlayerFullClaimLimit(s.playerId), 2);
            s.setPrivateLimit(PlayerConfigOptions.BONUS_CHUNK_FORCELOADS, claimsAPI(s.server).getPlayerFullForceloadLimit(s.playerId), 0);

            CapturingCommandSource out = run(s.server, s.player, 0, "teamclaims convert topersonal 1");
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_done_personal", "1", "0", "main"))
                            && out.received(localized(s.server, KEY + "convert_skipped_already_personal", "1"))
                            && out.received(localized(s.server, KEY + "convert_skipped_budget", "2")),
                    "expected 1 conversion, 1 claim already personal and 2 over a limit, got " + out.all());
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_stopped_private_limit", "2", "2"))
                            && out.received(localized(s.server, KEY + "convert_forceload_private_limit", "0", "0")),
                    "expected the stop at the private claim limit and the private forceload limit to be reported, got " + out.all());
            s.assertClaim(x0, 0, s.playerId, playerTeamSub, true);
            s.assertClaim(x0 - 1, 0, s.playerId, -1, false);
            s.assertClaim(x0 + 1, 0, s.playerId, playerTeamSub, false);
            BudgetInfo budget = cm.getBudgetInfo(s.playerId);
            helper.assertTrue(budget.privateClaims() == 2 && budget.privateForceloads() == 0 && budget.teamClaims() == 2
                            && budget.teamForceloads() == 1,
                    "expected 2 private claims without a forceload and 2 team claims, 1 of them forceloaded, got " + budget);
            helper.succeed();
        } finally {
            s.cleanup();
        }
    }

    /**
     * {@code convert toteam} of a forceloaded claim needs room in the team's forceload budget: with a team forceload
     * limit of 0 the forceloaded claim stays private and forceloaded, while the plain claim next to it still becomes a
     * team claim (the forceload limit only stands in the way of forceloaded claims).
     */
    public static void toTeamSkipsForceloadedAtTeamForceloadLimit(GameTestHelper helper) {
        Setup s = Setup.create(helper, "T53", 53000);
        int x0 = s.x0;
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        try {
            cm.setBudgetSettingsOverride(s.party.getId(), new BudgetSettings(2, 10, 0, 0, 0, 168));
            s.claim(s.playerId, -1, x0, 0, true);
            s.claim(s.playerId, -1, x0 + 1, 0, false);

            CapturingCommandSource out = run(s.server, s.player, 0, "teamclaims convert toteam 1");
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_done_team", "1", "0"))
                            && out.received(localized(s.server, KEY + "convert_skipped_budget", "1"))
                            && out.received(localized(s.server, KEY + "convert_forceload_team_limit", "0", "0")),
                    "expected the plain claim to convert and the forceloaded one to be skipped for the team forceload limit, got "
                            + out.all());
            helper.assertTrue(!out.received(localized(s.server, KEY + "convert_stopped_team_limit", "1", "10")),
                    "expected no stop at the team claim limit, got " + out.all());
            s.assertClaim(x0, 0, s.playerId, -1, true);
            s.assertClaim(x0 + 1, 0, s.playerId, s.teamSub(s.playerId), false);
            BudgetInfo budget = cm.getBudgetInfo(s.playerId);
            helper.assertTrue(budget.teamClaims() == 1 && budget.teamForceloads() == 0 && budget.privateForceloads() == 1,
                    "expected 1 team claim, no team forceload and the private forceload to be kept, got " + budget);
            helper.succeed();
        } finally {
            s.cleanup();
        }
    }

    /**
     * With {@code claim = admin} and {@code unclaim = admin}, the MEMBER's {@code toteam} converts none of their two
     * personal claims and {@code topersonal} none of their team claim, each reporting the role reason; nothing changes.
     */
    public static void conversionNeedsTeamRoles(GameTestHelper helper) {
        Setup s = Setup.create(helper, "T49", 49000);
        int x0 = s.x0;
        try {
            setRoleAsOwner(helper, s, TeamAction.CLAIM, TeamRole.ADMIN);
            setRoleAsOwner(helper, s, TeamAction.UNCLAIM, TeamRole.ADMIN);
            int playerTeamSub = s.teamSub(s.playerId);
            s.claim(s.playerId, -1, x0, 0, false);
            s.claim(s.playerId, -1, x0 + 1, 0, true);
            s.claim(s.playerId, playerTeamSub, x0, 1, false);

            CapturingCommandSource toTeam = run(s.server, s.player, 0, "teamclaims convert toteam 1");
            helper.assertTrue(toTeam.received(localized(s.server, KEY + "convert_done_team", "0", "0"))
                            && toTeam.received(localized(s.server, KEY + "convert_skipped_role", "2"))
                            && toTeam.received(localized(s.server, KEY + "role_denied", TeamAction.CLAIM.displayName(),
                            TeamRole.ADMIN.displayName())),
                    "expected toteam to convert nothing and report the claim role, got " + toTeam.all());
            CapturingCommandSource toPersonal = run(s.server, s.player, 0, "teamclaims convert topersonal 1");
            helper.assertTrue(toPersonal.received(localized(s.server, KEY + "convert_done_personal", "0", "0", "main"))
                            && toPersonal.received(localized(s.server, KEY + "convert_skipped_role", "1"))
                            && toPersonal.received(localized(s.server, KEY + "role_denied", TeamAction.UNCLAIM.displayName(),
                            TeamRole.ADMIN.displayName())),
                    "expected topersonal to convert nothing and report the unclaim role, got " + toPersonal.all());

            s.assertClaim(x0, 0, s.playerId, -1, false);
            s.assertClaim(x0 + 1, 0, s.playerId, -1, true);
            s.assertClaim(x0, 1, s.playerId, playerTeamSub, false);
            helper.succeed();
        } finally {
            s.cleanup();
        }
    }

    /**
     * With {@code claim = member} and {@code forceload = admin}, the MEMBER's {@code toteam} converts their plain
     * personal claim, but not their forceloaded one: making it a team claim would add a team forceload, which needs the
     * forceload level. That one is skipped for the role with the forceload reason and stays personal and forceloaded.
     */
    public static void toTeamForceloadedNeedsForceloadRole(GameTestHelper helper) {
        Setup s = Setup.create(helper, "T51", 51000);
        int x0 = s.x0;
        try {
            setRoleAsOwner(helper, s, TeamAction.FORCELOAD, TeamRole.ADMIN);
            s.claim(s.playerId, -1, x0, 0, false);
            s.claim(s.playerId, -1, x0 + 1, 0, true);

            CapturingCommandSource out = run(s.server, s.player, 0, "teamclaims convert toteam 1");
            helper.assertTrue(out.received(localized(s.server, KEY + "convert_done_team", "1", "0"))
                            && out.received(localized(s.server, KEY + "convert_skipped_role", "1"))
                            && out.received(localized(s.server, KEY + "role_denied", TeamAction.FORCELOAD.displayName(),
                            TeamRole.ADMIN.displayName())),
                    "expected the plain claim to convert and the forceloaded one to be skipped for the forceload role, got " + out.all());
            helper.assertTrue(!out.received(localized(s.server, KEY + "role_denied", TeamAction.CLAIM.displayName(),
                            TeamRole.ADMIN.displayName())),
                    "expected no claim role reason, got " + out.all());
            s.assertClaim(x0, 0, s.playerId, s.teamSub(s.playerId), false);
            s.assertClaim(x0 + 1, 0, s.playerId, -1, true);
            helper.assertTrue(!s.handler.hasTicket(OVERWORLD, x0 + 1, 0) && s.opacTicketEnabled(x0 + 1, 0),
                    "expected the skipped claim to keep only OPAC's forceload ticket");
            helper.succeed();
        } finally {
            s.cleanup();
        }
    }

    /**
     * A radius above {@code convertMaxRadius} is rejected with the configured maximum (also after changing it, which
     * applies right away), a negative one doesn't parse, and a player in no team gets the "not in a team" failure.
     */
    public static void convertRadiusAndTeamChecks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TeamClaimsServerConfig config = TeamClaimsServerConfig.CONFIG;
        int maxBefore = config.convertMaxRadius.get();
        IServerPartyAPI party = null;
        try {
            helper.assertTrue(config.convertMaxRadius.getDefault() == 4, "expected the default of 'convertMaxRadius' to be 4, got "
                    + config.convertMaxRadius.getDefault());
            helper.assertTrue(canRun(server, player, 0, "teamclaims convert toteam") && canRun(server, player, 0, "teamclaims convert topersonal 3"),
                    "expected /teamclaims convert to need no permission");
            helper.assertTrue(!canRun(server, player, 0, "teamclaims convert toteam -1"), "expected a negative radius not to parse");

            CapturingCommandSource noTeam = run(server, player, 0, "teamclaims convert toteam");
            helper.assertTrue(noTeam.received(localized(server, KEY + "info_no_team")),
                    "expected a player in no team to be told so, got " + noTeam.all());

            party = createPartyWithTeam(server, profile("T50_Owner"), List.of(player.getGameProfile()));
            player.setPos(50000 * 16 + 8, 64, 8);
            CapturingCommandSource tooLarge = run(server, player, 0, "teamclaims convert toteam " + (maxBefore + 1));
            helper.assertTrue(tooLarge.received(localized(server, KEY + "convert_radius_too_large", String.valueOf(maxBefore))),
                    "expected a radius above the maximum to be rejected, got " + tooLarge.all());
            config.convertMaxRadius.set(1);
            CapturingCommandSource lowered = run(server, player, 0, "teamclaims convert topersonal 2");
            helper.assertTrue(lowered.received(localized(server, KEY + "convert_radius_too_large", "1")),
                    "expected the lowered maximum to apply right away, got " + lowered.all());
            CapturingCommandSource fits = run(server, player, 0, "teamclaims convert toteam 1");
            helper.assertTrue(fits.received(localized(server, KEY + "convert_done_team", "0", "0")),
                    "expected a radius within the maximum to be used (nothing to convert), got " + fits.all());
            helper.succeed();
        } finally {
            config.convertMaxRadius.set(maxBefore);
            removePlayerQuiet(server, player);
            if (party != null) disbandPartyQuiet(server, party.getId());
        }
    }

    // ==================== Helpers ====================

    /**
     * A team owned by an offline player with the mock player as a MEMBER, standing in chunk {@code (x0, 0)}.
     * {@link #cleanup} unclaims the 5x5 chunks around it and removes everything again.
     */
    private static final class Setup {
        final GameTestHelper helper;
        final MinecraftServer server;
        final ServerPlayer player;
        final UUID playerId;
        final UUID ownerId;
        final IServerPartyAPI party;
        final TeamForceLoadHandler handler = TeamClaimsCommon.getForceLoadHandler();
        final int x0;

        private Setup(GameTestHelper helper, ServerPlayer player, GameProfile ownerProfile, IServerPartyAPI party, int x0) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.player = player;
            this.playerId = player.getUUID();
            this.ownerId = ownerProfile.getId();
            this.party = party;
            this.x0 = x0;
        }

        static Setup create(GameTestHelper helper, String name, int x0) {
            MinecraftServer server = helper.getLevel().getServer();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            try {
                GameProfile ownerProfile = profile(name + "_Owner");
                IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of(player.getGameProfile()));
                player.setPos(x0 * 16 + 8, 64, 8);
                helper.assertTrue(player.chunkPosition().equals(new ChunkPos(x0, 0)) && player.level().dimension().location().equals(OVERWORLD),
                        "expected the mock player in chunk (" + x0 + ", 0) of the overworld, got " + player.chunkPosition());
                return new Setup(helper, player, ownerProfile, party, x0);
            } catch (RuntimeException | Error e) {
                removePlayerQuiet(server, player);
                throw e;
            }
        }

        int teamSub(UUID memberId) {
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            return teamSubIndexOf(server, subId, memberId);
        }

        void claim(UUID id, int subIndex, int x, int z, boolean forceloaded) {
            IPlayerChunkClaimAPI claim = claimsAPI(server).claim(OVERWORLD, id, subIndex, x, z, forceloaded);
            helper.assertTrue(claim != null, "test setup: expected the claim at (" + x + ", " + z + ") to be made");
        }

        void assertClaim(int x, int z, UUID expectedOwner, int expectedSub, boolean expectedForceloaded) {
            IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x, z);
            helper.assertTrue(claim != null && claim.getPlayerId().equals(expectedOwner) && claim.getSubConfigIndex() == expectedSub
                            && claim.isForceloadable() == expectedForceloaded,
                    "expected the claim at (" + x + ", " + z + ") to be of " + expectedOwner + " with sub-config " + expectedSub
                            + (expectedForceloaded ? ", forceloaded" : ", not forceloaded") + ", got " + (claim == null ? null
                            : claim.getPlayerId() + " with sub-config " + claim.getSubConfigIndex() + ", forceloaded " + claim.isForceloadable()));
        }

        /**
         * The team's totals and both members' private claims and private forceloads (their own claims that are not
         * team claims), as the budget info reports them.
         */
        void assertNumbers(String stage, int teamClaims, int teamForceloads, int playerClaims, int ownerClaims,
                int playerForceloads, int ownerForceloads) {
            BudgetInfo playerBudget = TeamClaimsCommon.getClaimManager().getBudgetInfo(playerId);
            BudgetInfo ownerBudget = TeamClaimsCommon.getClaimManager().getBudgetInfo(ownerId);
            helper.assertTrue(playerBudget.teamClaims() == teamClaims && playerBudget.teamForceloads() == teamForceloads
                            && ownerBudget.teamClaims() == teamClaims && ownerBudget.teamForceloads() == teamForceloads
                            && playerBudget.privateClaims() == playerClaims && ownerBudget.privateClaims() == ownerClaims
                            && playerBudget.privateForceloads() == playerForceloads
                            && ownerBudget.privateForceloads() == ownerForceloads,
                    stage + ": expected team " + teamClaims + "/" + teamForceloads + ", player " + playerClaims + "/" + playerForceloads
                            + ", owner " + ownerClaims + "/" + ownerForceloads + " (claims/forceloads, the members' private ones),"
                            + " got player " + playerBudget + ", owner " + ownerBudget);
        }

        /** Moves the full private limit of the mock player from {@code current} to {@code target} with the matching bonus option. */
        void setPrivateLimit(IPlayerConfigOptionSpecAPI<Integer> bonusOption, int current, int target) {
            IPlayerConfigAPI config = configManager(server).getLoadedConfig(playerId);
            IPlayerConfigAPI.SetResult result = config.tryToSet(bonusOption, config.getEffective(bonusOption) + target - current);
            helper.assertTrue(result == IPlayerConfigAPI.SetResult.SUCCESS,
                    "test setup: expected setting " + bonusOption.getId() + " to succeed, got " + result);
        }

        boolean opacTicketEnabled(int x, int z) {
            return ServerData.from(server).getForceLoadManager().isTicketEnabled(OVERWORLD, playerId, x, z);
        }

        void cleanup() {
            for (IPlayerConfigOptionSpecAPI<Integer> bonusOption : List.of(PlayerConfigOptions.BONUS_CHUNK_CLAIMS,
                    PlayerConfigOptions.BONUS_CHUNK_FORCELOADS)) {
                try {
                    configManager(server).getLoadedConfig(playerId).tryToReset(bonusOption);
                } catch (Exception ignored) {
                }
            }
            for (int x = x0 - 2; x <= x0 + 2; x++)
                for (int z = -2; z <= 2; z++)
                    unclaimQuiet(server, x, z);
            TeamClaimsCommon.getClaimManager().setBudgetSettingsOverride(party.getId(), null);
            try {
                TeamClaimsCommon.getClaimManager().deactivateTeamForceLoads(party.getId());
            } catch (Exception ignored) {
            }
            removePlayerQuiet(server, player);
            disbandPartyQuiet(server, party.getId());
        }
    }

    private static void setRoleAsOwner(GameTestHelper helper, Setup s, TeamAction action, TeamRole role) {
        CapturingCommandSource capture = new CapturingCommandSource();
        int result = TeamRoles.setRole(commandSource(helper, capture), null, s.ownerId, action, role);
        helper.assertTrue(result == 1, "expected the owner to set " + action + " to " + role + ", got " + capture.all());
    }

    private static void resetUsedSubClaimQuiet(IPlayerConfigAPI playerConfig) {
        try {
            playerConfig.tryToReset(PlayerConfigOptions.USED_SUBCLAIM);
        } catch (Exception ignored) {
        }
    }

    /** The ticket level of a chunk, or Integer.MAX_VALUE when it has no chunk holder (as in TeamClaimsHardeningTestCases). */
    private static int ticketLevelOf(GameTestHelper helper, int x, int z) {
        String debug = helper.getLevel().getChunkSource().getChunkDebugData(new ChunkPos(x, z));
        if (debug == null) return Integer.MAX_VALUE;
        int end = 0;
        while (end < debug.length() && Character.isDigit(debug.charAt(end))) end++;
        return end == 0 ? Integer.MAX_VALUE : Integer.parseInt(debug.substring(0, end));
    }
}
