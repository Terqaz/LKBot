package com.my;

import javax.validation.constraints.NotEmpty;

public final class ParserUtils {

    private ParserUtils () {}

    public static String makeShortSenderName (String name) {
        final String[] chunks = name.split(" ");
        return chunks[0] + " " + chunks[1].charAt(0) + chunks[2].charAt(0);
    }

    public static String capitalize (@NotEmpty String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
