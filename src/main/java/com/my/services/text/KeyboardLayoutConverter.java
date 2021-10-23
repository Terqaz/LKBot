package com.my.services.text;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class KeyboardLayoutConverter {
    private KeyboardLayoutConverter () {}

    private static final Map<Character, Character> enToRuCharsMap = new HashMap<>();
    static {
        final String enChars = "qwertyuiop[]asdfghjkl;'zxcvbnm,./QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?";
        final String ruChars = "йцукенгшщзхъфывапролджэячсмитьбю.ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ.";
        for (int i = 0; i < enChars.length(); i++) {
            enToRuCharsMap.put(enChars.charAt(i), ruChars.charAt(i));
        }
    }

    public static String convertFromEngIfNeeds(String s1) {
        if (!isEnCharsString(s1))
            return s1;

        StringBuilder s2 = new StringBuilder();
        for (int i = 0; i < s1.length(); i++) {
            final char s1char = s1.charAt(i);
            s2.append(enToRuCharsMap.getOrDefault(s1char, s1char));
        }
        return s2.toString();
    }

    private static boolean isEnCharsString(String s) {
        return StandardCharsets.US_ASCII.newEncoder().canEncode(s);
    }
}
