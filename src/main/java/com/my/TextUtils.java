package com.my;

import com.ibm.icu.text.Transliterator;

import javax.validation.constraints.NotEmpty;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public final class TextUtils {

    private static final Transliterator toLatinTransliterator = Transliterator.getInstance("Russian-Latin/BGN");

    private TextUtils() {}

    public static String makeShortSenderName (String name) {
        final String[] chunks = name.split(" ");
        return chunks[0] + " " + chunks[1].charAt(0) + chunks[2].charAt(0);
    }

    public static String capitalize (@NotEmpty String s) {
        if (s.isBlank())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    public static String changeEncodingIso_8859_1_Windows_1251(String s) {
        return new String(s.getBytes(ISO_8859_1), Charset.forName("Windows-1251"));
    }

    public static String toUnixCompatibleName(String s) {
        final char[] chars = toLatinTransliterator.transliterate(s).replace("ʹ", "").toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!(Character.isLetterOrDigit(chars[i]) || Set.of('.', '-').contains(chars[i]))) {
                chars[i] = '_';
            }
        }

        return trimUnderscore(chars);
    }

    private static String trimUnderscore(char[] chars) {
        int s = 0;
        while (chars[s] == '_') ++s;

        int e = chars.length-1;
        while (chars[e] == '_') --e;

        return new String(chars, s, e-s+1);
    }

    public static boolean isUnacceptableFileExtension(String strFilePath) {
        strFilePath = strFilePath.toLowerCase(Locale.ROOT);
        return Stream.of(".mp3", ".exe", ".dll", ".zip", ".rar", ".tar", ".7z",
                ".wim", ".bz2", ".gz", ".xz", ".com", ".flac", ".alac")
            .anyMatch(strFilePath::endsWith);
    }
}
