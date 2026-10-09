package xaero.pac.teamclaims.ftbsync;

import xaero.pac.teamclaims.TeamNames;

/**
 * Turns a party name of one mod into a name the other mod accepts. Both directions are pure functions of the
 * name (and, towards OPAC, of the configured maximum team name length), so the same input always gives the same
 * result. The sync stores the name each side really has after a rename and only acts when one of them changes,
 * which is why a sanitised name that differs from the original is never written back.
 */
public final class FtbSyncNames {

    /** FTB Teams' {@code display_name} property only accepts names of at least this many characters. */
    public static final int FTB_MIN_LENGTH = 3;
    /** The length limit of OPAC's {@code PARTY_NAME} option. */
    public static final int OPAC_MAX_LENGTH = 100;
    private static final char FILLER = '_';

    private FtbSyncNames() {}

    /**
     * An OPAC party name as an FTB Teams display name: line breaks become spaces (the property pattern does not
     * match them), and a name shorter than {@link #FTB_MIN_LENGTH} is padded with underscores.
     */
    public static String toFtbName(String opacName) {
        StringBuilder name = new StringBuilder(opacName.replace('\n', ' ').replace('\r', ' ').trim());
        while (name.length() < FTB_MIN_LENGTH) name.append(FILLER);
        return name.toString();
    }

    /**
     * An FTB Teams display name as an OPAC party name, by the rules of {@code /teamclaims create <name>}: formatting
     * codes are removed and whitespace is collapsed ({@link TeamNames#sanitize}), every character OPAC's
     * {@code PARTY_NAME} option does not allow becomes an underscore, and the result is cut to the shorter of the
     * configured maximum team name length and OPAC's own limit. The result may be empty (a name of nothing but
     * formatting codes or whitespace), which stands for the party's default name.
     */
    public static String toOpacName(String ftbName) {
        return toOpacName(ftbName, Math.min(TeamNames.getMaxLength(), OPAC_MAX_LENGTH));
    }

    static String toOpacName(String ftbName, int maxLength) {
        String cleaned = TeamNames.sanitize(ftbName);
        StringBuilder name = new StringBuilder(cleaned.length());
        for (int i = 0; i < cleaned.length(); ) {
            int codePoint = cleaned.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isAllowedInOpacName(codePoint)) name.appendCodePoint(codePoint);
            else name.append(FILLER);
        }
        if (name.length() > maxLength) {
            int end = maxLength;
            if (Character.isHighSurrogate(name.charAt(end - 1))) end--;//never half of a surrogate pair
            name.setLength(end);
        }
        return name.toString().trim();
    }

    /** The characters of OPAC's {@code PARTY_NAME} validator: {@code ^(\p{L}|[0-9 _'"!?,\-&%*\(\):])*$}. */
    private static boolean isAllowedInOpacName(int codePoint) {
        if (Character.isLetter(codePoint)) return true;
        if (codePoint >= '0' && codePoint <= '9') return true;
        return " _'\"!?,-&%*():".indexOf(codePoint) >= 0;
    }
}
