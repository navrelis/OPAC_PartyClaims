package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.AreaClaimResult;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.player.config.PlayerConfigConstants;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI.SetResult;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommands;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.TeamForceLoadHandler;
import xaero.pac.teamclaims.TeamNames;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;

/**
 * Dev/test-only, loader-neutral test bodies for the event-driven membership handling, the budget
 * bookkeeping and the hardening of Team Claims (team name validation, option persistence, unique
 * sub-config IDs, corrupt config files, forceload ticket balance). Same conventions as
 * {@link TeamClaimsLogicTestCases} (shared helpers, offline players, a chunk offset of 1000 per test
 * number, cleanup on every path); every test is registered with the loader's empty structure
 * template and the default timeout (on Fabric: {@code TeamClaimsLogicTest}).
 */
public final class TeamClaimsHardeningTestCases {

    private TeamClaimsHardeningTestCases() {}

    // ==================== Helpers ====================

    /** Collects every message sent to a command source. */
    static final class CapturingCommandSource implements CommandSource {
        private final List<Component> messages = new ArrayList<>();

        @Override public void sendSystemMessage(Component component) { messages.add(component); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }

        List<Component> messages() {
            return messages;
        }

        boolean received(String text) {
            return messages.stream().anyMatch(m -> m.getString().contains(text));
        }

        String all() {
            return messages.stream().map(Component::getString).toList().toString();
        }
    }

    static CommandSourceStack commandSource(GameTestHelper helper, CapturingCommandSource capture) {
        return new CommandSourceStack(capture, Vec3.ZERO, Vec2.ZERO, helper.getLevel(), 4, "TeamClaimsTest",
                Component.literal("TeamClaimsTest"), helper.getLevel().getServer(), null);
    }

    /** The text of a message key for a source without a player (OPAC's server-side default translation). */
    static String localized(MinecraftServer server, String key, Object... args) {
        return OpenPACServerAPI.get(server).getAdaptiveTextLocalizer().getFor(null, key, args).getString();
    }

    private static void resetPartyNameQuiet(MinecraftServer server, UUID playerId) {
        try {
            configManager(server).getLoadedConfig(playerId).tryToReset(PlayerConfigOptions.PARTY_NAME);
        } catch (Exception ignored) {
        }
    }

    /**
     * The team total (tracked, and as both members' budget info reports it), each member's private claims and how
     * many of the team claims each of them technically owns.
     */
    private static void assertCounts(GameTestHelper helper, MinecraftServer server, UUID partyId, String stage,
            int teamTotalExpected, UUID ownerId, int ownerPrivateExpected, int ownerOwnedExpected,
            UUID memberId, int memberPrivateExpected, int memberOwnedExpected) {
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamClaimManager.TeamData teamData = cm.getTeamData(partyId);
        int teamTotal = teamData == null ? 0 : teamData.getClaimCount();
        TeamClaimManager.BudgetInfo owner = cm.getBudgetInfo(ownerId);
        TeamClaimManager.BudgetInfo member = cm.getBudgetInfo(memberId);
        helper.assertTrue(teamTotal == teamTotalExpected && owner.teamClaims() == teamTotalExpected
                        && member.teamClaims() == teamTotalExpected,
                stage + ": expected " + teamTotalExpected + " team claims (tracked and for both members), got " + teamTotal
                        + ", owner=" + owner.teamClaims() + ", member=" + member.teamClaims());
        helper.assertTrue(owner.privateClaims() == ownerPrivateExpected && owner.ownedTeamClaims() == ownerOwnedExpected,
                stage + ": expected the owner to have " + ownerPrivateExpected + " private claim(s) and to own "
                        + ownerOwnedExpected + " team claim(s), got " + owner);
        helper.assertTrue(member.privateClaims() == memberPrivateExpected && member.ownedTeamClaims() == memberOwnedExpected,
                stage + ": expected the member to have " + memberPrivateExpected + " private claim(s) and to own "
                        + memberOwnedExpected + " team claim(s), got " + member);
        int ownerRaw = claimsAPI(server).getPlayerInfo(ownerId).getClaimCount();
        int memberRaw = claimsAPI(server).getPlayerInfo(memberId).getClaimCount();
        helper.assertTrue(ownerRaw == ownerPrivateExpected + ownerOwnedExpected
                        && memberRaw == memberPrivateExpected + memberOwnedExpected,
                stage + ": expected OPAC's own claim counts to be private + owned team claims, got owner=" + ownerRaw
                        + " member=" + memberRaw);
    }

    /** The ticket level of a chunk, or Integer.MAX_VALUE when the chunk has no chunk holder at all. */
    private static int ticketLevelOf(GameTestHelper helper, int x, int z) {
        String debug = helper.getLevel().getChunkSource().getChunkDebugData(new ChunkPos(x, z));
        if (debug == null) return Integer.MAX_VALUE;
        int end = 0;
        while (end < debug.length() && Character.isDigit(debug.charAt(end))) end++;
        return end == 0 ? Integer.MAX_VALUE : Integer.parseInt(debug.substring(0, end));
    }

    // ==================== Tests ====================

    /**
     * 9) A member who joins and leaves again within a couple of ticks (far less than the old 60-tick
     * membership poll) is processed anyway: the join gives them the team sub-config at the end of the
     * tick, and their team claim made in between is handed over to the owner right after the leave.
     */
    public static void quickJoinAndLeaveTransfersImmediately(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T9_Owner");
        GameProfile memberProfile = profile("T9_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        int x0 = 9000;
        IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of());
        UUID partyId = party.getId();
        String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
        Runnable cleanup = () -> {
            unclaimQuiet(server, x0, 0);
            disbandPartyQuiet(server, partyId);
        };
        try {
            // Joining only through OPAC's party API: the team sub-config comes from the queued join event
            helper.assertTrue(party.addMember(memberId, PartyMemberRank.MEMBER, memberProfile.getName()) != null,
                    "expected party.addMember to succeed");
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(1, () -> {
            try {
                int memberTeamSub = teamSubIndexOf(server, subId, memberId);
                helper.assertTrue(memberTeamSub != -1,
                        "expected the join event to have created the member's team sub-config '" + subId + "' within one tick");
                ClaimResult<IPlayerChunkClaimAPI> c1 = doClaim(server, memberId, memberTeamSub, x0, 0);
                helper.assertTrue(c1.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                        "expected the new member's team claim to succeed, got " + c1.getResultType());
                helper.assertTrue(party.removeMember(memberId) != null, "expected party.removeMember to succeed");
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(2, () -> {
                try {
                    IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x0, 0);
                    helper.assertTrue(claim != null && ownerId.equals(claim.getPlayerId()),
                            "expected the quick leaver's team claim to belong to the owner 2 ticks after the leave, got "
                                    + (claim == null ? "unclaimed" : claim.getPlayerId()));
                    helper.assertTrue(claim.getSubConfigIndex() == teamSubIndexOf(server, subId, ownerId),
                            "expected the transferred claim to use the owner's team sub-config");
                    helper.assertTrue(!configManager(server).getLoadedConfig(memberId).subConfigExists(subId),
                            "expected the quick leaver's team sub-config to be removed");
                    int leaverCount = claimsAPI(server).getPlayerInfo(memberId).getClaimCount();
                    helper.assertTrue(leaverCount == 0,
                            "expected the quick leaver to have no claims left, got " + leaverCount);
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
        });
    }

    /**
     * 10) The two budgets stay right through many claim/unclaim operations by both members, including a
     * 3x3 area claim and an area unclaim (one batch of chunk changes each), and a teammate forceload: the
     * team total is the same for both members, a member's private claims are only their own claims that
     * are not team claims, and the team claims each of them technically owns add up to the team total.
     */
    public static void budgetsStayCorrectAfterManyChanges(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T10_Owner");
        GameProfile memberProfile = profile("T10_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        int x0 = 10000;
        IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
        UUID partyId = party.getId();
        String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
        int ownerTeamSub = teamSubIndexOf(server, subId, ownerId);
        int memberTeamSub = teamSubIndexOf(server, subId, memberId);
        Runnable cleanup = () -> {
            for (int x = x0; x <= x0 + 12; x++)
                for (int z = 0; z <= 5; z++)
                    unclaimQuiet(server, x, z);
            disbandPartyQuiet(server, partyId);
        };
        AtomicReference<AreaClaimResult> areaClaim = new AtomicReference<>();
        try {
            // 3x3 team area claim of the owner around (x0+1, 1)
            claimsAPI(server).tryToClaimArea(OVERWORLD, ownerId, ownerTeamSub, OVERWORLD, x0 + 1, 1,
                    x0, 0, x0 + 2, 2, false, areaClaim::set);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(3, () -> {
            AtomicReference<AreaClaimResult> areaUnclaim = new AtomicReference<>();
            try {
                helper.assertTrue(areaClaim.get() != null, "expected the area claim to have finished within 3 ticks");
                assertCounts(helper, server, partyId, "after the 3x3 area claim", 9, ownerId, 0, 9, memberId, 0, 0);

                for (int i = 0; i < 2; i++) {
                    ClaimResult<IPlayerChunkClaimAPI> r = doClaim(server, memberId, memberTeamSub, x0 + 4 + i, 0);
                    helper.assertTrue(r.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                            "expected the member's team claim " + i + " to succeed, got " + r.getResultType());
                }
                ClaimResult<IPlayerChunkClaimAPI> personal = doClaim(server, ownerId, -1, x0 + 7, 0);
                helper.assertTrue(personal.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                        "expected the owner's personal claim to succeed, got " + personal.getResultType());
                assertCounts(helper, server, partyId, "after single claims", 11, ownerId, 1, 9, memberId, 0, 2);

                // The member unclaims 3 of the owner's area team claims, the owner one of the member's
                for (int z = 0; z < 3; z++) {
                    ClaimResult<IPlayerChunkClaimAPI> r = doUnclaim(server, memberId, x0, z);
                    helper.assertTrue(r.getResultType() == ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                            "expected the member to unclaim the owner's team claim at z=" + z + ", got " + r.getResultType());
                }
                ClaimResult<IPlayerChunkClaimAPI> u = doUnclaim(server, ownerId, x0 + 4, 0);
                helper.assertTrue(u.getResultType() == ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                        "expected the owner to unclaim the member's team claim, got " + u.getResultType());
                assertCounts(helper, server, partyId, "after teammate unclaims", 7, ownerId, 1, 6, memberId, 0, 1);

                // Claim/unclaim churn by alternating members: the totals must come back to the same numbers
                for (int i = 0; i < 6; i++) {
                    int x = x0 + 6 + i;
                    boolean ownerClaims = i % 2 == 0;
                    ClaimResult<IPlayerChunkClaimAPI> c = doClaim(server, ownerClaims ? ownerId : memberId,
                            ownerClaims ? ownerTeamSub : memberTeamSub, x, 5);
                    helper.assertTrue(c.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                            "expected churn claim " + i + " to succeed, got " + c.getResultType());
                    ClaimResult<IPlayerChunkClaimAPI> r = doUnclaim(server, ownerClaims ? memberId : ownerId, x, 5);
                    helper.assertTrue(r.getResultType() == ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                            "expected churn unclaim " + i + " to succeed, got " + r.getResultType());
                }
                assertCounts(helper, server, partyId, "after claim/unclaim churn", 7, ownerId, 1, 6, memberId, 0, 1);

                // The owner forceloads the member's remaining team claim: a team forceload for both, a private one for nobody
                ClaimResult<IPlayerChunkClaimAPI> f = doForceload(server, ownerId, x0 + 5, 0, true);
                helper.assertTrue(f.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                        "expected the owner to forceload the member's team claim, got " + f.getResultType());
                TeamClaimManager.BudgetInfo ownerBudget = TeamClaimsCommon.getClaimManager().getBudgetInfo(ownerId);
                TeamClaimManager.BudgetInfo memberBudget = TeamClaimsCommon.getClaimManager().getBudgetInfo(memberId);
                helper.assertTrue(ownerBudget.teamForceloads() == 1 && memberBudget.teamForceloads() == 1
                                && ownerBudget.privateForceloads() == 0 && memberBudget.privateForceloads() == 0
                                && ownerBudget.ownedTeamForceloads() == 0 && memberBudget.ownedTeamForceloads() == 1,
                        "expected 1 team forceload for both, owned by the member, and no private forceloads, got owner="
                                + ownerBudget + " member=" + memberBudget);

                // Area unclaim by the owner: removes the owner's 6 remaining area team claims in one batch
                claimsAPI(server).tryToUnclaimArea(OVERWORLD, ownerId, OVERWORLD, x0 + 1, 1, x0, 0, x0 + 2, 2,
                        false, areaUnclaim::set);
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(3, () -> {
                try {
                    helper.assertTrue(areaUnclaim.get() != null, "expected the area unclaim to have finished within 3 ticks");
                    assertCounts(helper, server, partyId, "after the area unclaim", 1, ownerId, 1, 0, memberId, 0, 1);
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
        });
    }

    /**
     * 11) The shared create path rejects an invalid team name, reports why to the command source and
     * creates nothing, through both entry points: {@code /teamclaims create} and the bridge method the
     * {@code /<parties> create <teamname>} hook in {@code CreatePartyCommand} calls. A valid name (with
     * a formatting code and extra spaces that get stripped) creates and names the party and its team.
     * <p>
     * Executed without a real player (the gametest server has none), as an operator source on behalf
     * of an offline owner profile, the same way an impersonating operator would.
     */
    public static void teamCreateValidatesName(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T11_Owner");
        UUID ownerId = ownerProfile.getId();
        IServerPartyAPI created = null;
        try {
            String[][] invalid = {
                    {"§a&b", "gui.xaero_pac_team_claims_create_name_empty"},
                    {"This Team Name Is Too Long", "gui.xaero_pac_team_claims_create_name_too_long"},
                    {"Bad<Name>", "gui.xaero_pac_team_claims_create_name_invalid"}
            };
            for (String[] testCase : invalid) {
                String expected = localized(server, testCase[1], TeamNames.getMaxLength());
                CapturingCommandSource teamclaimsCapture = new CapturingCommandSource();
                int teamclaimsResult = TeamClaimsCommands.createPartyWithTeamName(
                        commandSource(helper, teamclaimsCapture), null, ownerProfile, testCase[0], true);
                helper.assertTrue(teamclaimsResult == 0 && teamclaimsCapture.received(expected),
                        "expected /teamclaims create '" + testCase[0] + "' to fail with '" + expected + "', got result "
                                + teamclaimsResult + " and messages " + teamclaimsCapture.all());

                CapturingCommandSource partiesCapture = new CapturingCommandSource();
                int partiesResult = TeamClaimsIntegration.getHandler().createPartyWithTeamName(
                        commandSource(helper, partiesCapture), null, ownerProfile, testCase[0]);
                helper.assertTrue(partiesResult == 0 && partiesCapture.received(expected),
                        "expected /<parties> create '" + testCase[0] + "' to fail with '" + expected + "', got result "
                                + partiesResult + " and messages " + partiesCapture.all());

                helper.assertTrue(partyManager(server).getPartyByOwner(ownerId) == null,
                        "expected no party to be created for the rejected name '" + testCase[0] + "'");
            }

            CapturingCommandSource capture = new CapturingCommandSource();
            int result = TeamClaimsCommands.createPartyWithTeamName(commandSource(helper, capture), null, ownerProfile,
                    "  Valid  §cTeam T11 ", true);
            created = partyManager(server).getPartyByOwner(ownerId);
            helper.assertTrue(result == 1 && created != null,
                    "expected a valid team name to create the party, got result " + result + " and messages " + capture.all());
            helper.assertTrue(capture.received(localized(server, "gui.xaero_parties_party_created")),
                    "expected the party created message, got " + capture.all());
            String partyName = configManager(server).getLoadedConfig(ownerId).getEffective(PlayerConfigOptions.PARTY_NAME);
            helper.assertTrue("Valid Team T11".equals(partyName),
                    "expected the sanitised party name 'Valid Team T11', got '" + partyName + "'");
            TeamConfig tc = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(created.getId());
            helper.assertTrue(tc != null && "Valid Team T11".equals(tc.getTeamName()),
                    "expected the team config to exist right away with the team name, got " + tc);
            helper.assertTrue(configManager(server).getLoadedConfig(ownerId).subConfigExists(tc.getSubConfigId()),
                    "expected the owner's team sub-config to exist right away");
            helper.succeed();
        } finally {
            if (created != null)
                disbandPartyQuiet(server, created.getId());
            resetPartyNameQuiet(server, ownerId);
        }
    }

    /**
     * 12) An option other than the claims color/name that the owner sets on the team sub-config is
     * persisted in the team config and applied to a member who joins later.
     */
    public static void lateJoinerGetsAdminSetOptions(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T12_Owner");
        GameProfile memberProfile = profile("T12_Member");
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of());
        UUID partyId = party.getId();
        TeamConfig tc = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId);
        String subId = tc.getSubConfigId();
        boolean newValue;
        try {
            IPlayerConfigAPI ownerSub = configManager(server).getLoadedConfig(ownerProfile.getId()).getSubConfig(subId);
            newValue = !ownerSub.getEffective(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS);
            SetResult set = ownerSub.tryToSet(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS, newValue);
            helper.assertTrue(set == SetResult.SUCCESS,
                    "expected the owner to set PROTECT_CLAIMED_CHUNKS on the team sub-config, got " + set);
            Object persisted = tc.getOptions().get(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS.getId());
            helper.assertTrue(Boolean.valueOf(newValue).equals(persisted),
                    "expected the option to be persisted in the team config as " + newValue + ", got " + persisted);
            helper.assertTrue(tc.toJsonString().contains(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS.getId()),
                    "expected the option in the team config JSON");
            helper.assertTrue(party.addMember(memberId, PartyMemberRank.MEMBER, memberProfile.getName()) != null,
                    "expected party.addMember to succeed");
        } catch (RuntimeException | Error e) {
            disbandPartyQuiet(server, partyId);
            throw e;
        }
        final boolean expected = newValue;
        helper.runAfterDelay(2, () -> {
            try {
                IPlayerConfigAPI memberSub = configManager(server).getLoadedConfig(memberId).getSubConfig(subId);
                helper.assertTrue(memberSub != null, "expected the late joiner to have the team sub-config '" + subId + "'");
                boolean memberValue = memberSub.getEffective(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS);
                helper.assertTrue(memberValue == expected,
                        "expected the late joiner's team sub-config PROTECT_CLAIMED_CHUNKS to be " + expected
                                + ", got " + memberValue);
                String access = memberSub.getEffective(PlayerConfigOptions.FULL_ACCESS);
                helper.assertTrue(PlayerConfigConstants.PARTY_EXCEPTION_ID.equals(access),
                        "expected the late joiner's FULL_ACCESS to be the party group, got '" + access + "'");
                helper.succeed();
            } finally {
                disbandPartyQuiet(server, partyId);
            }
        });
    }

    /**
     * 13) Team names that sanitise to the same sub-config ID (accented letters) get different IDs, and a
     * name without any ASCII letter or digit (CJK) still gets a meaningful one. All are valid and short
     * enough for OPAC's config packets.
     */
    public static void nonAsciiTeamNamesGetDistinctSubIds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String[] names = {"Équipe Ünïque", "Èquipe Ùnïque", "東京チーム"};
        List<GameProfile> owners = new ArrayList<>();
        List<UUID> partyIds = new ArrayList<>();
        try {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                GameProfile owner = profile("T13_Owner" + i);
                owners.add(owner);
                SetResult nameSet = configManager(server).getLoadedConfig(owner.getId())
                        .tryToSet(PlayerConfigOptions.PARTY_NAME, names[i]);
                helper.assertTrue(nameSet == SetResult.SUCCESS,
                        "expected PARTY_NAME '" + names[i] + "' to be accepted, got " + nameSet);
                IServerPartyAPI party = createPartyWithTeam(server, owner, List.of());
                partyIds.add(party.getId());
                ids.add(TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId());
            }
            helper.assertTrue(!ids.get(0).equals(ids.get(1)),
                    "expected different sub-config IDs for '" + names[0] + "' and '" + names[1] + "', both got " + ids.get(0));
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                helper.assertTrue(id.startsWith("team_") && PlayerConfig.isValidSubIdOrTeam(id)
                                && id.length() <= TeamConfig.MAX_SUB_CONFIG_ID_LENGTH,
                        "expected a valid team sub-config ID for '" + names[i] + "', got '" + id + "'");
                helper.assertTrue(id.substring("team_".length()).matches(".*[a-z0-9].*"),
                        "expected a meaningful (non-degenerate) sub-config ID for '" + names[i] + "', got '" + id + "'");
            }
            helper.succeed();
        } finally {
            for (UUID partyId : partyIds) disbandPartyQuiet(server, partyId);
            for (GameProfile owner : owners) resetPartyNameQuiet(server, owner.getId());
        }
    }

    /**
     * 14) A corrupt team config file is preserved as {@code <name>.json.corrupt-<timestamp>} instead of
     * being overwritten later, and a team config in the original format (with the unused legacy
     * settings and without options) still loads, without writing those legacy settings back.
     * <p>
     * Uses {@link TeamConfigManager#loadConfigFile}, the per-file step of the server start loading,
     * on files of its own: reloading every team config of the running server would interfere with
     * the other tests.
     */
    public static void corruptTeamConfigIsPreserved(GameTestHelper helper) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        Path dir = tcm.getConfigDir();
        UUID corruptId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();
        Path corruptFile = dir.resolve(corruptId + ".json");
        Path legacyFile = dir.resolve(legacyId + ".json");
        String corruptContent = "{ \"partyId\": \"" + corruptId + "\", \"teamName\": ";
        try {
            Files.createDirectories(dir);
            Files.writeString(corruptFile, corruptContent);
            helper.assertTrue(tcm.loadConfigFile(corruptFile) == null, "expected the corrupt team config not to load");
            helper.assertTrue(!Files.exists(corruptFile), "expected the corrupt file to be moved away");
            List<Path> preserved;
            try (Stream<Path> files = Files.list(dir)) {
                preserved = files.filter(p -> p.getFileName().toString().startsWith(corruptId + ".json.corrupt-")).toList();
            }
            helper.assertTrue(preserved.size() == 1,
                    "expected exactly one preserved '" + corruptId + ".json.corrupt-*' file, got " + preserved);
            helper.assertTrue(corruptContent.equals(Files.readString(preserved.get(0))),
                    "expected the preserved file to keep the original content");

            UUID member = UUID.randomUUID();
            Files.writeString(legacyFile, "{\"partyId\":\"" + legacyId + "\",\"teamName\":\"Legacy\","
                    + "\"subConfigId\":\"team_legacy\",\"members\":[\"" + member + "\"],"
                    + "\"settings\":{\"forceLoadShared\":true,\"allowMemberClaims\":true,"
                    + "\"opac.CLAIMS_COLOR\":1193046,\"opac.CLAIMS_NAME\":\"Legacy\"}}");
            TeamConfig legacy = tcm.loadConfigFile(legacyFile);
            helper.assertTrue(legacy != null && legacyId.equals(legacy.getPartyId())
                            && "team_legacy".equals(legacy.getSubConfigId()) && legacy.isMember(member)
                            && Integer.valueOf(1193046).equals(legacy.getClaimsColor())
                            && "Legacy".equals(legacy.getClaimsName()) && legacy.getOptions().isEmpty(),
                    "expected a team config in the original format to load unchanged, got " + legacy);
            String rewritten = legacy.toJsonString();
            helper.assertTrue(!rewritten.contains("forceLoadShared") && !rewritten.contains("allowMemberClaims"),
                    "expected the unused legacy settings not to be written back, got " + rewritten);
            helper.succeed();
        } catch (IOException e) {
            throw new IllegalStateException("file access failed in the corrupt team config test", e);
        } finally {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.filter(p -> p.getFileName().toString().startsWith(corruptId.toString())
                        || p.getFileName().toString().startsWith(legacyId.toString())).toList())
                    Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 15) Team forceload tickets stay balanced: activating the team adds exactly one ticket for its
     * forceloaded team claim (twice is still one), unforceloading removes it, re-forceloading while
     * active adds it again and unclaiming removes it, leaving no ticket behind. Checked both on the
     * handler's own bookkeeping and on the chunk's real ticket level ({@code 31} = a forceload-style
     * region ticket of distance 2). Nobody is online, so OPAC's own ticket for the claim stays off.
     * <p>
     * Team forceloads are only active while a member is online, and the gametest server has no
     * players, so the test activates the team by hand one tick after the setup (the party events of
     * the setup tick re-check the activation and correctly turn it off). Should the once-a-minute
     * safety net happen to do the same between activation and check, the check is repeated.
     */
    public static void teamForceloadTicketsAreBalanced(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T15_Owner");
        GameProfile memberProfile = profile("T15_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        int x0 = 15000;
        IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
        UUID partyId = party.getId();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamForceLoadHandler handler = TeamClaimsCommon.getForceLoadHandler();
        Runnable cleanup = () -> {
            unclaimQuiet(server, x0, 0);
            cm.deactivateTeamForceLoads(partyId);
            disbandPartyQuiet(server, partyId);
        };
        try {
            int levelBefore = ticketLevelOf(helper, x0, 0);
            helper.assertTrue(levelBefore > 31,
                    "test precondition: expected no ticket at chunk (" + x0 + ",0) yet, got level " + levelBefore);
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
            ClaimResult<IPlayerChunkClaimAPI> c = doClaim(server, ownerId, teamSubIndexOf(server, subId, ownerId), x0, 0);
            helper.assertTrue(c.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "expected the team claim to succeed, got " + c.getResultType());
            ClaimResult<IPlayerChunkClaimAPI> f = doForceload(server, memberId, x0, 0, true);
            helper.assertTrue(f.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "expected the forceload to succeed, got " + f.getResultType());
            helper.assertTrue(!handler.hasTicket(OVERWORLD, x0, 0),
                    "expected no team ticket while no member is online (team inactive)");
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(1, () -> {
            try {
                activateAndCheckTicket(helper, cm, handler, partyId, x0, 3, () -> {
                    int heldBefore = handler.getHeldTicketCount() - 1;
                    ClaimResult<IPlayerChunkClaimAPI> off = doForceload(server, memberId, x0, 0, false);
                    helper.assertTrue(off.getResultType() == ClaimResult.Type.SUCCESSFUL_UNFORCELOAD,
                            "expected the unforceload to succeed, got " + off.getResultType());
                    helper.assertTrue(!handler.hasTicket(OVERWORLD, x0, 0) && handler.getHeldTicketCount() == heldBefore,
                            "expected the team ticket to be removed by the unforceload");
                    ClaimResult<IPlayerChunkClaimAPI> on = doForceload(server, ownerId, x0, 0, true);
                    helper.assertTrue(on.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                            "expected the re-forceload to succeed, got " + on.getResultType());
                    helper.assertTrue(handler.hasTicket(OVERWORLD, x0, 0) && handler.getHeldTicketCount() == heldBefore + 1,
                            "expected the re-forceload of an active team to add the ticket again");
                    ClaimResult<IPlayerChunkClaimAPI> un = doUnclaim(server, memberId, x0, 0);
                    helper.assertTrue(un.getResultType() == ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                            "expected the teammate unclaim to succeed, got " + un.getResultType());
                    helper.assertTrue(!handler.hasTicket(OVERWORLD, x0, 0) && handler.getHeldTicketCount() == heldBefore,
                            "expected the unclaim to remove the team ticket");
                    helper.runAfterDelay(3, () -> {
                        try {
                            int level = ticketLevelOf(helper, x0, 0);
                            helper.assertTrue(level > 31,
                                    "expected no ticket left behind at chunk (" + x0 + ",0), got level " + level);
                            helper.succeed();
                        } finally {
                            cleanup.run();
                        }
                    });
                }, cleanup);
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
        });
    }

    /**
     * Activates the team (twice, which must still add exactly one ticket), waits for the chunk ticket
     * level to update and checks it, then runs {@code next} in that same tick. Retries if the team was
     * deactivated in between (by the safety net, since nobody is online).
     */
    private static void activateAndCheckTicket(GameTestHelper helper, TeamClaimManager cm, TeamForceLoadHandler handler,
            UUID partyId, int x0, int attemptsLeft, Runnable next, Runnable cleanup) {
        int heldBefore = handler.getHeldTicketCount();
        cm.activateTeamForceLoads(partyId);
        cm.activateTeamForceLoads(partyId);
        helper.assertTrue(handler.hasTicket(OVERWORLD, x0, 0) && handler.getHeldTicketCount() == heldBefore + 1,
                "expected exactly one team ticket after activating (twice), held " + handler.getHeldTicketCount()
                        + " vs " + heldBefore + " before");
        helper.runAfterDelay(3, () -> {
            try {
                if (!handler.hasTicket(OVERWORLD, x0, 0) && attemptsLeft > 1) {
                    activateAndCheckTicket(helper, cm, handler, partyId, x0, attemptsLeft - 1, next, cleanup);
                    return;
                }
                int level = ticketLevelOf(helper, x0, 0);
                helper.assertTrue(level <= 31, "expected the team ticket to be in effect (level <= 31), got " + level
                        + " (debug data: " + helper.getLevel().getChunkSource().getChunkDebugData(new ChunkPos(x0, 0)) + ")");
                next.run();
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
        });
    }
}
