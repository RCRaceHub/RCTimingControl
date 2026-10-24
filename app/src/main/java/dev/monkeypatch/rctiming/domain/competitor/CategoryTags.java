package dev.monkeypatch.rctiming.domain.competitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The category tags (Junior, Vet, F-Grade and so on) a competitor holds and a championship splits by (#494), kept as
 * one comma separated text column. Tags are the club's own words, so there is no fixed list; two tags that differ
 * only in case or spacing are the same tag.
 */
public final class CategoryTags {

    /** The most tags one competitor or championship can hold. */
    public static final int MAX_TAGS = 8;

    /** The longest a tag can be, in characters. */
    public static final int MAX_LENGTH = 30;

    private CategoryTags() {}

    /** The tags in a stored column value, in stored order; empty for null or blank. */
    public static List<String> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String part : stored.split(",")) {
            String tag = clean(part);
            if (!tag.isEmpty() && tags.stream().noneMatch(t -> same(t, tag))) {
                tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }

    /**
     * Tidies tags typed by an official: trims, collapses spaces, drops blanks and repeats (keeping the first
     * spelling) and returns them in order.
     *
     * @throws IllegalArgumentException if there are more than {@link #MAX_TAGS}, a tag is longer than {@link
     *         #MAX_LENGTH} or a tag contains a comma (HTTP 400)
     */
    public static List<String> normalise(List<String> typed) {
        List<String> tags = new ArrayList<>();
        if (typed != null) {
            for (String raw : typed) {
                if (raw != null && raw.contains(",")) {
                    throw new IllegalArgumentException("A category cannot contain a comma");
                }
                String tag = clean(raw);
                if (tag.length() > MAX_LENGTH) {
                    throw new IllegalArgumentException("A category can be at most " + MAX_LENGTH + " characters");
                }
                if (!tag.isEmpty() && tags.stream().noneMatch(t -> same(t, tag))) {
                    tags.add(tag);
                }
            }
        }
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("At most " + MAX_TAGS + " categories");
        }
        return List.copyOf(tags);
    }

    /** The column value for these tags: null when there are none. */
    public static String format(List<String> tags) {
        return tags == null || tags.isEmpty() ? null : String.join(",", tags);
    }

    /** True when the two tags are the same category (ignoring case). */
    public static boolean same(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }
}
