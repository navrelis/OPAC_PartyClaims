package xaero.pac.teamclaims.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.client.gui.hub.ClaimCategory;
import xaero.pac.client.gui.hub.map.ClaimOverlay;
import xaero.pac.client.gui.hub.map.TerrainSchedule;
import xaero.pac.client.gui.hub.map.TerrainShading;

import java.util.List;
import java.util.Set;

/**
 * Dev/test-only, loader-neutral test bodies for the pure rules behind the map of the Claims tab of the hub screen
 * ({@link TerrainShading}, {@link TerrainSchedule}, {@link ClaimOverlay} and the ally rule of {@link ClaimCategory}). None of them
 * touches a client class, so they run on the headless gametest server. Same conventions as {@link TeamClaimsHubTestCases}:
 * every test is registered with the loader's empty structure template and ends with {@code helper.succeed()}.
 */
public final class TeamClaimsMapTestCases {

    private TeamClaimsMapTestCases() {}

    // ==================== Tests ====================

    /**
     * Land is shaded like on a vanilla map: higher than the block to the north is lighter, lower is darker, level ground
     * follows the checkerboard noise only, and the steps are the ones of the vanilla map colours (180, 220, 255 of 255).
     */
    public static void landIsShadedByTheHeightDifferenceToTheNorth(GameTestHelper helper) {
        // level: the noise alone (+-0.2) stays in the normal step
        helper.assertTrue(TerrainShading.landBrightness(64, 64, false) == TerrainShading.NORMAL && TerrainShading.landBrightness(64, 64, true) == TerrainShading.NORMAL,
                "expected level ground to be NORMAL on both noise parities");
        // one block up: 0.8 + 0.2 on an odd column is above 0.6 (the 0.6 of an even column is left out: it sits on the edge of the step)
        helper.assertTrue(TerrainShading.landBrightness(65, 64, true) == TerrainShading.HIGH, "expected one block up on an odd column to be HIGH");
        helper.assertTrue(TerrainShading.landBrightness(70, 64, false) == TerrainShading.HIGH, "expected a rise of 6 blocks to be HIGH");
        helper.assertTrue(TerrainShading.landBrightness(63, 64, false) == TerrainShading.LOW, "expected one block down on an even column to be LOW");
        helper.assertTrue(TerrainShading.landBrightness(40, 64, true) == TerrainShading.LOW, "expected a drop of 24 blocks to be LOW");

        int stone = 0x707070;
        helper.assertTrue(TerrainShading.shade(stone, TerrainShading.HIGH) == 0xFF707070, "expected HIGH to keep the colour");
        helper.assertTrue(TerrainShading.shade(stone, TerrainShading.NORMAL) == (0xFF000000 | (0x70 * 220 / 255) * 0x010101), "expected NORMAL to be 220/255 of the colour");
        helper.assertTrue(TerrainShading.shade(stone, TerrainShading.LOW) == (0xFF000000 | (0x70 * 180 / 255) * 0x010101), "expected LOW to be 180/255 of the colour");
        helper.assertTrue(TerrainShading.shade(stone, TerrainShading.LOWEST) == (0xFF000000 | (0x70 * 135 / 255) * 0x010101), "expected LOWEST to be 135/255 of the colour");
        int shaded = TerrainShading.shade(0xFF8000, TerrainShading.NORMAL);
        helper.assertTrue((shaded >>> 24) == 0xFF && (shaded >> 16 & 255) == 255 * 220 / 255 && (shaded >> 8 & 255) == 128 * 220 / 255 && (shaded & 255) == 0,
                "expected the three channels to be shaded alone and the alpha to be opaque, got " + Integer.toHexString(shaded));
        helper.succeed();
    }

    /**
     * Water is darker the deeper it is, as on a vanilla map: shallow water is light, deep water is dark, and the noise
     * of the odd columns pushes it one step darker earlier.
     */
    public static void waterIsDarkerTheDeeperItIs(GameTestHelper helper) {
        helper.assertTrue(TerrainShading.waterBrightness(1, false) == TerrainShading.HIGH && TerrainShading.waterBrightness(1, true) == TerrainShading.HIGH, "expected 1 block of water to be HIGH");
        helper.assertTrue(TerrainShading.waterBrightness(4, false) == TerrainShading.HIGH && TerrainShading.waterBrightness(4, true) == TerrainShading.NORMAL, "expected 4 blocks to be HIGH on even and NORMAL on odd columns");
        helper.assertTrue(TerrainShading.waterBrightness(6, false) == TerrainShading.NORMAL, "expected 6 blocks of water to be NORMAL");
        helper.assertTrue(TerrainShading.waterBrightness(10, false) == TerrainShading.LOW && TerrainShading.waterBrightness(10, true) == TerrainShading.LOW, "expected 10 blocks of water to be LOW");
        int previous = TerrainShading.HIGH;
        for (int depth = 0; depth <= 30; depth++) {
            int brightness = TerrainShading.waterBrightness(depth, false);
            helper.assertTrue(rank(brightness) <= rank(previous), "expected deeper water never to be lighter, depth " + depth);
            previous = brightness;
        }
        helper.succeed();
    }

    private static int rank(int brightness) {
        // HIGH is the lightest, LOW the darkest of the three used here
        return switch (brightness) {
            case TerrainShading.HIGH -> 2;
            case TerrainShading.NORMAL -> 1;
            default -> 0;
        };
    }

    /**
     * Never more than 8 chunks are picked in a tick, the ones never drawn come before the ones that only wait for the
     * refresh, only chunks older than 5 seconds are refreshed, and of the due ones the nearest to the centre go first.
     */
    public static void terrainScheduleKeepsTheBudgetAndPicksTheNearestFirst(GameTestHelper helper) {
        helper.assertTrue(TerrainSchedule.MAX_PER_TICK == 8 && TerrainSchedule.REFRESH_TICKS == 100, "expected 8 chunks per tick and a refresh every 5 seconds (100 ticks)");
        helper.assertTrue(TerrainSchedule.isDue(TerrainSchedule.NEVER, 0), "expected a chunk that was never drawn to be due");
        helper.assertTrue(!TerrainSchedule.isDue(1000, 1000) && !TerrainSchedule.isDue(1000, 1099), "expected a chunk drawn less than 100 ticks ago not to be due");
        helper.assertTrue(TerrainSchedule.isDue(1000, 1100), "expected a chunk drawn 100 ticks ago to be due");

        int count = 30;
        long[] last = new long[count];
        int[] distance = new int[count];
        for (int i = 0; i < count; i++) {
            last[i] = TerrainSchedule.NEVER;
            distance[i] = (count - i) * (count - i); // the last candidate is the nearest
        }
        int[] picked = TerrainSchedule.pickDue(last, distance, count, 5, TerrainSchedule.MAX_PER_TICK);
        helper.assertTrue(picked.length == 8, "expected 8 picks out of 30 due candidates, got " + picked.length);
        for (int i = 0; i < picked.length; i++)
            helper.assertTrue(picked[i] == count - 1 - i, "expected the nearest candidates first, got " + picked[i] + " at " + i);

        // some are fresh: only the stale ones and the never drawn ones are picked
        last[count - 1] = 4; // the nearest was just drawn
        last[count - 2] = 4;
        picked = TerrainSchedule.pickDue(last, distance, count, 5, 3);
        helper.assertTrue(picked.length == 3 && picked[0] == count - 3 && picked[1] == count - 4 && picked[2] == count - 5,
                "expected the fresh candidates to be skipped, got " + java.util.Arrays.toString(picked));
        last[count - 1] = 5 - TerrainSchedule.REFRESH_TICKS;
        picked = TerrainSchedule.pickDue(last, distance, count, 5, 1);
        helper.assertTrue(picked.length == 1 && picked[0] == count - 1, "expected a stale candidate to be refreshed, nearest first, got " + java.util.Arrays.toString(picked));

        // fewer due than the budget, none due, and no budget
        long[] fresh = {10, 10, TerrainSchedule.NEVER};
        int[] near = {1, 2, 3};
        picked = TerrainSchedule.pickDue(fresh, near, 3, 11, 8);
        helper.assertTrue(picked.length == 1 && picked[0] == 2, "expected only the one due candidate, got " + java.util.Arrays.toString(picked));
        helper.assertTrue(TerrainSchedule.pickDue(fresh, near, 2, 11, 8).length == 0, "expected nothing to pick when all are fresh");
        helper.assertTrue(TerrainSchedule.pickDue(last, distance, count, 5, 0).length == 0, "expected nothing to pick without a budget");
        helper.succeed();
    }

    /**
     * The claim colour over terrain is a mix with the alpha, opaque over the plain background, and the lines go to the
     * north and west edge of a claim next to another owner or to nothing, and to the east and south edge next to nothing,
     * so that two claims share one line; the unclaimed and the edge of the grid get none.
     */
    public static void claimOverlayMixesColoursAndDrawsOneLineBetweenOwners(GameTestHelper helper) {
        helper.assertTrue(ClaimOverlay.blend(0xFF000000, 0xFF0000, 255) == 0xFFFF0000, "expected the full alpha to give the claim colour");
        helper.assertTrue(ClaimOverlay.blend(0xFF123456, 0xFF0000, 0) == 0xFF123456, "expected no alpha to keep the terrain");
        int half = ClaimOverlay.blend(0xFF000000, 0xFFFFFF, 128);
        helper.assertTrue((half >>> 24) == 0xFF && (half & 255) == 128 && (half >> 8 & 255) == 128 && (half >> 16 & 255) == 128, "expected half of white over black to be grey and opaque, got " + Integer.toHexString(half));
        helper.assertTrue(ClaimOverlay.CLAIM_ALPHA > 0x40 && ClaimOverlay.CLAIM_ALPHA < 0xC0, "expected the claim colour to be see-through but clear");
        int border = ClaimOverlay.borderColor(0xFFFFFF);
        helper.assertTrue((border >>> 24) == 0xFF && (border & 255) < 255 && (border & 255) > 0, "expected the border colour to be a darker, opaque version of the claim colour");

        Object a = "owner a";
        Object b = "owner b";
        int all = ClaimOverlay.NORTH | ClaimOverlay.EAST | ClaimOverlay.SOUTH | ClaimOverlay.WEST;
        helper.assertTrue(ClaimOverlay.edges(null, a, b, null, ClaimOverlay.OUTSIDE) == 0, "expected an unclaimed chunk to get no lines");
        helper.assertTrue(ClaimOverlay.edges(a, a, a, a, a) == 0, "expected the inside of a claim to get no lines");
        helper.assertTrue(ClaimOverlay.edges(a, null, null, null, null) == all, "expected a lone claim to be outlined on all four edges");
        helper.assertTrue(ClaimOverlay.edges(a, ClaimOverlay.OUTSIDE, ClaimOverlay.OUTSIDE, ClaimOverlay.OUTSIDE, ClaimOverlay.OUTSIDE) == 0, "expected the edge of the grid to get no lines");
        // two claims side by side: only the one to the east (west edge) or south (north edge) draws the shared line
        helper.assertTrue(ClaimOverlay.edges(a, a, b, a, a) == 0, "expected the west claim not to draw the line it shares with another claim");
        helper.assertTrue(ClaimOverlay.edges(b, b, b, b, a) == ClaimOverlay.WEST, "expected the east claim to draw the shared line on its west edge");
        helper.assertTrue(ClaimOverlay.edges(a, a, a, b, a) == 0, "expected the north claim not to draw the line it shares with another claim");
        helper.assertTrue(ClaimOverlay.edges(b, a, b, b, b) == ClaimOverlay.NORTH, "expected the south claim to draw the shared line on its north edge");
        helper.assertTrue(ClaimOverlay.edges(a, null, a, a, ClaimOverlay.OUTSIDE) == ClaimOverlay.NORTH, "expected the open north edge only");
        helper.assertTrue(ClaimOverlay.edges(a, a, null, a, a) == ClaimOverlay.EAST, "expected the open east edge to be drawn by the claim itself");
        helper.assertTrue(ClaimOverlay.edges(a, a, a, null, a) == ClaimOverlay.SOUTH, "expected the open south edge to be drawn by the claim itself");
        helper.succeed();
    }

    /**
     * Claims of allied parties are told apart from other players, but never win over the party of the local player,
     * the server, expired claims or the local player. The owner is matched by the name of the party that owns the claim, or
     * by the default name of the party that is made from the name of its owner.
     */
    public static void alliedClaimsAreTheirOwnCategory(GameTestHelper helper) {
        // classify(server, expired, self, partyMember, partyOwned, team, ally)
        helper.assertTrue(ClaimCategory.classify(false, false, false, false, false, false, true) == ClaimCategory.ALLY, "expected ALLY for the claim of an ally");
        helper.assertTrue(ClaimCategory.classify(false, false, false, false, true, false, true) == ClaimCategory.ALLY, "expected ALLY for the party claim of an allied party");
        helper.assertTrue(ClaimCategory.classify(false, false, false, false, false, false, false) == ClaimCategory.OTHER, "expected OTHER without the ally flag");
        helper.assertTrue(ClaimCategory.classify(false, false, false, true, false, false, true) == ClaimCategory.PARTY, "expected PARTY to win over ALLY");
        helper.assertTrue(ClaimCategory.classify(false, false, false, true, true, false, true) == ClaimCategory.PARTY, "expected a party claim of a member to be PARTY");
        helper.assertTrue(ClaimCategory.classify(false, false, true, false, false, false, true) == ClaimCategory.OWN_PRIVATE, "expected OWN_PRIVATE to win over ALLY");
        helper.assertTrue(ClaimCategory.classify(true, false, false, false, false, false, true) == ClaimCategory.SERVER, "expected SERVER to win over ALLY");
        helper.assertTrue(ClaimCategory.classify(false, true, false, false, false, false, true) == ClaimCategory.EXPIRED, "expected EXPIRED to win over ALLY");

        List<String> names = List.of("The Wanderers", "Alice's Party");
        List<String> defaults = List.of("Wanda's Party", "Alice's Party");
        helper.assertTrue(ClaimCategory.isAllyOwner(names, defaults, "The Wanderers", "Bob"), "expected a claim owned by an allied party to be matched by the party name");
        helper.assertTrue(ClaimCategory.isAllyOwner(names, defaults, null, "Wanda"), "expected the owner of an allied party to be matched by the default party name");
        helper.assertTrue(!ClaimCategory.isAllyOwner(names, defaults, "Other Party", "Bob"), "expected another party not to be an ally");
        helper.assertTrue(!ClaimCategory.isAllyOwner(names, defaults, null, "Bob"), "expected another player not to be an ally");
        helper.assertTrue(!ClaimCategory.isAllyOwner(names, defaults, null, null), "expected an unknown owner not to be an ally");
        helper.assertTrue(!ClaimCategory.isAllyOwner(List.of(), List.of(), "The Wanderers", "Wanda"), "expected nobody to be an ally without allies");
        helper.assertTrue(ClaimCategory.isAllyOwner(Set.of("X"), Set.of(), "X", null), "expected any collection of names to work");
        helper.succeed();
    }

}
