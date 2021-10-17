package com.my.models;

import java.util.regex.Pattern;

public final class Command {

    public static final String CHANGE_GROUP = "Измени группу";
    public static final String WANT_TO_LOGIN = "Хочу войти в ЛК";
    public static final String GET_SUBJECTS = "Предметы";
    public static final String COMMANDS = "Команды";
    public static final String WITHOUT_EMPTY_REPORTS = "Без пустых отчетов";
    public static final String WITH_EMPTY_REPORTS = "С пустыми отчетами";
    public static final String UPDATE_SCHEDULE = "Обновить расписание";
    public static final String SEND_EVERYDAY_SCHEDULE = "Присылай расписание";
    public static final String NOT_SEND_EVERYDAY_SCHEDULE = "Не присылай расписание";
    public static final String FORGET_ME = "Забудь меня";
    public static final Pattern CHANGE_UPDATE_INTERVAL = wrap("^изменить интервал на \\d+$");

    private static Pattern wrap (String command) {
        return Pattern.compile(command, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
    }

    private final String value;

    public Command(String value) {
        this.value = value;
    }

    public boolean is (String description) {
        return description.equals(value);
    }

    public boolean is (Pattern description) {
        return description.matcher(value).matches();
    }
}
