package com.my;

import java.util.regex.Pattern;

public class LstuUtils {
    public static final Pattern groupNamePattern =
            Pattern.compile("^((т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,5}-)(п-)?\\d{2}(-\\d)?$");
}
