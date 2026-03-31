package io.datapulse.tenancy.domain;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final int MAX_BASE_LENGTH = 70;
    private static final int MAX_TOTAL_LENGTH = 80;
    private static final int SUFFIX_LENGTH = 4;
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9-]");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final Map<Character, String> CYRILLIC_MAP = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "yo"), Map.entry('ж', "zh"),
            Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "shch"), Map.entry('ъ', ""),
            Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"), Map.entry('ю', "yu"),
            Map.entry('я', "ya")
    );

    private SlugUtils() {
    }

    public static String generateSlug(String name) {
        String input = name.trim().toLowerCase();
        String transliterated = transliterate(input);
        String normalized = Normalizer.normalize(transliterated, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = NON_SLUG_CHARS.matcher(normalized).replaceAll("-");
        slug = MULTIPLE_DASHES.matcher(slug).replaceAll("-");
        slug = slug.replaceAll("^-|-$", "");

        if (slug.length() > MAX_BASE_LENGTH) {
            slug = slug.substring(0, MAX_BASE_LENGTH);
            slug = slug.replaceAll("-$", "");
        }

        if (slug.isEmpty()) {
            slug = "workspace";
        }

        return slug;
    }

    public static String appendSuffix(String baseSlug) {
        String suffix = randomAlphanumeric(SUFFIX_LENGTH);
        String result = baseSlug + "-" + suffix;
        if (result.length() > MAX_TOTAL_LENGTH) {
            int trim = result.length() - MAX_TOTAL_LENGTH;
            result = baseSlug.substring(0, baseSlug.length() - trim) + "-" + suffix;
        }
        return result;
    }

    private static String transliterate(String input) {
        var sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            String mapped = CYRILLIC_MAP.get(c);
            if (mapped != null) {
                sb.append(mapped);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String randomAlphanumeric(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
