package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommands;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.TeamForceLoadHandler;
import xaero.pac.teamclaims.TeamNames;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;

import java.util.List;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.CapturingCommandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.commandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.localized;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;

/**
 * Dev/test-only, loader-neutral test bodies for the Team Claims server config
 * ({@code openpartiesandclaims-teamclaims-server.toml}) and the forceload grace period. Same conventions as
 * {@link TeamClaimsLogicTestCases} (offline players, a chunk offset of 1000 per test, cleanup on every path); every
 * test is registered with the loader's empty structure template and the default timeout.
 * <p>
 * The gametest server has no players, so the grace period tests drive the very methods a login and a logout use
 * ({@link TeamClaimManager#setTeamOnline} and {@link TeamClaimManager#onMemberLoggedOut}), and shorten the grace period
 * to a few ticks with {@link TeamClaimManager#setForceloadGraceTicksOverride}. Values of the config are changed with
 * {@code ConfigValue.set}, which only touches the loaded config in memory, and restored right after.
 */
public final class TeamClaimsConfigTestCases {

    /** Grace period used by the tests, in ticks. The release is looked for once a second, so it can be 20 ticks late. */
    private static final int TEST_GRACE_TICKS = 30;

    private TeamClaimsConfigTestCases() {}

    // ==================== Tests ====================

    /**
     * The config exists with its defaults, is loaded from its file, and Team
     * Claims is active in the test server (so the default {@code enabled = true} did activate it).
     */
    public static void serverConfigDefaultsAndTeamClaimsActive(GameTestHelper helper) {
        TeamClaimsServerConfig config = TeamClaimsServerConfig.CONFIG;
        helper.assertTrue(config.enabled.getDefault() && config.enabled.get(),
                "expected 'enabled' to default to true and be true, got " + config.enabled.get());
        helper.assertTrue(config.maxTeamNameLength.getDefault() == 24,
                "expected the default of 'maxTeamNameLength' to be 24, got " + config.maxTeamNameLength.getDefault());
        helper.assertTrue(config.forceloadGraceMinutes.getDefault() == 0,
                "expected the default of 'forceloadGraceMinutes' to be 0, got " + config.forceloadGraceMinutes.getDefault());
        helper.assertTrue(TeamClaimsCommon.isActive() && TeamClaimsIntegration.isActive(),
                "expected Team Claims to be active (managers created, bridge handler installed) with 'enabled' = true");
        helper.assertTrue(TeamClaimsServerConfig.SPEC.isLoaded(), "expected the config file to be loaded (and so generated)");
        helper.succeed();
    }

    /**
     * 'maxTeamNameLength' is what limits a team name, and the message names the configured limit. The original
     * value is restored afterwards.
     */
    public static void maxTeamNameLengthIsConfigurable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T16_Owner");
        UUID ownerId = ownerProfile.getId();
        TeamClaimsServerConfig.CONFIG.maxTeamNameLength.set(5);
        try {
            helper.assertTrue(TeamNames.getMaxLength() == 5, "expected the configured limit of 5, got " + TeamNames.getMaxLength());
            TeamNames.Result ok = TeamNames.validate("Fives", null);
            helper.assertTrue(ok.isValid(), "expected a name of exactly the limit to be accepted, got " + ok.errorKey());
            TeamNames.Result tooLong = TeamNames.validate("Sixsix", null);
            helper.assertTrue(!tooLong.isValid() && "gui.xaero_pac_team_claims_create_name_too_long".equals(tooLong.errorKey())
                            && tooLong.errorArgs().length == 1 && Integer.valueOf(5).equals(tooLong.errorArgs()[0]),
                    "expected a name over the limit to be rejected as too long with the limit as the argument, got "
                            + tooLong.errorKey());

            String expected = localized(server, "gui.xaero_pac_team_claims_create_name_too_long", 5);
            helper.assertTrue(expected.contains("max 5 characters"),
                    "expected the message to name the configured limit, got '" + expected + "'");
            CapturingCommandSource capture = new CapturingCommandSource();
            int result = TeamClaimsCommands.createPartyWithTeamName(commandSource(helper, capture), null, ownerProfile,
                    "Sixsix", true);
            helper.assertTrue(result == 0 && capture.received(expected),
                    "expected /teamclaims create to fail with '" + expected + "', got result " + result + " and messages "
                            + capture.all());
            helper.assertTrue(partyManager(server).getPartyByOwner(ownerId) == null,
                    "expected no party to be created for the rejected name");
        } finally {
            TeamClaimsServerConfig.CONFIG.maxTeamNameLength.set(TeamClaimsServerConfig.DEFAULT_MAX_TEAM_NAME_LENGTH);
        }
        helper.assertTrue(TeamNames.validate("a".repeat(24), null).isValid(),
                "expected the default limit of 24 to be back after the test");
        helper.succeed();
    }

    /**
     * With a grace period, the forceloads of a team whose last member went offline stay active until the grace ticks
     * passed, then they are released.
     */
    public static void forceloadGraceKeepsTeamLoadedUntilItPasses(GameTestHelper helper) {
        GraceSetup setup = GraceSetup.create(helper, "T17", 17000, TEST_GRACE_TICKS);
        helper.runAfterDelay(1, () -> setup.guard(() -> {
            setup.cm.setTeamOnline(setup.partyId, true);//a member logs in
            setup.assertActive("after the login");
            setup.cm.onMemberLoggedOut(setup.ownerId);//and out again, nobody else is online
            helper.assertTrue(setup.cm.hasPendingDeactivation(setup.partyId),
                    "expected the deactivation to be scheduled by the logout of the last member");
            setup.assertActive("right after the logout");
            helper.runAfterDelay(TEST_GRACE_TICKS / 2, () -> setup.guard(() -> {
                setup.assertActive("halfway through the grace period");
                helper.runAfterDelay(TEST_GRACE_TICKS + 25, () -> setup.guard(() -> {
                    helper.assertTrue(!setup.handler.isTeamActive(setup.partyId) && !setup.handler.hasTicket(OVERWORLD, setup.x0, 0),
                            "expected the team forceloads to be released after the grace period");
                    helper.assertTrue(!setup.cm.hasPendingDeactivation(setup.partyId),
                            "expected no deactivation to be pending anymore");
                    helper.succeed();
                }, true));
            }, false));
        }, false));
    }

    /** A member logging in again within the grace period cancels the deactivation. */
    public static void forceloadGraceCancelledByReturningMember(GameTestHelper helper) {
        GraceSetup setup = GraceSetup.create(helper, "T18", 18000, TEST_GRACE_TICKS);
        helper.runAfterDelay(1, () -> setup.guard(() -> {
            setup.cm.setTeamOnline(setup.partyId, true);
            setup.assertActive("after the login");
            setup.cm.onMemberLoggedOut(setup.ownerId);
            helper.assertTrue(setup.cm.hasPendingDeactivation(setup.partyId), "expected the deactivation to be scheduled");
            helper.runAfterDelay(5, () -> setup.guard(() -> {
                setup.cm.setTeamOnline(setup.partyId, true);//a member is back
                helper.assertTrue(!setup.cm.hasPendingDeactivation(setup.partyId),
                        "expected the returning member to cancel the scheduled deactivation");
                setup.assertActive("right after the member returned");
                helper.runAfterDelay(TEST_GRACE_TICKS + 25, () -> setup.guard(() -> {
                    setup.assertActive("after the original grace period would have passed");
                    helper.succeed();
                }, true));
            }, false));
        }, false));
    }

    /** Without a grace period (the default) the forceloads are released by the logout itself, as before. */
    public static void noGraceReleasesForceloadsImmediately(GameTestHelper helper) {
        GraceSetup setup = GraceSetup.create(helper, "T19", 19000, 0);
        helper.runAfterDelay(1, () -> setup.guard(() -> {
            setup.cm.setTeamOnline(setup.partyId, true);
            setup.assertActive("after the login");
            setup.cm.onMemberLoggedOut(setup.ownerId);
            helper.assertTrue(!setup.handler.isTeamActive(setup.partyId) && !setup.handler.hasTicket(OVERWORLD, setup.x0, 0),
                    "expected the logout of the last member to release the forceloads right away");
            helper.assertTrue(!setup.cm.hasPendingDeactivation(setup.partyId), "expected nothing to be scheduled");
            helper.succeed();
        }, true));
    }

    // ==================== Grace period setup ====================

    /**
     * A party with a team claim at chunk (x0, 0) forceloaded by the member, with the grace period of the manager set
     * to {@code graceTicks}. The team is only activated by the test itself, one tick after the setup tick, as the party
     * events of the setup tick re-check the activation (see
     * {@link TeamClaimsHardeningTestCases#teamForceloadTicketsAreBalanced}). Always {@link #cleanup}s on a failure.
     */
    private static final class GraceSetup {
        final GameTestHelper helper;
        final TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        final TeamForceLoadHandler handler = TeamClaimsCommon.getForceLoadHandler();
        final MinecraftServer server;
        final UUID ownerId;
        final UUID partyId;
        final int x0;

        private GraceSetup(GameTestHelper helper, UUID ownerId, UUID partyId, int x0) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.ownerId = ownerId;
            this.partyId = partyId;
            this.x0 = x0;
        }

        static GraceSetup create(GameTestHelper helper, String name, int x0, int graceTicks) {
            MinecraftServer server = helper.getLevel().getServer();
            GameProfile ownerProfile = profile(name + "_Owner");
            GameProfile memberProfile = profile(name + "_Member");
            IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            GraceSetup setup = new GraceSetup(helper, ownerProfile.getId(), party.getId(), x0);
            setup.cm.setForceloadGraceTicksOverride(setup.partyId, graceTicks);
            try {
                String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(setup.partyId).getSubConfigId();
                ClaimResult<?> claim = doClaim(server, setup.ownerId, teamSubIndexOf(server, subId, setup.ownerId), x0, 0);
                helper.assertTrue(claim.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                        "expected the team claim to succeed, got " + claim.getResultType());
                ClaimResult<?> forceload = doForceload(server, memberProfile.getId(), x0, 0, true);
                helper.assertTrue(forceload.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                        "expected the forceload to succeed, got " + forceload.getResultType());
            } catch (RuntimeException | Error e) {
                setup.cleanup();
                throw e;
            }
            return setup;
        }

        void assertActive(String when) {
            helper.assertTrue(handler.isTeamActive(partyId) && handler.hasTicket(OVERWORLD, x0, 0),
                    "expected the team forceloads to be active " + when);
        }

        /** Runs a step; on a failure (or after the last step, {@code last}) everything is cleaned up. */
        void guard(Runnable step, boolean last) {
            try {
                step.run();
            } catch (RuntimeException | Error e) {
                cleanup();
                throw e;
            }
            if (last) cleanup();
        }

        void cleanup() {
            cm.setForceloadGraceTicksOverride(partyId, -1);
            unclaimQuiet(server, x0, 0);
            cm.deactivateTeamForceLoads(partyId);
            disbandPartyQuiet(server, partyId);
        }
    }
}
