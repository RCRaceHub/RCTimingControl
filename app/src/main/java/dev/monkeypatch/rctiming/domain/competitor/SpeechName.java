package dev.monkeypatch.rctiming.domain.competitor;

import java.util.Locale;

/**
 * What the announcer says for a competitor (#119, #120): the one place that decides the speech text,
 * so the Piper clips, the running order and the browser's fallback voice all say the same thing.
 * <p>
 * A spoken name an admin typed is used exactly as typed. Otherwise the display name is tidied for speech
 * ({@link #tidy}). Neither changes what is displayed or exported.
 */
public final class SpeechName {

    private SpeechName() {
    }

    /** The spoken name when one is set, else the tidied display name. Never blank unless the name is. */
    public static String of(String spokenName, String displayName) {
        if (spokenName != null && !spokenName.isBlank()) {
            return spokenName.trim();
        }
        return displayName == null ? "" : tidy(displayName);
    }

    /**
     * Tidies a typed name for the speech engine, deterministically:
     * <ul>
     *   <li>text in brackets, such as a nickname or club tag, is left out: "Alex Rowe (Wyvern)" is "Alex Rowe";</li>
     *   <li>characters that are not spoken well (emoji, symbols, stray punctuation) become spaces; letters,
     *       digits, apostrophes, hyphens and full stops stay;</li>
     *   <li>repeated spaces collapse, the ends are trimmed, and a stray "-" or "." standing alone goes;</li>
     *   <li>names in ALL CAPITALS, which some engines spell out letter by letter, are spoken in normal
     *       capitalisation, and so is a capitalised word of three letters or more inside a mixed-case name
     *       ("Alex ROWE"); short ones such as the initials "AJ" or "DJ" are left alone, unless the whole
     *       name is in capitals.</li>
     * </ul>
     * When nothing speakable is left, the name as given (trimmed) is returned, never an empty text.
     */
    public static String tidy(String name) {
        if (name == null) {
            return "";
        }
        String original = name.trim();
        String withoutBrackets = original.replaceAll("[(\\[{][^)\\]}]*[)\\]}]", " ");
        String spoken = withoutBrackets.codePoints()
                .map(cp -> isSpace(cp) || !isSpoken(cp) ? ' ' : cp)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .replaceAll(" +", " ")
                .trim();
        if (spoken.isEmpty() || spoken.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return original;
        }
        boolean allCapitals = spoken.codePoints().noneMatch(Character::isLowerCase)
                && spoken.codePoints().anyMatch(Character::isUpperCase);
        StringBuilder out = new StringBuilder();
        for (String word : spoken.split(" ")) {
            if (word.codePoints().noneMatch(Character::isLetterOrDigit)) {
                continue; // a stray "-" or "." on its own
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(shouldTitleCase(word, allCapitals) ? titleCase(word) : word);
        }
        return out.toString();
    }

    private static boolean isSpoken(int cp) {
        return Character.isLetterOrDigit(cp)
                || Character.getType(cp) == Character.NON_SPACING_MARK
                || Character.getType(cp) == Character.COMBINING_SPACING_MARK
                || cp == '\'' || cp == '\u2019' || cp == '-' || cp == '.';
    }

    /** Any kind of space, such as a tab, a non-breaking space or an em space, is spoken as one plain space. */
    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static boolean shouldTitleCase(String word, boolean allCapitals) {
        long letters = word.codePoints().filter(Character::isLetter).count();
        boolean noLowercase = word.codePoints().noneMatch(Character::isLowerCase);
        if (letters == 0 || !noLowercase) {
            return false;
        }
        return allCapitals || letters >= 3;
    }

    /** "O'BRIEN" is "O'Brien" and "SMITH-JONES" is "Smith-Jones". */
    private static String titleCase(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean startOfPart = true;
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
            out.appendCodePoint(startOfPart ? Character.toTitleCase(cp) : cp);
            startOfPart = cp == '-' || cp == '\'' || cp == '\u2019';
        }
        return out.toString();
    }

    /**
     * Just the first name of a speech name, for a call that names each driver briefly (#482): everything up to the
     * first space, so "Alex Rowe" is "Alex". A name of one word, or a blank one, comes back as it is. A spoken
     * name an admin typed is cut the same way, so one meant to be said whole should keep to a single word.
     */
    public static String firstName(String speechName) {
        if (speechName == null) {
            return "";
        }
        String trimmed = speechName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
