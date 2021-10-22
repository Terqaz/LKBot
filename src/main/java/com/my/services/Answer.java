package com.my.services;

import com.mongodb.lang.Nullable;
import com.my.Utils;
import com.my.models.Command;
import com.my.models.Message;
import com.my.models.Subject;
import com.my.models.TimetableSubject;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Answer {

    public static final String TYPE_FORGET_ME = "Напиши "+quotes(Command.FORGET_ME)+", чтобы перезайти в меня";
    public static final String COMMAND_FOR_ONLY_LEADER = "Это может сделать только лидер твоей группы";

    private Answer() {}


    public static final String WARNING_APP_STOPPED = "WARNING: APP STOPPED";
    public static final String LK_NOT_RESPONDING = "ЛК сейчас не работает, попробуй это позже";
    public static final String NOT_UNDERSTAND_YOU_OR_MISTAKE = "Я не понял тебя или ошибся сам";
    public static final String YOUR_MESSAGE_IS_SPAM = "Твое сообщение похоже на спам.\n Напиши корректную команду";

    public static final String WRITE_WHICH_GROUP = "Напиши мне из какой ты группы так же, как указано в ЛК";
    public static final String YOU_ALREADY_WRITE_YOUR_GROUP =
            "Ты уже указал мне имя своей группы.\n" +
            "Если ты ошибся при вводе группы, то напиши мне " + quotes(Command.CHANGE_GROUP);

    public static final String YOUR_GROUP_IS_NEW =
            "Из твоей группы еще никто не работал со мной\n" +
            "➡ Если ты хочешь первый из своей группы получать информацию и настраивать обновления " +
            "из ЛК, то напиши мне "+quotes(Command.WANT_TO_LOGIN)+"\n" +
            "➡ Иначе просто ожидай, пока другой человек из твоей группы войдет в ЛК через меня ;-)";

    public static final String YOUR_GROUP_WITHOUT_LEADER =
            "Из твоей группы уже работали со мной, но ее лидер решил выйти\n" +
            "➡ Если ты хочешь продолжить получать информацию для своей группы и настраивать обновления " +
            "из ЛК, то напиши мне "+quotes(Command.WANT_TO_LOGIN)+"\n" +
            "➡ Иначе просто ожидай, пока другой человек из твоей группы войдет в ЛК через меня ;-)";

    public static final String GROUP_NOT_LOGGED_AND_YOU_CAN_LOGIN =
            "Из твоей группы еще никто не работал со мной. " +
            "Если ты хочешь зайти, то напиши " + quotes(Command.WANT_TO_LOGIN);

    public static final String CANNOT_CHANGE_LEADER = "Я не могу просто так изменить лидера группы. " +
            "Если в вашей группе решили поменять лидера, то ему следует написать мне "+quotes(Command.FORGET_ME);
    public static final String INPUT_CREDENTIALS =
            "Хорошо.\nВведи свой логин и пароль подряд через пробел, " +
            "а потом удали свое сообщение на всякий случай ;-)";
    public static final String TRY_TO_LOGIN = "Пробую зайти в твой ЛК...";
    public static final String SUCCESSFUL_LOGIN = "Я успешно зашел в твой ЛК";
    public static final String LOGIN_FAILED =
            "Мне не удалось войти через твои логин и пароль. Проверь правильность их ввода";
    public static final String YOU_LATE_LOGIN = "Ой, похоже ты опоздал! Эту группу уже успели зарегистрировать.";
    public static final String I_CAN_SEND_INFO =
            "➡ Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК. " +
            "Тебе нужно просто позвать их пообщаться со мной.";

    public static final String I_KNOW_THIS_GROUP = "О, я знаю эту группу!";
    public static final String SEND_ME_CODE = "Скажи мне проверочный код, присланный лидеру твоей группы";
    public static final String I_ALREADY_SENT_CODE = "Я уже присылал проверочный код лидеру твоей группы";
    public static final String TYPE_CODE_INITIALLY = "Сначала введи правильный код доступа";
    public static final String WRONG_CODE = "Ты ввел неправильный код доступа";

    public static final String INTERVAL_CHANGED = "Интервал изменен";
    public static final String WRONG_INTERVAL = "Нельзя установить такой интервал обновления";
    public static final String SILENT_TIME_CHANGED = "Время тихого режима изменено";
    public static final String WRONG_SILENT_TIME = "Нельзя установить такое время тихого режима";

    private static String quotes(String s) {
        return "\""+s+"\"";
    }

    public static String getYouAddedToGroup (String groupName) {
        return "Я добавил тебя в группу "+groupName;
    }

    public static String getVerificationCode(String userName, Integer verificationCode) {
        return "Проверочный код для входа в группу пользователя " +
                userName + ": " + verificationCode;
    }

    public static String getUserAdded(String userName) {
        return "Пользователь "+userName+" добавлен в группу";
    }

    public static String getTodaySchedule(String dayScheduleReport) {
        return "Держи расписание на сегодня ;-)\n" + dayScheduleReport;
    }

    public static String getTomorrowSchedule(String dayScheduleReport) {
        return "Держи расписание на завтра ;-)\n" + dayScheduleReport;
    }

    public static String getUpdateNotSuccessful(Date nextCheckDate) {
        return "Обновление не удалось, так как ЛК долго отвечал на запросы. " +
                Answer.getNextUpdateDateText(nextCheckDate);
    }

    public static String getSubjectsNames (List<Subject> subjects) {
        final var sb = new StringBuilder();
        for (Subject data : subjects) {
            sb.append("➡ ").append(data.getId()).append(" ")
                    .append(data.getName()).append("\n");
        }
        return sb.toString();
    }

    public static String getSubjects(List<Subject> subjects, @Nullable Date nextCheckDate) {
        if (subjects.isEmpty())
            return emptySubjectsReport(nextCheckDate);

        final var sb = new StringBuilder();

        String partBuilder = subjects.stream()
                .filter(subject -> !subject.getDocumentNames().isEmpty())
                .map(data -> "➡ " + data.getId() + " " + data.getName() + ":\n" +
                        data.getDocumentNames().stream()
                                .sorted()
                                .collect(Collectors.joining("\n"))
                ).collect(Collectors.joining("\n\n"));

        if (partBuilder.length() > 0)
            sb.append("\uD83D\uDD34 Новые документы:\n" + partBuilder);

        partBuilder = subjects.stream()
                .filter(data -> !data.getMessages().isEmpty())
                .map(data -> "➡ " + data.getId() + " " + data.getName() + ":\n" +
                        getSubjectMessages(data.getMessages())
                ).collect(Collectors.joining("\n\n"));

        if (partBuilder.length() > 0)
            sb.append("\n\n\uD83D\uDD34 Новые сообщения:\n").append(partBuilder);

        if (nextCheckDate != null)
            sb.append("\n\n").append(getNextUpdateDateText(nextCheckDate));

        return sb.toString();
    }

    public static String emptySubjectsReport(@Nullable Date nextCheckDate) {
        return "Нет новой информации по предметам\n" + getNextUpdateDateText(nextCheckDate);
    }

    private static String getSubjectMessages(List<Message> messages) {
        return messages.stream()
                .map(messageData ->
                        "☑ " + messageData.getSender() + " в " +
                                Utils.formatDate(messageData.getDate()) + ":\n" +
                                messageData.getComment()
                ).collect(Collectors.joining("\n\n"));
    }

    public static String getNextUpdateDateText (Date nextCheckDate) {
        return "Следующее обновление в " + Utils.formatDate(nextCheckDate);
    }

    public static String getDaySchedule (List<TimetableSubject> subjects, boolean isWhiteWeek) {
        return subjects.isEmpty() ? "" :
                "\uD83D\uDD36 " +
                        (isWhiteWeek ? "Белая неделя" : "Зеленая неделя") +
                        " \uD83D\uDD36\n" +
                        subjects.stream()
                                .map(subject -> "➡ " + subject.getInterval() + ' ' +
                                        subject.getName() + '\n' +
                                        subject.getPlace() + '\n' +
                                        subject.getAcademicName())
                                .collect(Collectors.joining("\n\n"));

    }
}
