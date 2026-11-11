package xaero.pac.teamclaims.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.client.gui.configview.ConfigOptionGrouping;
import xaero.pac.client.gui.configview.ConfigResetRule;
import xaero.pac.client.gui.configview.ConfigViewLayout;
import xaero.pac.client.gui.configview.ConfigViewLayout.Entry;
import xaero.pac.client.gui.configview.ConfigViewLayout.Row;
import xaero.pac.client.gui.configview.ConfigViewLayout.RowKind;
import xaero.pac.client.gui.configview.ConfigViewState;
import xaero.pac.common.server.player.config.PlayerConfigOptionSpec;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev/test-only, loader-neutral test bodies for the pure rules behind the player config screen (groups, collapsing,
 * search, fillers, reset button: {@link ConfigOptionGrouping}, {@link ConfigViewState}, {@link ConfigViewLayout},
 * {@link ConfigResetRule}). None of them touches a client class, so they run on the headless gametest server. Same
 * conventions as {@link TeamClaimsHubTestCases}.
 */
public final class TeamClaimsConfigViewTestCases {

    private TeamClaimsConfigViewTestCases() {}

    private static Entry entry(String group, String name, String id, String tooltip) {
        return Entry.of(group, name, id, tooltip);
    }

    /** {@code groups} groups named g0, g1, ... with {@code perGroup} options each. */
    private static List<Entry> entries(int groups, int perGroup) {
        List<Entry> entries = new ArrayList<>();
        for (int g = 0; g < groups; g++)
            for (int o = 0; o < perGroup; o++)
                entries.add(entry("g" + g, "Option " + g + "-" + o, "g" + g + ".option" + o, "Tooltip " + g + "-" + o));
        return entries;
    }

    private static List<Integer> optionIndexes(List<Row> rows) {
        List<Integer> indexes = new ArrayList<>();
        for (Row row : rows)
            if (row.kind() == RowKind.OPTION)
                indexes.add(row.entryIndex());
        return indexes;
    }

    private static List<String> headerKeys(List<Row> rows) {
        List<String> keys = new ArrayList<>();
        for (Row row : rows)
            if (row.kind() == RowKind.HEADER)
                keys.add(row.groupKey());
        return keys;
    }

    private static JsonObject readLang(String file) throws IOException {
        try (InputStream in = ConfigOptionGrouping.class.getResourceAsStream("/assets/openpartiesandclaims/lang/" + file)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    // ==================== Tests ====================

    /**
     * The group is the first segment of the shortened id, {@code general} without one, and the options under
     * {@code claims.protection.} are split by category, except the {@code GENERAL*} categories.
     */
    public static void groupIsTheFirstSegmentAndProtectionIsSplitByCategory(GameTestHelper helper) {
        String[][] cases = {
                {"claims.name", "GENERAL_CLAIMS", "claims"},
                {"claims.forceload.enabled", "GENERAL_CLAIMS", "claims"},
                {"claims.protectClaimedChunks", "GENERAL_CLAIMS", "claims"},
                {"parties.shareLocationWithParty", "GENERAL_PARTY", "parties"},
                {"bonusPlayerGroups", "GENERAL", "general"},
                {"claims.protection.exceptions.fullAccess", "GENERAL_CLAIMS", "claims"},
                {"claims.protection.exceptions.blocksByPlayers", "BLOCK_PROTECTION", "claims.protection.block_protection"},
                {"claims.protection.fluidBarrier", "BLOCK_PROTECTION", "claims.protection.block_protection"},
                {"claims.protection.exceptions.platesByMobs", "BLOCK_TRIGGERS", "claims.protection.block_triggers"},
                {"claims.protection.exceptions.xpPickup", "PICKUP_PROTECTION", "claims.protection.pickup_protection"},
                {"othermod.feature.value", "GENERAL", "othermod"},
        };
        for (String[] c : cases) {
            String key = ConfigOptionGrouping.groupKey(c[0], c[1]);
            helper.assertTrue(key.equals(c[2]), "expected the option " + c[0] + " (" + c[1] + ") in the group " + c[2] + ", got " + key);
        }
        helper.assertTrue(ConfigOptionGrouping.fallbackTitle("my_mod").equals("My Mod") && ConfigOptionGrouping.fallbackTitle("claims.protection.mixed_protection").equals("Mixed Protection"),
                "expected a readable fallback title from the last segment");
        helper.succeed();
    }

    /**
     * Every directly configurable option that exists gets a group with 3 to 30 options (only {@code general} may have
     * fewer), and every group has a title in the English and in the German language file.
     */
    public static void everyRealOptionHasAGroupWithATitleInBothLanguages(GameTestHelper helper) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IPlayerConfigOptionSpecAPI<?> api : PlayerConfigOptions.OPTIONS.values()) {
            PlayerConfigOptionSpec<?> option = (PlayerConfigOptionSpec<?>) api;
            if (!option.isSyncable() || !option.isDirectlyConfigurable()
                    || option == PlayerConfigOptions.USED_SUBCLAIM || option == PlayerConfigOptions.USED_SERVER_SUBCLAIM
                    || option == PlayerConfigOptions.USED_PARTY_SUBCLAIM)
                continue;
            counts.merge(ConfigOptionGrouping.groupKey(option.getShortenedId(), option.getCategory().name()), 1, Integer::sum);
        }
        try {
            JsonObject english = readLang("en_us.json");
            JsonObject german = readLang("de_de.json");
            for (Map.Entry<String, Integer> group : counts.entrySet()) {
                String key = group.getKey();
                int count = group.getValue();
                helper.assertTrue(count <= 30, "expected at most 30 options in the group " + key + ", got " + count + " (all groups: " + counts + ")");
                helper.assertTrue(count >= 3 || key.equals(ConfigOptionGrouping.GENERAL), "expected at least 3 options in the group " + key + ", got " + count + " (all groups: " + counts + ")");
                String langKey = "gui.xaero_pac_config_group_" + key;
                helper.assertTrue(english.has(langKey) && german.has(langKey), "expected a title for the group " + key + " in en_us.json and de_de.json (" + langKey + ")");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        helper.assertTrue(counts.size() >= 10, "expected the real options to fall into at least 10 groups, got " + counts);
        helper.succeed();
    }

    /**
     * A long list opens only its first group the first time, a list that fits a page opens every group, a group that
     * shows up later in a list that was already seen starts collapsed (and in a short list expanded).
     */
    public static void longListsOpenOnlyTheFirstGroupAndShortListsOpenAll(GameTestHelper helper) {
        ConfigViewState longState = new ConfigViewState();
        List<Row> rows = ConfigViewLayout.compute(entries(4, 4), longState, "", 0);
        helper.assertTrue(headerKeys(rows).equals(List.of("g0", "g1", "g2", "g3")), "expected a header for every group, got " + headerKeys(rows));
        helper.assertTrue(longState.isExpanded("g0") && !longState.isExpanded("g1") && !longState.isExpanded("g2") && !longState.isExpanded("g3"),
                "expected only the first group of a 16 option list to be expanded");
        helper.assertTrue(optionIndexes(rows).equals(List.of(0, 1, 2, 3)), "expected the options of the first group only, got " + optionIndexes(rows));

        ConfigViewState shortState = new ConfigViewState();
        rows = ConfigViewLayout.compute(entries(3, 4), shortState, "", 0);
        helper.assertTrue(optionIndexes(rows).size() == 12 && shortState.isExpanded("g0") && shortState.isExpanded("g1") && shortState.isExpanded("g2"),
                "expected all groups of a 12 option list to be expanded, got " + optionIndexes(rows).size() + " options");

        // a new group in a list that was seen before is collapsed
        List<Entry> more = entries(4, 4);
        more.add(entry("late", "Late", "late.option", "Late tooltip"));
        ConfigViewLayout.compute(more, longState, "", 0);
        helper.assertTrue(!longState.isExpanded("late"), "expected a group that shows up later to start collapsed in a long list");
        helper.succeed();
    }

    /**
     * Toggling a header flips just that group, the state of a config type survives (the same object comes back), and
     * different config types do not share it.
     */
    public static void collapseStateIsKeptPerConfigType(GameTestHelper helper) {
        ConfigViewState.clearAll();
        ConfigViewState player = ConfigViewState.forType("PLAYER");
        List<Entry> entries = entries(4, 4);
        ConfigViewLayout.compute(entries, player, "", 0);
        player.toggle("g2");
        helper.assertTrue(player.isExpanded("g0") && !player.isExpanded("g1") && player.isExpanded("g2"), "expected only g2 to flip when toggled");
        helper.assertTrue(optionIndexes(ConfigViewLayout.compute(entries, player, "", 0)).equals(List.of(0, 1, 2, 3, 8, 9, 10, 11)),
                "expected the options of g0 and g2 after expanding g2");
        player.toggle("g0");
        helper.assertTrue(!player.isExpanded("g0"), "expected g0 to collapse when toggled");

        helper.assertTrue(ConfigViewState.forType("PLAYER") == player, "expected the same state object for the same config type");
        ConfigViewState server = ConfigViewState.forType("SERVER");
        helper.assertTrue(server != player, "expected another state object for another config type");
        ConfigViewLayout.compute(entries, server, "", 0);
        helper.assertTrue(server.isExpanded("g0") && !player.isExpanded("g0"), "expected the config types not to share the collapse state");
        ConfigViewState.clearAll();
        helper.succeed();
    }

    /**
     * The search looks at the name, the id and the tooltip, ignores case, wants every word, hides groups without a match,
     * opens the groups with a match whatever their collapse state, and leaves the collapse state alone so that it is back when
     * the search is cleared.
     */
    public static void searchFiltersByNameIdAndTooltipAndOpensMatchingGroups(GameTestHelper helper) {
        List<Entry> entries = new ArrayList<>();
        entries.add(entry("claims", "Claim Name", "claims.name", "The name of your claims"));//0
        entries.add(entry("claims", "Claim Color", "claims.color", "Hex color on the map"));//1
        entries.add(entry("parties", "Party Name", "parties.name", "Shown to your party"));//2
        entries.add(entry("protection", "Blocks By Players", "claims.protection.exceptions.blocksByPlayers", "Who can break blocks"));//3
        entries.add(entry("protection", "Plates By Mobs", "claims.protection.exceptions.platesByMobs", "Pressure plates"));//4
        for (int i = 0; i < 10; i++)
            entries.add(entry("filler", "Filler " + i, "filler.option" + i, "Nothing"));//5 to 14, makes the list long
        ConfigViewState state = new ConfigViewState();

        List<Row> rows = ConfigViewLayout.compute(entries, state, "", 0);
        helper.assertTrue(optionIndexes(rows).equals(List.of(0, 1)) && headerKeys(rows).size() == 4, "expected only the first group open without a search, got " + optionIndexes(rows));

        rows = ConfigViewLayout.compute(entries, state, "NAME", 0);
        helper.assertTrue(optionIndexes(rows).equals(List.of(0, 2)), "expected the name search to find the claim and party names, got " + optionIndexes(rows));
        helper.assertTrue(headerKeys(rows).equals(List.of("claims", "parties")), "expected the groups without a match to be hidden, got " + headerKeys(rows));
        helper.assertTrue(rows.get(0).shown() == 1 && rows.get(0).total() == 2 && rows.get(0).expanded() && rows.get(2).expanded(),
                "expected the headers to count the matches and to be open, even for the collapsed party group");
        helper.assertTrue(!state.isExpanded("parties"), "expected the search not to change the collapse state");

        rows = ConfigViewLayout.compute(entries, state, "protection.exceptions.PLATES", 0);
        helper.assertTrue(optionIndexes(rows).equals(List.of(4)), "expected the id search to find the plates option, got " + optionIndexes(rows));

        rows = ConfigViewLayout.compute(entries, state, "pressure", 0);
        helper.assertTrue(optionIndexes(rows).equals(List.of(4)), "expected the tooltip search to find the plates option, got " + optionIndexes(rows));

        rows = ConfigViewLayout.compute(entries, state, "  claim   color ", 0);
        helper.assertTrue(optionIndexes(rows).equals(List.of(1)), "expected every word to be needed and the spaces to be ignored, got " + optionIndexes(rows));

        rows = ConfigViewLayout.compute(entries, state, "no such option", 0);
        helper.assertTrue(rows.isEmpty(), "expected no rows without a match, got " + rows.size());

        rows = ConfigViewLayout.compute(entries, state, "   ", 0);
        helper.assertTrue(optionIndexes(rows).equals(List.of(0, 1)) && headerKeys(rows).size() == 4, "expected blank search text to bring the collapse state back, got " + optionIndexes(rows));
        helper.succeed();
    }

    /**
     * A list with a single group has no headers, so a short list of one group is just the options.
     */
    public static void aSingleGroupHasNoHeader(GameTestHelper helper) {
        List<Row> rows = ConfigViewLayout.compute(entries(1, 30), new ConfigViewState(), "", 4);
        helper.assertTrue(headerKeys(rows).isEmpty() && optionIndexes(rows).size() == 30 && rows.size() == 30,
                "expected 30 options and nothing else for a single group, got " + rows.size() + " rows");
        helper.succeed();
    }

    /**
     * A header that opens a group is never put in the last row of a column of the screen (6 rows): a filler takes that
     * row. Checked for every number of cells in front of the list and for different group sizes. A collapsed header
     * may be in the last row, nothing follows it.
     */
    public static void anOpenHeaderIsNeverTheLastRowOfAColumn(GameTestHelper helper) {
        for (int leading = 0; leading < 12; leading++) {
            for (int perGroup = 1; perGroup <= 7; perGroup++) {
                for (String query : new String[]{"", "option"}) {
                    List<Row> rows = ConfigViewLayout.compute(entries(3, perGroup), new ConfigViewState(), query, leading);
                    for (int i = 0; i < rows.size(); i++) {
                        Row row = rows.get(i);
                        if (row.kind() == RowKind.HEADER && row.expanded())
                            helper.assertTrue((leading + i) % ConfigViewLayout.ROWS_PER_COLUMN != ConfigViewLayout.ROWS_PER_COLUMN - 1,
                                    "expected an open header not in the last row of a column (leading " + leading + ", " + perGroup + " per group, search '" + query + "'), got it at " + (leading + i));
                        if (row.kind() == RowKind.FILLER)
                            helper.assertTrue(i + 1 < rows.size() && rows.get(i + 1).kind() == RowKind.HEADER && (leading + i) % ConfigViewLayout.ROWS_PER_COLUMN == ConfigViewLayout.ROWS_PER_COLUMN - 1,
                                    "expected a filler only in front of a header in the last row of a column (leading " + leading + ", " + perGroup + " per group)");
                    }
                }
            }
        }
        List<Row> rows = ConfigViewLayout.compute(entries(3, 2), new ConfigViewState(), "", 5);
        helper.assertTrue(rows.get(0).kind() == RowKind.FILLER && rows.get(1).kind() == RowKind.HEADER, "expected a filler and then the first header with 5 cells in front");

        ConfigViewState collapsed = new ConfigViewState();
        ConfigViewLayout.compute(entries(8, 3), collapsed, "", 0);//long list: only g0 open
        rows = ConfigViewLayout.compute(entries(8, 3), collapsed, "", 0);
        helper.assertTrue(rows.get(4).kind() == RowKind.HEADER && !rows.get(4).expanded() && rows.get(5).kind() == RowKind.HEADER && !rows.get(5).expanded(),
                "expected collapsed headers to follow each other without fillers, got " + rows.get(4).kind() + " and " + rows.get(5).kind());
        helper.succeed();
    }

    /**
     * Reset sets nothing on a sub-config (it falls back to the main config), otherwise the server's default player config,
     * else the default of the option; the button is only there for an editable option that is not at that value yet.
     */
    public static void resetTargetAndVisibility(GameTestHelper helper) {
        helper.assertTrue(ConfigResetRule.target(true, 5, 3) == null, "expected a sub-config to reset to nothing");
        helper.assertTrue(ConfigResetRule.target(false, 5, 3) == 5, "expected the server default player config to win");
        helper.assertTrue(ConfigResetRule.target(false, null, 3) == 3, "expected the option default without a server default");

        helper.assertTrue(ConfigResetRule.isVisible(true, 4, 5), "expected the button for an editable option away from the default");
        helper.assertTrue(!ConfigResetRule.isVisible(true, 5, 5), "expected no button at the default");
        helper.assertTrue(!ConfigResetRule.isVisible(false, 4, 5), "expected no button when the player may not edit the option");
        helper.assertTrue(ConfigResetRule.isVisible(true, 7, null), "expected the button for a sub-config option that has its own value");
        helper.assertTrue(!ConfigResetRule.isVisible(true, null, null), "expected no button for a sub-config option that uses the main config");
        helper.assertTrue(!ConfigResetRule.isVisible(true, "a", "a") && ConfigResetRule.isVisible(true, "a", "b"), "expected equal strings to count as the default");
        helper.succeed();
    }

}
