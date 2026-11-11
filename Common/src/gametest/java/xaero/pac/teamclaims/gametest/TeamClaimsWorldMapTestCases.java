package xaero.pac.teamclaims.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import xaero.pac.client.teamclaims.WorldMapClaimingLogic;
import xaero.pac.teamclaims.config.TeamClaimsClientConfig.ButtonPosition;

/**
 * Dev/test-only, loader-neutral test bodies for the pure rules behind the Team Claims entries and button on the map screen
 * of Xaero's World Map ({@link WorldMapClaimingLogic}). It touches no client and no World Map class, so it runs on the
 * headless gametest server. Same conventions as {@link TeamClaimsHubTestCases}.
 */
public final class TeamClaimsWorldMapTestCases {

    private TeamClaimsWorldMapTestCases() {}

    /**
     * An entry is usable only while the player claims as a player; the team entry also needs a team sub-config, the private
     * one does not.
     */
    public static void entriesAreUsableOnlyAsAPlayerAndTheTeamOneWithATeamSub(GameTestHelper helper) {
        helper.assertTrue(WorldMapClaimingLogic.isUsable(false, true, false), "expected the private entry to work as a player without a team");
        helper.assertTrue(WorldMapClaimingLogic.isUsable(false, true, true), "expected the private entry to work as a player with a team");
        helper.assertTrue(WorldMapClaimingLogic.isUsable(true, true, true), "expected the team entry to work as a player with a team");
        helper.assertTrue(!WorldMapClaimingLogic.isUsable(true, true, false), "expected the team entry not to work without a team sub-config");
        for (boolean team : new boolean[]{false, true})
            for (boolean hasTeamSub : new boolean[]{false, true})
                helper.assertTrue(!WorldMapClaimingLogic.isUsable(team, false, hasTeamSub),
                        "expected no entry to work outside the player claiming mode (team=" + team + ", hasTeamSub=" + hasTeamSub + ")");
        helper.succeed();
    }

    /**
     * A one-shot claim puts the claim mode back exactly when it claimed with the other choice than the one selected.
     */
    public static void oneShotRestoresOnlyWhenItSwitched(GameTestHelper helper) {
        helper.assertTrue(!WorldMapClaimingLogic.restoresAfterOneShot(false, false), "expected no restore for private claimed while private was selected");
        helper.assertTrue(!WorldMapClaimingLogic.restoresAfterOneShot(true, true), "expected no restore for team claimed while team was selected");
        helper.assertTrue(WorldMapClaimingLogic.restoresAfterOneShot(false, true), "expected a restore for team claimed while private was selected");
        helper.assertTrue(WorldMapClaimingLogic.restoresAfterOneShot(true, false), "expected a restore for private claimed while team was selected");
        helper.succeed();
    }

    /**
     * The counts read {@code used/limit} with the infinity sign for the biggest limit, and the labels carry the mode and the
     * counts as arguments of their translation keys.
     */
    public static void labelsShowTheModeAndTheCounts(GameTestHelper helper) {
        helper.assertTrue("34/120".equals(WorldMapClaimingLogic.formatCount(34, 120)), "expected 34/120, got " + WorldMapClaimingLogic.formatCount(34, 120));
        helper.assertTrue("0/∞".equals(WorldMapClaimingLogic.formatCount(0, Integer.MAX_VALUE)), "expected 0/infinity for the biggest limit, got " + WorldMapClaimingLogic.formatCount(0, Integer.MAX_VALUE));

        TranslatableContents team = translatable(WorldMapClaimingLogic.buttonLabel(true, 34, 120), helper);
        helper.assertTrue(WorldMapClaimingLogic.KEY_BUTTON.equals(team.getKey()) && team.getArgs().length == 1, "expected the button key with one argument, got " + team.getKey());
        Component teamMode = (Component) team.getArgs()[0];
        helper.assertTrue(teamMode.getString().endsWith(" 34/120"), "expected the counts at the end of the mode argument, got " + teamMode.getString());
        helper.assertTrue(teamMode.getSiblings().size() == 2 && WorldMapClaimingLogic.KEY_MODE_TEAM.equals(translatable(teamMode.getSiblings().get(0), helper).getKey()),
                "expected the team mode name first in the mode argument, got " + teamMode);

        Component unknown = (Component) translatable(WorldMapClaimingLogic.buttonLabel(false, null, 0), helper).getArgs()[0];
        helper.assertTrue(WorldMapClaimingLogic.KEY_MODE_PRIVATE.equals(translatable(unknown, helper).getKey()),
                "expected only the private mode name while the counts are unknown, got " + unknown);

        TranslatableContents toggle = translatable(WorldMapClaimingLogic.toggleLabel(false), helper);
        helper.assertTrue(WorldMapClaimingLogic.KEY_TOGGLE_MODE.equals(toggle.getKey()) && toggle.getArgs().length == 1
                        && WorldMapClaimingLogic.KEY_MODE_TEAM.equals(translatable((Component) toggle.getArgs()[0], helper).getKey()),
                "expected the toggle label to name only the mode it switches to (team) while the current one is private");
        helper.succeed();
    }

    /**
     * Whatever the screen size, the button stays fully on the screen, sits on the side its position names, and the centred one
     * is centred.
     */
    public static void buttonPositionStaysOnTheScreenOnTheNamedSide(GameTestHelper helper) {
        int w = WorldMapClaimingLogic.BUTTON_WIDTH;
        int h = WorldMapClaimingLogic.BUTTON_HEIGHT;
        for (int[] size : new int[][]{{427, 240}, {640, 360}, {854, 480}, {1920, 1080}}) {
            for (ButtonPosition position : ButtonPosition.values()) {
                int[] at = WorldMapClaimingLogic.buttonPosition(position, size[0], size[1], w, h);
                helper.assertTrue(at[0] >= 0 && at[1] >= 0 && at[0] + w <= size[0] && at[1] + h <= size[1],
                        "expected the button of " + position + " fully on a " + size[0] + "x" + size[1] + " screen, got " + at[0] + "," + at[1]);
                boolean top = position == ButtonPosition.TOP_LEFT || position == ButtonPosition.TOP_CENTER || position == ButtonPosition.TOP_RIGHT;
                helper.assertTrue(top == at[1] < size[1] / 2, "expected " + position + " on the " + (top ? "top" : "bottom") + " half, got y " + at[1]);
                if (position == ButtonPosition.TOP_LEFT || position == ButtonPosition.BOTTOM_LEFT)
                    helper.assertTrue(at[0] + w / 2 < size[0] / 2, "expected " + position + " on the left half, got x " + at[0]);
                if (position == ButtonPosition.TOP_RIGHT || position == ButtonPosition.BOTTOM_RIGHT)
                    helper.assertTrue(at[0] + w / 2 > size[0] / 2, "expected " + position + " on the right half, got x " + at[0]);
                if (position == ButtonPosition.TOP_CENTER)
                    helper.assertTrue(Math.abs(at[0] + w / 2 - size[0] / 2) <= 1, "expected the centred button centred, got x " + at[0]);
            }
        }
        helper.succeed();
    }

    private static TranslatableContents translatable(Component component, GameTestHelper helper) {
        helper.assertTrue(component.getContents() instanceof TranslatableContents, "expected a translatable component, got " + component);
        return (TranslatableContents) component.getContents();
    }
}
