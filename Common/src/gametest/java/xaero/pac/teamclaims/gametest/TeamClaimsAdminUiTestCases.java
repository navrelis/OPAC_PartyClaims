package xaero.pac.teamclaims.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.client.gui.hub.AdminConfigView;
import xaero.pac.client.gui.hub.AdminValueRules;
import xaero.pac.client.gui.hub.AdminValueRules.Status;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry.ValueType;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;

import java.util.List;

/**
 * Dev/test-only, loader-neutral test bodies for the pure rules behind the Admin tab of the hub screen
 * ({@link AdminValueRules}, {@link AdminConfigView}). None of them touches a client class, so they run on the headless
 * gametest server. Same conventions as {@link TeamClaimsHubTestCases}: every test is registered with the loader's empty
 * structure template and ends with {@code helper.succeed()}.
 */
public final class TeamClaimsAdminUiTestCases {

    private TeamClaimsAdminUiTestCases() {}

    private static AdminConfigEntry entry(String configId, String path, ValueType type, String value, String min, String max,
                                          List<String> allowed, String comment, boolean restart, String pending) {
        return new AdminConfigEntry(configId, path, type, value, value, min, max, allowed, comment, restart, pending);
    }

    private static AdminConfigEntry number(ValueType type, String value, String min, String max) {
        return entry("opac", "serverConfig.claims.number", type, value, min, max, List.of(), "", false, null);
    }

    // ==================== Tests ====================

    /**
     * Whole numbers must be whole numbers of the option's type and inside its range (both ends included), decimals take
     * the usual forms but no words, booleans and enum constants are matched without regard to the case, a range end that
     * is no number ("Infinity") is no limit, and any text is a string.
     */
    public static void valueRulesCheckTypeAndRange(GameTestHelper helper) {
        AdminConfigEntry intEntry = number(ValueType.INT, "5", "0", "100");
        helper.assertTrue(AdminValueRules.check(intEntry, "0") == Status.OK && AdminValueRules.check(intEntry, "100") == Status.OK
                && AdminValueRules.check(intEntry, " 42 ") == Status.OK, "expected 0, 100 and ' 42 ' to be valid for the range 0..100");
        helper.assertTrue(AdminValueRules.check(intEntry, "101") == Status.OUT_OF_RANGE && AdminValueRules.check(intEntry, "-1") == Status.OUT_OF_RANGE,
                "expected 101 and -1 to be out of the range 0..100");
        helper.assertTrue(AdminValueRules.check(intEntry, "5.5") == Status.NOT_A_NUMBER && AdminValueRules.check(intEntry, "abc") == Status.NOT_A_NUMBER
                && AdminValueRules.check(intEntry, "99999999999") == Status.NOT_A_NUMBER, "expected a decimal, a word and a number beyond int to be no int");
        helper.assertTrue(AdminValueRules.check(intEntry, "") == Status.EMPTY && AdminValueRules.check(intEntry, "  ") == Status.EMPTY,
                "expected an empty box to be EMPTY");

        AdminConfigEntry longEntry = number(ValueType.LONG, "5", null, null);
        helper.assertTrue(AdminValueRules.check(longEntry, "99999999999") == Status.OK && AdminValueRules.check(longEntry, "9223372036854775808") == Status.NOT_A_NUMBER,
                "expected a long to take 99999999999 and refuse 2^63");

        AdminConfigEntry doubleEntry = number(ValueType.DOUBLE, "0.5", "0.0", "1.0");
        for (String good : new String[]{"0", "1", ".5", "0.25", "5e-1", "+0.75", "1.0"})
            helper.assertTrue(AdminValueRules.check(doubleEntry, good) == Status.OK, "expected '" + good + "' to be valid for the range 0.0..1.0");
        for (String bad : new String[]{"NaN", "Infinity", "1d", "0x1", "1,5", "1e999"})
            helper.assertTrue(AdminValueRules.check(doubleEntry, bad) == Status.NOT_A_NUMBER, "expected '" + bad + "' to be no decimal number");
        helper.assertTrue(AdminValueRules.check(doubleEntry, "1.0001") == Status.OUT_OF_RANGE && AdminValueRules.check(doubleEntry, "-0.1") == Status.OUT_OF_RANGE,
                "expected 1.0001 and -0.1 to be out of the range 0.0..1.0");
        AdminConfigEntry unbounded = entry("opac", "x.y", ValueType.DOUBLE, "1.0", "-Infinity", "Infinity", List.of(), "", false, null);
        helper.assertTrue(AdminValueRules.check(unbounded, "123456789.5") == Status.OK, "expected an infinite range end to be no limit");

        AdminConfigEntry bool = entry("opac", "x.flag", ValueType.BOOLEAN, "true", null, null, List.of(), "", false, null);
        helper.assertTrue(AdminValueRules.check(bool, "TRUE") == Status.OK && AdminValueRules.check(bool, "false") == Status.OK
                && AdminValueRules.check(bool, "yes") == Status.NOT_ALLOWED, "expected only true and false for a boolean");

        AdminConfigEntry enumEntry = entry("opac", "x.mode", ValueType.ENUM, "ALL", null, null, List.of("ALL", "ONLY", "ALL_BUT"), "", false, null);
        helper.assertTrue(AdminValueRules.check(enumEntry, "only") == Status.OK && AdminValueRules.check(enumEntry, "NONE") == Status.NOT_ALLOWED,
                "expected an enum to take its constants in any case and nothing else");
        helper.assertTrue("ALL_BUT".equals(AdminValueRules.normalise(enumEntry, " all_but ")), "expected the enum to be sent in the spelling of the constant, got "
                + AdminValueRules.normalise(enumEntry, " all_but "));
        helper.assertTrue("true".equals(AdminValueRules.normalise(bool, " TRUE ")), "expected the boolean to be sent as true");

        AdminConfigEntry string = entry("opac", "x.text", ValueType.STRING, "abc", null, null, List.of(), "", false, null);
        helper.assertTrue(AdminValueRules.check(string, "") == Status.OK && AdminValueRules.check(string, " any ä text ") == Status.OK, "expected any text to be a valid string");
        helper.assertTrue(" any text ".equals(AdminValueRules.normalise(string, " any text ")), "expected a string to be sent as it is");
        helper.assertTrue(AdminValueRules.check(string, "a".repeat(AdminValueRules.MAX_TEXT_LENGTH + 1)) == Status.TOO_LONG
                && AdminValueRules.check(string, "a".repeat(AdminValueRules.MAX_TEXT_LENGTH)) == Status.OK, "expected the length limit of the server to apply");
        helper.succeed();
    }

    /**
     * The Save button is enabled for a valid text that is not the saved value: 5 and 5.0 are the same number, a restart-only
     * option compares with its pending (saved) value rather than the running one so that it can be set back, and an
     * invalid text never saves.
     */
    public static void saveIsOnlyEnabledForAValidChangedValue(GameTestHelper helper) {
        AdminConfigEntry plain = number(ValueType.DOUBLE, "5.0", "0", "10");
        helper.assertTrue(!AdminValueRules.canSave(plain, "5") && !AdminValueRules.canSave(plain, "5.00") && !AdminValueRules.canSave(plain, " 5.0 "),
                "expected 5, 5.00 and ' 5.0 ' not to be changes of 5.0");
        helper.assertTrue(AdminValueRules.canSave(plain, "6") && !AdminValueRules.canSave(plain, "11") && !AdminValueRules.canSave(plain, "x") && !AdminValueRules.canSave(plain, ""),
                "expected 6 to be savable and 11, x and an empty box not");

        AdminConfigEntry restart = entry("opac", "serverConfig.claims.max", ValueType.INT, "10", "0", "100", List.of(), "", true, "20");
        helper.assertTrue("20".equals(AdminValueRules.getSavedValue(restart)), "expected the saved value of a pending option to be the pending one");
        helper.assertTrue(!AdminValueRules.canSave(restart, "20") && AdminValueRules.canSave(restart, "10") && AdminValueRules.canSave(restart, "30"),
                "expected 20 (saved already) not to be savable, and 10 (the running value) and 30 to be");
        AdminConfigEntry restartNoPending = entry("opac", "serverConfig.claims.max", ValueType.INT, "10", "0", "100", List.of(), "", true, null);
        helper.assertTrue("10".equals(AdminValueRules.getSavedValue(restartNoPending)) && !AdminValueRules.canSave(restartNoPending, "10"),
                "expected the running value to be the saved one without a pending value");

        AdminConfigEntry string = entry("opac", "x.text", ValueType.STRING, "abc", null, null, List.of(), "", false, null);
        helper.assertTrue(!AdminValueRules.canSave(string, "abc") && AdminValueRules.canSave(string, "abc ") && AdminValueRules.canSave(string, ""),
                "expected a string to compare exactly (a trailing space is a change, an empty text too)");
        helper.succeed();
    }

    /**
     * A boolean button sends the opposite of the saved value (the pending one for a restart-only option), an enum button
     * the next constant and wraps around, going backwards too, starts at an end when the saved value is not listed, and the
     * other types have no button value.
     */
    public static void toggleAndCycleSendTheNextValue(GameTestHelper helper) {
        AdminConfigEntry bool = entry("opac", "x.flag", ValueType.BOOLEAN, "true", null, null, List.of(), "", false, null);
        helper.assertTrue("false".equals(AdminValueRules.getNextValue(bool, false)), "expected a true boolean to send false");
        AdminConfigEntry boolPending = entry("opac", "x.flag", ValueType.BOOLEAN, "true", null, null, List.of(), "", true, "false");
        helper.assertTrue("true".equals(AdminValueRules.getNextValue(boolPending, false)), "expected a boolean with the pending value false to send true");

        List<String> modes = List.of("ALL", "ONLY", "ALL_BUT");
        AdminConfigEntry mode = entry("opac", "x.mode", ValueType.ENUM, "ONLY", null, null, modes, "", false, null);
        helper.assertTrue("ALL_BUT".equals(AdminValueRules.getNextValue(mode, false)) && "ALL".equals(AdminValueRules.getNextValue(mode, true)),
                "expected ONLY to go to ALL_BUT forwards and to ALL backwards");
        AdminConfigEntry last = entry("opac", "x.mode", ValueType.ENUM, "ALL_BUT", null, null, modes, "", false, null);
        helper.assertTrue("ALL".equals(AdminValueRules.getNextValue(last, false)), "expected the last constant to wrap around to the first");
        AdminConfigEntry first = entry("opac", "x.mode", ValueType.ENUM, "ALL", null, null, modes, "", false, null);
        helper.assertTrue("ALL_BUT".equals(AdminValueRules.getNextValue(first, true)), "expected the first constant to wrap around to the last backwards");
        AdminConfigEntry unknown = entry("opac", "x.mode", ValueType.ENUM, "OTHER", null, null, modes, "", false, null);
        helper.assertTrue("ALL".equals(AdminValueRules.getNextValue(unknown, false)) && "ALL_BUT".equals(AdminValueRules.getNextValue(unknown, true)),
                "expected an unlisted value to start at an end");
        AdminConfigEntry empty = entry("opac", "x.mode", ValueType.ENUM, "ALL", null, null, List.of(), "", false, null);
        helper.assertTrue(AdminValueRules.getNextValue(empty, false) == null && AdminValueRules.getNextValue(number(ValueType.INT, "1", null, null), false) == null,
                "expected no button value for an enum without constants and for a number");
        helper.succeed();
    }

    /**
     * The option list: options of one section stay together under one header named by the section without the root that
     * all options share, options directly under the root are "General" (empty label), the order of the server is kept, the
     * filter looks at the path, the readable name and the comment with every word having to match, and a header whose
     * options are all filtered out disappears.
     */
    public static void configRowsGroupAndFilter(GameTestHelper helper) {
        List<AdminConfigEntry> entries = List.of(
                entry("opac", "serverConfig.defaultLanguage", ValueType.STRING, "en_us", null, null, List.of(), "The language of the server messages", true, null),
                entry("opac", "serverConfig.parties.enabled", ValueType.BOOLEAN, "true", null, null, List.of(), "Turns parties on", true, null),
                entry("opac", "serverConfig.parties.maxPartyMembers", ValueType.INT, "10", "1", "100", List.of(), "How many players a party can have", true, null),
                entry("opac", "serverConfig.claims.maxPlayerClaims", ValueType.INT, "500", "0", "100000", List.of(), "Claims per player", true, null),
                entry("opac", "serverConfig.claims.enabled", ValueType.BOOLEAN, "true", null, null, List.of(), "Turns claims on", true, null));

        List<AdminConfigView.Row> all = AdminConfigView.buildRows(entries, "");
        helper.assertTrue(all.size() == 8 && AdminConfigView.countEntries(all) == 5, "expected 3 headers and 5 options, got " + all.size() + " rows");
        helper.assertTrue(all.get(0).isHeader() && "".equals(all.get(0).header()) && "defaultLanguage".equals(AdminConfigView.getLastSegment(all.get(1).entry().path())),
                "expected the option directly under the root to come first under the General (empty) header");
        helper.assertTrue(all.get(2).isHeader() && "parties".equals(all.get(2).header()) && all.get(5).isHeader() && "claims".equals(all.get(5).header()),
                "expected the parties and claims headers without the serverConfig root, in the order of the server");

        List<AdminConfigView.Row> enabled = AdminConfigView.buildRows(entries, "enabled");
        helper.assertTrue(AdminConfigView.countEntries(enabled) == 2 && enabled.size() == 4, "expected the filter 'enabled' to keep the two enabled options under two headers, got " + enabled.size());
        List<AdminConfigView.Row> byComment = AdminConfigView.buildRows(entries, "how many");
        helper.assertTrue(AdminConfigView.countEntries(byComment) == 1 && byComment.get(1).entry().path().endsWith("maxPartyMembers"), "expected the filter to find an option by its comment");
        List<AdminConfigView.Row> byName = AdminConfigView.buildRows(entries, "MAX claims");
        helper.assertTrue(AdminConfigView.countEntries(byName) == 1 && byName.get(1).entry().path().endsWith("maxPlayerClaims"), "expected two words to both have to match, in any case");
        helper.assertTrue(AdminConfigView.buildRows(entries, "zzz").isEmpty(), "expected a filter that matches nothing to leave no rows, not even headers");
        helper.assertTrue(AdminConfigView.buildRows(entries, "  ").size() == 8, "expected a blank filter to keep everything");

        List<AdminConfigEntry> flat = List.of(entry("teamclaims", "teamClaims.enabled", ValueType.BOOLEAN, "true", null, null, List.of(), "", true, null),
                entry("teamclaims", "teamClaims.limits.base", ValueType.INT, "10", "0", "1000", List.of(), "", false, null));
        List<AdminConfigView.Row> teamRows = AdminConfigView.buildRows(flat, "");
        helper.assertTrue(teamRows.size() == 4 && "".equals(teamRows.get(0).header()) && "limits".equals(teamRows.get(2).header()), "expected the Team Claims options to group under the root and 'limits'");
        helper.assertTrue("serverConfig.claims".equals(AdminConfigView.getGroup("serverConfig.claims.enabled")) && "".equals(AdminConfigView.getGroup("root")), "expected the group to be the path without its last segment");
        helper.succeed();
    }

    /**
     * Option names are shown as words, a pending value is only shown for a restart-only option whose saved value differs
     * from the running one, and the range text leaves a missing end empty.
     */
    public static void displayTextsFollowTheEntry(GameTestHelper helper) {
        helper.assertTrue("Max player claims".equals(AdminConfigView.humanise("maxPlayerClaims")), "got " + AdminConfigView.humanise("maxPlayerClaims"));
        helper.assertTrue("Enabled".equals(AdminConfigView.humanise("enabled")), "got " + AdminConfigView.humanise("enabled"));
        helper.assertTrue("Ftb teams sync".equals(AdminConfigView.humanise("ftbTeamsSync")), "got " + AdminConfigView.humanise("ftbTeamsSync"));
        helper.assertTrue("TNT explosion damage".equals(AdminConfigView.humanise("TNTExplosionDamage")), "got " + AdminConfigView.humanise("TNTExplosionDamage"));
        helper.assertTrue("Claim2 limit".equals(AdminConfigView.humanise("claim2Limit")), "got " + AdminConfigView.humanise("claim2Limit"));

        AdminConfigEntry pending = entry("opac", "a.b", ValueType.INT, "10", "0", "100", List.of(), "", true, "20");
        AdminConfigEntry same = entry("opac", "a.b", ValueType.INT, "10", "0", "100", List.of(), "", true, "10");
        AdminConfigEntry none = entry("opac", "a.b", ValueType.INT, "10", "0", "100", List.of(), "", true, null);
        AdminConfigEntry live = entry("opac", "a.b", ValueType.INT, "10", "0", "100", List.of(), "", false, "20");
        helper.assertTrue(AdminConfigView.hasPending(pending) && !AdminConfigView.hasPending(same) && !AdminConfigView.hasPending(none) && !AdminConfigView.hasPending(live),
                "expected a pending value only for a restart-only option whose saved value differs");
        helper.assertTrue("0 .. 100".equals(AdminConfigView.getRangeText(pending)), "got " + AdminConfigView.getRangeText(pending));
        helper.assertTrue("5 .. ".equals(AdminConfigView.getRangeText(entry("opac", "a.b", ValueType.INT, "10", "5", null, List.of(), "", false, null)))
                && AdminConfigView.getRangeText(entry("opac", "a.b", ValueType.STRING, "x", null, null, List.of(), "", false, null)) == null, "expected a missing range end to stay empty and no range to be null");
        helper.assertTrue("abcdefg".equals(AdminConfigView.shorten("abcdefg", 7)) && "abcd...".equals(AdminConfigView.shorten("abcdefghi", 7)), "expected shorten to cut with three dots");
        helper.succeed();
    }

    /**
     * A player's limit is the base plus the bonus, unlimited stays unlimited, a sum that does not fit is unlimited rather
     * than negative, and going over the limit is only possible with a limit. The bonus boxes take whole numbers within the
     * range the server accepts (players -1000000..1000000, teams 0..1000000).
     */
    public static void playerTotalsAndBonusBoxesFollowTheServerRules(GameTestHelper helper) {
        helper.assertTrue(AdminConfigView.getTotal(100, 25) == 125 && AdminConfigView.getTotal(100, -30) == 70 && AdminConfigView.getTotal(0, 0) == 0,
                "expected base plus bonus");
        helper.assertTrue(AdminConfigView.getTotal(Integer.MAX_VALUE, -5) == Integer.MAX_VALUE && AdminConfigView.getTotal(Integer.MAX_VALUE - 1, 10) == Integer.MAX_VALUE,
                "expected unlimited to stay unlimited and an overflowing sum to be unlimited");
        helper.assertTrue(AdminConfigView.isOverLimit(11, 10) && !AdminConfigView.isOverLimit(10, 10) && !AdminConfigView.isOverLimit(1000000, Integer.MAX_VALUE) && !AdminConfigView.isOverLimit(-1, 0),
                "expected only a count above a real limit to be over it (-1 is no count)");

        int max = AdminPlayerBonus.MAX_ABS_VALUE;
        helper.assertTrue(AdminValueRules.checkInt("-1000000", -max, max) == Status.OK && AdminValueRules.checkInt("1000000", -max, max) == Status.OK
                && AdminValueRules.checkInt("1000001", -max, max) == Status.OUT_OF_RANGE && AdminValueRules.checkInt("-1000001", -max, max) == Status.OUT_OF_RANGE
                && AdminValueRules.checkInt("1.5", -max, max) == Status.NOT_A_NUMBER && AdminValueRules.checkInt("", -max, max) == Status.EMPTY,
                "expected the player bonus box to take -1000000..1000000 whole numbers");
        helper.assertTrue(AdminValueRules.checkInt("0", 0, max) == Status.OK && AdminValueRules.checkInt("-1", 0, max) == Status.OUT_OF_RANGE,
                "expected the team bonus box not to take negative numbers");
        helper.assertTrue(AdminValueRules.parseInt(" 42 ") == 42 && AdminValueRules.parseInt("-7") == -7, "expected parseInt to read what checkInt accepted");
        helper.succeed();
    }
}
