package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.player.config.PlayerConfigConstants;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI.SetResult;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import java.util.List;
import java.util.UUID;

/**
 * Dev/test-only, loader-neutral logic test bodies for the headless gametest server (vanilla
 * {@link GameTestHelper} + OPAC + Team Claims API only).
 * <p>
 * Lives in the non-shipped shared dir Common/src/gametest, which is NOT a source set of Common
 * itself: each loader adds it as an extra source dir of its own test-only gametest source set and
 * registers one thin {@code @GameTest} wrapper method per test here (on Fabric:
 * {@code TeamClaimsLogicTest}). Every test must be registered with the loader's empty structure
 * template; {@link #leavingMemberClaimsTransferToOwner} additionally needs a timeout of
 * {@link #LEAVING_MEMBER_TIMEOUT_TICKS}.
 * <p>
 * Unlike {@link TeamClaimsSmokeTestCases}, which only checks that Team Claims wired itself into the
 * server, these tests exercise the actual party/claims/config game logic using OFFLINE players
 * identified only by random UUIDs -- no fake {@code ServerPlayer} is ever created. OPAC's server
 * API operates on player UUIDs throughout (party manager, claims manager, player config manager),
 * so this works the same way real offline-mode players would be handled.
 * <p>
 * Every test uses its own random UUIDs, unique usernames, its own party and its own chunk
 * coordinates (a base offset of 1000 chunks per test, in the overworld) so that tests never
 * interfere with each other, and so reruns against the persisted {@code run-boottest} world stay
 * green. Every test cleans up its own claims and party in a {@code finally} block, on both the
 * success and failure paths.
 */
public final class TeamClaimsLogicTestCases {

    /**
     * Timeout {@link #leavingMemberClaimsTransferToOwner} must be registered with. The leave is event-driven, so
     * the test only waits a few ticks; kept as a constant for the loader wrappers.
     */
    public static final int LEAVING_MEMBER_TIMEOUT_TICKS = 100;

    static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();

    private TeamClaimsLogicTestCases() {}

    // ==================== Shared helpers ====================

    static IPartyManagerAPI partyManager(MinecraftServer server) {
        return OpenPACServerAPI.get(server).getPartyManager();
    }

    static IServerClaimsManagerAPI claimsAPI(MinecraftServer server) {
        return OpenPACServerAPI.get(server).getServerClaimsManager();
    }

    static IPlayerConfigManagerAPI configManager(MinecraftServer server) {
        return OpenPACServerAPI.get(server).getPlayerConfigManager();
    }

    /**
     * Creates a party for {@code ownerProfile}, adds every profile in {@code memberProfiles} as a
     * regular {@link PartyMemberRank#MEMBER}, then creates the {@link TeamConfig} right away the same
     * way the real create commands do it ({@code TeamClaimsCommands#createPartyWithTeamName}), so
     * every member has their {@code team_*} sub-config immediately. The party events queued by the
     * party hooks are then processed at the end of the tick and find everything already set up.
     */
    static IServerPartyAPI createPartyWithTeam(MinecraftServer server, GameProfile ownerProfile,
            List<GameProfile> memberProfiles) {
        IServerPartyAPI party = partyManager(server).createPartyForOwner(ownerProfile);
        if (party == null) {
            throw new IllegalStateException("expected createPartyForOwner to return a new party for '"
                    + ownerProfile.getName() + "', got null");
        }
        for (GameProfile member : memberProfiles) {
            IPartyMemberAPI added = party.addMember(member.getId(), PartyMemberRank.MEMBER, member.getName());
            if (added == null) {
                throw new IllegalStateException("expected party.addMember to succeed for '"
                        + member.getName() + "', got null");
            }
        }
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        tcm.createTeamConfig(party);
        cm.ensureAllMembersHaveSubConfig(party);
        return party;
    }

    /** Best-effort teardown of a party and its Team Claims config; never throws. */
    static void disbandPartyQuiet(MinecraftServer server, UUID partyId) {
        try {
            TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
            if (tcm != null)
                tcm.removeTeamConfig(partyId);
        } catch (Exception ignored) {
        }
        try {
            partyManager(server).removePartyById(partyId);
        } catch (Exception ignored) {
        }
    }

    /** Best-effort direct chunk unclaim (bypasses all checks), for cleanup; never throws. */
    static void unclaimQuiet(MinecraftServer server, int... xzPairs) {
        IServerClaimsManagerAPI api = claimsAPI(server);
        for (int i = 0; i + 1 < xzPairs.length; i += 2) {
            try {
                api.unclaim(OVERWORLD, xzPairs[i], xzPairs[i + 1]);
            } catch (Exception ignored) {
            }
        }
    }

    static int teamSubIndexOf(MinecraftServer server, String subConfigId, UUID playerId) {
        IPlayerConfigAPI sub = configManager(server).getLoadedConfig(playerId).getSubConfig(subConfigId);
        return sub == null ? -1 : sub.getSubIndex();
    }

    static ClaimResult<IPlayerChunkClaimAPI> doClaim(MinecraftServer server, UUID playerId, int subIndex,
            int x, int z) {
        return claimsAPI(server).tryToClaim(OVERWORLD, playerId, subIndex, OVERWORLD, x, z, x, z, false);
    }

    static ClaimResult<IPlayerChunkClaimAPI> doUnclaim(MinecraftServer server, UUID playerId, int x, int z) {
        return claimsAPI(server).tryToUnclaim(OVERWORLD, playerId, OVERWORLD, x, z, x, z, false);
    }

    static ClaimResult<IPlayerChunkClaimAPI> doForceload(MinecraftServer server, UUID playerId, int x, int z,
            boolean enable) {
        return claimsAPI(server).tryToForceload(OVERWORLD, playerId, OVERWORLD, x, z, x, z, enable, false);
    }

    static GameProfile profile(String name) {
        return new GameProfile(UUID.randomUUID(), name);
    }

    // ==================== Tests ====================

    /**
     * 1) After creating a 2-member party and triggering team config creation the same way the mod
     * does, both members have the {@code team_*} sub-config, {@code FULL_ACCESS} on it is the
     * party group, and color/name are equal between both.
     */
    public static void teamSubConfigCreated(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T1_Owner");
        GameProfile memberProfile = profile("T1_Member");
        IServerPartyAPI party = null;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            TeamConfig tc = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
            helper.assertTrue(tc != null, "expected a TeamConfig to exist for the created party, got null");
            String subId = tc.getSubConfigId();
            helper.assertTrue(subId != null && subId.startsWith("team_"),
                    "expected the team sub-config id to start with 'team_', got '" + subId + "'");

            IPlayerConfigAPI ownerSub = configManager(server).getLoadedConfig(ownerProfile.getId()).getSubConfig(subId);
            IPlayerConfigAPI memberSub = configManager(server).getLoadedConfig(memberProfile.getId()).getSubConfig(subId);
            helper.assertTrue(ownerSub != null, "expected the owner to have sub-config '" + subId + "', got none");
            helper.assertTrue(memberSub != null, "expected the member to have sub-config '" + subId + "', got none");

            String ownerAccess = ownerSub.getEffective(PlayerConfigOptions.FULL_ACCESS);
            helper.assertTrue(PlayerConfigConstants.PARTY_EXCEPTION_ID.equals(ownerAccess),
                    "expected owner's team sub FULL_ACCESS to be '" + PlayerConfigConstants.PARTY_EXCEPTION_ID
                            + "', got '" + ownerAccess + "'");
            String memberAccess = memberSub.getEffective(PlayerConfigOptions.FULL_ACCESS);
            helper.assertTrue(PlayerConfigConstants.PARTY_EXCEPTION_ID.equals(memberAccess),
                    "expected member's team sub FULL_ACCESS to be '" + PlayerConfigConstants.PARTY_EXCEPTION_ID
                            + "', got '" + memberAccess + "'");

            int ownerColor = ownerSub.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
            int memberColor = memberSub.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
            helper.assertTrue(ownerColor == memberColor, "expected member's team sub CLAIMS_COLOR (" + memberColor
                    + ") to equal owner's (" + ownerColor + ")");

            String ownerName = ownerSub.getEffective(PlayerConfigOptions.CLAIMS_NAME);
            String memberName = memberSub.getEffective(PlayerConfigOptions.CLAIMS_NAME);
            helper.assertTrue(java.util.Objects.equals(ownerName, memberName),
                    "expected member's team sub CLAIMS_NAME ('" + memberName + "') to equal owner's ('" + ownerName
                            + "')");

            helper.succeed();
        } finally {
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * 2) The owner makes 2 team claims + 1 personal claim; the member's displayed claim count only
     * reflects the team claims as overhead. Unclaiming one team claim updates both counts.
     */
    public static void teamClaimCountsAsOverhead(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T2_Owner");
        GameProfile memberProfile = profile("T2_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 2000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            int ownerTeamSub = teamSubIndexOf(server, subId, ownerId);

            ClaimResult<IPlayerChunkClaimAPI> c1 = doClaim(server, ownerId, ownerTeamSub, x0, 0);
            helper.assertTrue(c1.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's 1st team claim to succeed, got " + c1.getResultType());
            ClaimResult<IPlayerChunkClaimAPI> c2 = doClaim(server, ownerId, ownerTeamSub, x0 + 1, 0);
            helper.assertTrue(c2.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's 2nd team claim to succeed, got " + c2.getResultType());
            ClaimResult<IPlayerChunkClaimAPI> c3 = doClaim(server, ownerId, -1, x0 + 2, 0);
            helper.assertTrue(c3.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's personal claim to succeed, got " + c3.getResultType());

            int ownerCount = claimsAPI(server).getPlayerInfo(ownerId).getClaimCount();
            helper.assertTrue(ownerCount == 3,
                    "expected owner claim count (2 team + 1 personal) to be 3, got " + ownerCount);
            int memberCount = claimsAPI(server).getPlayerInfo(memberId).getClaimCount();
            helper.assertTrue(memberCount == 2,
                    "expected member claim count (overhead from owner's 2 team claims) to be 2, got " + memberCount);

            ClaimResult<IPlayerChunkClaimAPI> u1 = doUnclaim(server, ownerId, x0, 0);
            helper.assertTrue(u1.getResultType() == ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                    "expected unclaiming one team claim to succeed, got " + u1.getResultType());

            int ownerCountAfter = claimsAPI(server).getPlayerInfo(ownerId).getClaimCount();
            helper.assertTrue(ownerCountAfter == 2,
                    "expected owner claim count after unclaiming 1 team claim to be 2, got " + ownerCountAfter);
            int memberCountAfter = claimsAPI(server).getPlayerInfo(memberId).getClaimCount();
            helper.assertTrue(memberCountAfter == 1,
                    "expected member claim count after owner unclaimed 1 team claim to be 1, got " + memberCountAfter);

            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0);
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * 3) A team claim is rejected with CLAIM_LIMIT_REACHED if it would push a teammate over their
     * own full claim limit, but the owner's personal claim still succeeds.
     */
    public static void teamClaimRejectedWhenTeammateAtLimit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T3_Owner");
        GameProfile memberProfile = profile("T3_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        IPlayerConfigAPI memberMainCfg = null;
        int x0 = 3000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            int ownerTeamSub = teamSubIndexOf(server, subId, ownerId);

            memberMainCfg = configManager(server).getLoadedConfig(memberId);
            int memberLimitBefore = claimsAPI(server).getPlayerFullClaimLimit(memberId);
            SetResult bonusSet = memberMainCfg.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, -memberLimitBefore);
            helper.assertTrue(bonusSet == SetResult.SUCCESS,
                    "expected setting the member's BONUS_CHUNK_CLAIMS to bring them to their limit to succeed, got "
                            + bonusSet);
            int memberLimitAfter = claimsAPI(server).getPlayerFullClaimLimit(memberId);
            helper.assertTrue(memberLimitAfter == 0,
                    "expected member's full claim limit to be reduced to 0, got " + memberLimitAfter);

            ClaimResult<IPlayerChunkClaimAPI> teamResult = doClaim(server, ownerId, ownerTeamSub, x0, 0);
            helper.assertTrue(teamResult.getResultType() == ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "expected owner's team claim to be rejected with CLAIM_LIMIT_REACHED while the teammate is at "
                            + "their limit, got " + teamResult.getResultType());
            IPlayerChunkClaimAPI stateAtX0 = claimsAPI(server).get(OVERWORLD, x0, 0);
            helper.assertTrue(stateAtX0 == null,
                    "expected the chunk to remain unclaimed after the rejected team claim, got owner="
                            + (stateAtX0 == null ? null : stateAtX0.getPlayerId()));

            ClaimResult<IPlayerChunkClaimAPI> personalResult = doClaim(server, ownerId, -1, x0 + 1, 0);
            helper.assertTrue(personalResult.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's personal claim to still succeed while the teammate is at their team-claim "
                            + "limit, got " + personalResult.getResultType());

            helper.succeed();
        } finally {
            if (memberMainCfg != null) {
                try {
                    memberMainCfg.tryToReset(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
                } catch (Exception ignored) {
                }
            }
            unclaimQuiet(server, x0, 0, x0 + 1, 0);
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * 4) Any party member may unclaim a teammate's team claim (non-forced). Nobody (member or
     * outsider) may unclaim a claim they don't actually own this way: a personal claim can't be
     * unclaimed by a teammate, and a team claim can't be unclaimed by a non-member.
     */
    public static void teammateCanUnclaimTeamClaim(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T4_Owner");
        GameProfile memberProfile = profile("T4_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        UUID outsiderId = UUID.randomUUID();
        IServerPartyAPI party = null;
        int x0 = 4000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            int ownerTeamSub = teamSubIndexOf(server, subId, ownerId);

            ClaimResult<IPlayerChunkClaimAPI> teamClaim = doClaim(server, ownerId, ownerTeamSub, x0, 0);
            helper.assertTrue(teamClaim.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's team claim to succeed, got " + teamClaim.getResultType());
            ClaimResult<IPlayerChunkClaimAPI> personalClaim = doClaim(server, ownerId, -1, x0 + 1, 0);
            helper.assertTrue(personalClaim.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's personal claim to succeed, got " + personalClaim.getResultType());

            ClaimResult<IPlayerChunkClaimAPI> res1 = doUnclaim(server, memberId, x0, 0);
            helper.assertTrue(res1.getResultType() == ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                    "expected the teammate to be able to unclaim the owner's team claim, got " + res1.getResultType());
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0, 0) == null,
                    "expected the team claim chunk to be unclaimed after the teammate unclaimed it");

            ClaimResult<IPlayerChunkClaimAPI> res2 = doUnclaim(server, memberId, x0 + 1, 0);
            helper.assertTrue(res2.getResultType() == ClaimResult.Type.NOT_CLAIMED_BY_USER,
                    "expected the teammate unclaiming the owner's PERSONAL claim to fail with NOT_CLAIMED_BY_USER, got "
                            + res2.getResultType());
            IPlayerChunkClaimAPI personalStillThere = claimsAPI(server).get(OVERWORLD, x0 + 1, 0);
            helper.assertTrue(personalStillThere != null && ownerId.equals(personalStillThere.getPlayerId()),
                    "expected the owner's personal claim to remain owned by the owner");

            ClaimResult<IPlayerChunkClaimAPI> teamClaim2 = doClaim(server, ownerId, ownerTeamSub, x0 + 2, 0);
            helper.assertTrue(teamClaim2.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's 2nd team claim to succeed, got " + teamClaim2.getResultType());
            ClaimResult<IPlayerChunkClaimAPI> res3 = doUnclaim(server, outsiderId, x0 + 2, 0);
            helper.assertTrue(res3.getResultType() == ClaimResult.Type.NOT_CLAIMED_BY_USER,
                    "expected a non-member unclaiming the team claim to fail with NOT_CLAIMED_BY_USER, got "
                            + res3.getResultType());
            IPlayerChunkClaimAPI teamClaim2StillThere = claimsAPI(server).get(OVERWORLD, x0 + 2, 0);
            helper.assertTrue(teamClaim2StillThere != null,
                    "expected the team claim to remain after a non-member's rejected unclaim attempt");

            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0);
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * 5) A party member can toggle the forceload state of a teammate's team claim; the claim stays
     * owned by the original owner with the same sub-config index. A non-member cannot.
     */
    public static void teammateCanToggleForceload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T5_Owner");
        GameProfile memberProfile = profile("T5_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        UUID outsiderId = UUID.randomUUID();
        IServerPartyAPI party = null;
        int x0 = 5000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            int ownerTeamSub = teamSubIndexOf(server, subId, ownerId);

            ClaimResult<IPlayerChunkClaimAPI> teamClaim = doClaim(server, ownerId, ownerTeamSub, x0, 0);
            helper.assertTrue(teamClaim.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected owner's team claim to succeed, got " + teamClaim.getResultType());

            ClaimResult<IPlayerChunkClaimAPI> res1 = doForceload(server, memberId, x0, 0, true);
            helper.assertTrue(res1.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "expected the teammate to be able to enable forceload on the owner's team claim, got "
                            + res1.getResultType());
            IPlayerChunkClaimAPI stateOn = claimsAPI(server).get(OVERWORLD, x0, 0);
            helper.assertTrue(stateOn != null && stateOn.isForceloadable(),
                    "expected the chunk to be forceloadable after the teammate enabled it");
            helper.assertTrue(stateOn != null && ownerId.equals(stateOn.getPlayerId()),
                    "expected the claim to remain owned by the owner after the teammate toggled forceload, got "
                            + (stateOn == null ? null : stateOn.getPlayerId()));
            helper.assertTrue(stateOn != null && stateOn.getSubConfigIndex() == ownerTeamSub,
                    "expected the claim's sub-config index to remain the owner's team sub index (" + ownerTeamSub
                            + "), got " + (stateOn == null ? -1 : stateOn.getSubConfigIndex()));

            int ownerForceloadCount = claimsAPI(server).getPlayerInfo(ownerId).getForceloadCount();
            helper.assertTrue(ownerForceloadCount == 1,
                    "expected owner's forceload count to be 1, got " + ownerForceloadCount);
            int memberForceloadCount = claimsAPI(server).getPlayerInfo(memberId).getForceloadCount();
            helper.assertTrue(memberForceloadCount == 1,
                    "expected member's forceload count (overhead) to be 1, got " + memberForceloadCount);

            ClaimResult<IPlayerChunkClaimAPI> res2 = doForceload(server, memberId, x0, 0, false);
            helper.assertTrue(res2.getResultType() == ClaimResult.Type.SUCCESSFUL_UNFORCELOAD,
                    "expected the teammate to be able to disable forceload on the owner's team claim, got "
                            + res2.getResultType());
            IPlayerChunkClaimAPI stateOff = claimsAPI(server).get(OVERWORLD, x0, 0);
            helper.assertTrue(stateOff != null && !stateOff.isForceloadable(),
                    "expected the chunk to no longer be forceloadable after the teammate disabled it");

            ClaimResult<IPlayerChunkClaimAPI> res3 = doForceload(server, outsiderId, x0, 0, true);
            helper.assertTrue(res3.getResultType() == ClaimResult.Type.NOT_CLAIMED_BY_USER_FORCELOAD,
                    "expected a non-member's forceload attempt to fail with NOT_CLAIMED_BY_USER_FORCELOAD, got "
                            + res3.getResultType());
            IPlayerChunkClaimAPI stateAfterOutsider = claimsAPI(server).get(OVERWORLD, x0, 0);
            helper.assertTrue(stateAfterOutsider != null && !stateAfterOutsider.isForceloadable(),
                    "expected the chunk to remain non-forceloaded after a non-member's rejected forceload attempt");

            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0);
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * 6) Only the party owner/ADMIN rank may {@code tryToSet} options on a {@code team_*}
     * sub-config; a regular MEMBER gets ILLEGAL_OPTION and the value is unchanged. A successful
     * edit propagates to every member's team sub-config. FULL_ACCESS may only be set to a
     * party/allies/everyone group.
     */
    public static void nonAdminCannotEditTeamSubConfig(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T6_Owner");
        GameProfile memberProfile = profile("T6_Member");
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            IPlayerConfigAPI ownerSub = configManager(server).getLoadedConfig(ownerProfile.getId()).getSubConfig(subId);
            IPlayerConfigAPI memberSub = configManager(server).getLoadedConfig(memberId).getSubConfig(subId);

            int colorBefore = memberSub.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
            SetResult r1 = memberSub.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, 0x123456);
            helper.assertTrue(r1 == SetResult.ILLEGAL_OPTION,
                    "expected a non-admin member's tryToSet(CLAIMS_COLOR) on their own team sub to be "
                            + "ILLEGAL_OPTION, got " + r1);
            int colorAfterRejected = memberSub.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
            helper.assertTrue(colorAfterRejected == colorBefore,
                    "expected member's team sub CLAIMS_COLOR to stay " + colorBefore
                            + " after the rejected edit, got " + colorAfterRejected);

            SetResult r2 = ownerSub.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, 0x123456);
            helper.assertTrue(r2 == SetResult.SUCCESS,
                    "expected the owner's tryToSet(CLAIMS_COLOR) on the team sub to succeed, got " + r2);
            int memberColorAfterOwnerEdit = memberSub.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
            helper.assertTrue(memberColorAfterOwnerEdit == 0x123456,
                    "expected the owner's edit to propagate to the member's team sub CLAIMS_COLOR (expected "
                            + Integer.toHexString(0x123456) + "), got " + Integer.toHexString(memberColorAfterOwnerEdit));

            IPartyMemberAPI memberInfo = party.getMemberInfo(memberId);
            helper.assertTrue(memberInfo != null, "expected to find the member's party info, got null");
            boolean rankSet = party.setRank(memberInfo, PartyMemberRank.ADMIN);
            helper.assertTrue(rankSet, "expected party.setRank to succeed promoting the member to ADMIN");

            SetResult r3 = memberSub.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, 0x654321);
            helper.assertTrue(r3 == SetResult.SUCCESS,
                    "expected an ADMIN member's tryToSet(CLAIMS_COLOR) on the team sub to succeed, got " + r3);
            int ownerColorAfterMemberEdit = ownerSub.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
            helper.assertTrue(ownerColorAfterMemberEdit == 0x654321,
                    "expected the admin member's edit to propagate to the owner's team sub CLAIMS_COLOR (expected "
                            + Integer.toHexString(0x654321) + "), got " + Integer.toHexString(ownerColorAfterMemberEdit));

            SetResult r4 = ownerSub.tryToSet(PlayerConfigOptions.FULL_ACCESS, PlayerConfigConstants.NO_EXCEPTION_ID);
            helper.assertTrue(r4 == SetResult.ILLEGAL_OPTION,
                    "expected setting FULL_ACCESS to a non-party group ('" + PlayerConfigConstants.NO_EXCEPTION_ID
                            + "') on a team sub to be ILLEGAL_OPTION, got " + r4);

            helper.succeed();
        } finally {
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * 7) When a member leaves (or is kicked from) a party that still exists, their team claims are
     * transferred to the party owner (staying team claims, forceload flag kept), and the leaver's
     * team sub-config is removed.
     * <p>
     * The leave is event-driven: {@code ServerParty.removeMember}'s hook queues it and it is
     * processed at the end of the same server tick, so a few ticks of waiting are enough (the old
     * membership poll needed two full poll intervals).
     */
    public static void leavingMemberClaimsTransferToOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T7_Owner");
        GameProfile memberProfile = profile("T7_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        int x0 = 7000;
        IServerPartyAPI[] partyBox = new IServerPartyAPI[1];
        try {
            IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            partyBox[0] = party;
            UUID partyId = party.getId();
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
            int memberTeamSub = teamSubIndexOf(server, subId, memberId);

            ClaimResult<IPlayerChunkClaimAPI> c1 = doClaim(server, memberId, memberTeamSub, x0, 0);
            helper.assertTrue(c1.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected member's 1st team claim to succeed, got " + c1.getResultType());
            ClaimResult<IPlayerChunkClaimAPI> c2 = doClaim(server, memberId, memberTeamSub, x0 + 1, 0);
            helper.assertTrue(c2.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected member's 2nd team claim to succeed, got " + c2.getResultType());
            // The member forceloads their OWN team claim directly (normal same-owner path, no
            // teammate-toggle permission involved), so this is independent of test 5's result.
            ClaimResult<IPlayerChunkClaimAPI> f1 = doForceload(server, memberId, x0, 0, true);
            helper.assertTrue(f1.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "expected member to be able to forceload their own team claim, got " + f1.getResultType());

            // Stage 1 (t+1): the party events of the setup have been processed; remove the member.
            helper.runAfterDelay(1, () -> {
                try {
                    IPartyMemberAPI removed = party.removeMember(memberId);
                    helper.assertTrue(removed != null,
                            "expected party.removeMember to succeed for the member, got null");
                } catch (RuntimeException | Error e) {
                    unclaimQuiet(server, x0, 0, x0 + 1, 0);
                    disbandPartyQuiet(server, partyId);
                    throw e;
                }

                // Stage 2 (t+1+3): the leave was processed at the end of the removal tick
                // (transfer + sub-config removal); a few ticks of margin.
                helper.runAfterDelay(3, () -> {
                    try {
                        IPlayerChunkClaimAPI claim1 = claimsAPI(server).get(OVERWORLD, x0, 0);
                        helper.assertTrue(claim1 != null,
                                "expected the ex-member's former team claim at (" + x0 + ",0) to still be claimed "
                                        + "(transferred to the owner), got unclaimed");
                        helper.assertTrue(claim1.getPlayerId().equals(ownerId),
                                "expected the claim at (" + x0 + ",0) to be owned by the owner after transfer, got "
                                        + claim1.getPlayerId());
                        helper.assertTrue(claim1.isForceloadable(),
                                "expected the forceload flag to be kept on the transferred claim at (" + x0 + ",0)");

                        IPlayerChunkClaimAPI claim2 = claimsAPI(server).get(OVERWORLD, x0 + 1, 0);
                        helper.assertTrue(claim2 != null,
                                "expected the ex-member's other former team claim at (" + (x0 + 1) + ",0) to still "
                                        + "be claimed (transferred to the owner), got unclaimed");
                        helper.assertTrue(claim2.getPlayerId().equals(ownerId),
                                "expected the claim at (" + (x0 + 1) + ",0) to be owned by the owner after "
                                        + "transfer, got " + claim2.getPlayerId());
                        helper.assertTrue(!claim2.isForceloadable(),
                                "expected the non-forceloaded transferred claim at (" + (x0 + 1) + ",0) to stay "
                                        + "non-forceloaded");

                        int ownerTeamSubAfter = teamSubIndexOf(server, subId, ownerId);
                        helper.assertTrue(claim1.getSubConfigIndex() == ownerTeamSubAfter,
                                "expected the transferred claim's sub-config index to be the owner's team sub "
                                        + "index (" + ownerTeamSubAfter + "), got " + claim1.getSubConfigIndex());
                        helper.assertTrue(claim2.getSubConfigIndex() == ownerTeamSubAfter,
                                "expected the other transferred claim's sub-config index to be the owner's team "
                                        + "sub index (" + ownerTeamSubAfter + "), got "
                                        + claim2.getSubConfigIndex());

                        int ownerCount = claimsAPI(server).getPlayerInfo(ownerId).getClaimCount();
                        helper.assertTrue(ownerCount == 2,
                                "expected owner's claim count after the transfer to be 2, got " + ownerCount);
                        int exMemberCount = claimsAPI(server).getPlayerInfo(memberId).getClaimCount();
                        helper.assertTrue(exMemberCount == 0,
                                "expected the ex-member's claim count after the transfer to be 0, got "
                                        + exMemberCount);

                        boolean exMemberStillHasSub =
                                configManager(server).getLoadedConfig(memberId).subConfigExists(subId);
                        helper.assertTrue(!exMemberStillHasSub,
                                "expected the ex-member's team sub-config '" + subId + "' to have been removed "
                                        + "after leaving, but it still exists");

                        helper.succeed();
                    } finally {
                        unclaimQuiet(server, x0, 0, x0 + 1, 0);
                        disbandPartyQuiet(server, partyId);
                    }
                });
            });
        } catch (RuntimeException | Error e) {
            unclaimQuiet(server, x0, 0, x0 + 1, 0);
            if (partyBox[0] != null)
                disbandPartyQuiet(server, partyBox[0].getId());
            throw e;
        }
    }

    /**
     * 8) A party name longer than 16 characters, containing spaces and uppercase letters, still
     * produces a valid {@code team_*} sub-config id (per {@link PlayerConfig#isValidSubIdOrTeam}),
     * which can be selected as the used sub-claim via {@code tryToSet(USED_SUBCLAIM, ...)}.
     */
    public static void longTeamNameSubConfig(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T8_Owner");
        UUID ownerId = ownerProfile.getId();
        String longTeamName = "My Long TEAM Name Test8";
        IServerPartyAPI party = null;
        try {
            IPlayerConfigAPI ownerMainCfg = configManager(server).getLoadedConfig(ownerId);
            SetResult nameSet = ownerMainCfg.tryToSet(PlayerConfigOptions.PARTY_NAME, longTeamName);
            helper.assertTrue(nameSet == SetResult.SUCCESS,
                    "expected setting a long custom PARTY_NAME to succeed, got " + nameSet);

            party = createPartyWithTeam(server, ownerProfile, List.of());
            TeamConfig tc = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
            helper.assertTrue(tc != null, "expected a TeamConfig to exist for the created party, got null");
            String subId = tc.getSubConfigId();

            helper.assertTrue(subId.startsWith("team_"),
                    "expected the sub-config id to start with 'team_', got '" + subId + "'");
            helper.assertTrue(subId.length() > 16,
                    "expected the sub-config id derived from a " + longTeamName.length()
                            + "-char team name to exceed the normal 16-char sub id length limit, got length "
                            + subId.length() + " ('" + subId + "')");
            helper.assertTrue(PlayerConfig.isValidSubIdOrTeam(subId),
                    "expected '" + subId + "' to be considered a valid sub id by PlayerConfig.isValidSubIdOrTeam");

            IPlayerConfigAPI ownerSub = ownerMainCfg.getSubConfig(subId);
            helper.assertTrue(ownerSub != null, "expected the owner to have sub-config '" + subId + "', got none");

            SetResult usedSet = ownerMainCfg.tryToSet(PlayerConfigOptions.USED_SUBCLAIM, subId);
            helper.assertTrue(usedSet == SetResult.SUCCESS,
                    "expected tryToSet(USED_SUBCLAIM, '" + subId + "') to succeed, got " + usedSet);
            String currentUsed = ownerMainCfg.getEffective(PlayerConfigOptions.USED_SUBCLAIM);
            helper.assertTrue(subId.equals(currentUsed),
                    "expected the used sub-claim to be '" + subId + "', got '" + currentUsed + "'");

            helper.succeed();
        } finally {
            try {
                configManager(server).getLoadedConfig(ownerId).tryToReset(PlayerConfigOptions.PARTY_NAME);
            } catch (Exception ignored) {
            }
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }
}
