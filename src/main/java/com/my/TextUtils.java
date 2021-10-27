package com.my;

import javax.validation.constraints.NotEmpty;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public final class TextUtils {



    private TextUtils() {}

    public static String makeShortSenderName (String name) {
        final String[] chunks = name.split(" ");
        return chunks[0] + " " + chunks[1].charAt(0) + chunks[2].charAt(0);
    }

    public static String capitalize (@NotEmpty String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    public static String changeEncodingIso_8859_1_Windows_1251(String s) {
        return new String(s.getBytes(ISO_8859_1), Charset.forName("Windows-1251"));
    }


}
