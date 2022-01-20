package com.my.services;

import com.my.TextUtils;
import com.my.services.text.KeyboardLayoutConverter;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Command {

    public static final String CHANGE_GROUP = "Измени группу";
    public static final String WANT_TO_LOGIN = "Хочу войти в ЛК";
    public static final String GET_SUBJECTS = "Предметы";
    public static final String COMMANDS = "Команды";
    public static final String WITH_EVERYDAY_SCHEDULE = "Присылай расписание";
    public static final String WITHOUT_EVERYDAY_SCHEDULE = "Не присылай расписание";
    public static final String FORGET_ME = "Забудь меня";
    public static final String FINALLY_FORGET_ME = "Да, точно забудь меня";

    private static final Pattern GROUP_NAME_PATTERN = wrap1("^((Т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,4}-)(П-)?\\d{2}(-\\d)?$");
    public static final Pattern WANT_TO_LOGIN_PATTERN = wrap1("^Хочу войти в ЛК$");
    public static final Pattern CREDENTIALS = wrap2("^\\S+\\n+\\S+$");
    public static final Pattern GET_SUBJECT_DOCUMENTS = wrap1("^Документы \\d{1,2}$");
    public static final Pattern GET_SUBJECT_DOCUMENT = wrap1("^\\d{1,2} \\d{1,3}$");
    public static final Pattern CHANGE_SILENT_TIME = wrap1("^Новый тихий режим: с \\d+ [пд]о \\d+$");
    public static final Pattern VERIFICATION_CODE = wrap2("^\\d{6}$");

    // Команды админа
    public static final String WHICH_WEEK_TYPE = "тип недели";
    public static final String APPLICATION_STOP = "I WANT TO STOP THE APPLICATION";
    public static final Pattern WEEK_TYPE = wrap1("^(белая|зеленая)$");

    private static Pattern wrap1(String command) {
        return Pattern.compile(command, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
    }

    private static Pattern wrap2 (String command) {
        return Pattern.compile(command, Pattern.UNICODE_CASE);
    }

    @Getter
    private final String value;
    private final String convertedValue;
    private final String capitalizedConvertedValue;

    public Command(String value) {
        this.value = value;
        this.convertedValue = KeyboardLayoutConverter.convertFromEngIfNeeds(value);
        this.capitalizedConvertedValue = TextUtils.capitalize(convertedValue);
    }

    public boolean is (String description) {
        return description.equals(value) || description.equals(convertedValue) ||
                description.equals(capitalizedConvertedValue);
    }

    public boolean is (Pattern description) {
        return description.matcher(value).matches() ||
                description.matcher(convertedValue).matches();
    }

    public String parseGroupName() {
        final Matcher matcher = GROUP_NAME_PATTERN.matcher(convertedValue);
        if (matcher.find())
            return convertedValue.substring(matcher.start(), matcher.end());
        else return null;
    }

    public List<Integer> parseNumbers() {
        return Stream.of(value.split(" "))
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                       return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
