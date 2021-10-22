package com.my.services;

import com.mongodb.lang.Nullable;
import com.my.Utils;
import com.my.models.*;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Answer {

    public static final String LEADER_FORGET_ME_NOTICE =
            "➡ Эта опция позволит тебе прекратить пользоваться мной." +
            "После твоего ухода кому-то из твоей группы нужно будет сказать мне " +
            "свой логин и пароль от ЛК, иначе никто из твоей группы не сможет пользоваться мной.\n" +
            "➡ Если ты уверен, что правильно все делаешь, то напиши: " + quotes(Command.FINALLY_FORGET_ME);

    public static final String USER_FORGET_ME_NOTICE =
            "➡ Эта опция позволит тебе прекратить пользоваться мной." +
            "➡ Если ты уверен, что правильно все делаешь, то напиши: " + quotes(Command.FINALLY_FORGET_ME);
    public static final String AFTER_LEADER_FORGETTING =
            "Хорошо. Рекомендую тебе поменять пароль в ЛК (http://lk.stu.lipetsk.ru).\n" +
            "Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB";
    public static final String AFTER_USER_FORGETTING = "Хорошо. Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB";

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

    public static final String BECOME_NEW_LEADER_INSTRUCTION = "➡ Если ты хочешь продолжить получать информацию для своей группы и настраивать обновления " +
            "из ЛК, то напиши мне " + quotes(Command.WANT_TO_LOGIN) + "\n" +
            "➡ Иначе просто ожидай, пока другой человек из твоей группы войдет в ЛК через меня ;-)";

    public static final String LEADER_EXITED = "Лидер твоей группы вышел\n" +
            BECOME_NEW_LEADER_INSTRUCTION;

    public static final String FOR_NEW_USER_LEADER_EXITED =
            "Из твоей группы уже работали со мной, но ее лидер решил выйти\n" +
                    BECOME_NEW_LEADER_INSTRUCTION;

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
    public static final String I_ALREADY_SENT_CODE = "Я уже присылал проверочный код лидеру твоей группы";
    public static final String TYPE_CODE_INITIALLY = "Сначала введи правильный код доступа";
    public static final String WRONG_CODE = "Ты ввел неправильный код доступа";

    private static final String BASIC_COMMANDS =
            "🔷 Вывести список предметов:\n" +
                    Command.GET_SUBJECTS+"\n" +
                    "🔷 Узнать самую свежую информацию по предмету из ЛК:\n" +
                    "n (n - номер в моем списке предметов)\n" +
                    "🔶 Показать эти команды:\n" +
                    Command.COMMANDS+"\n" +
                    "🔶 Прекратить пользоваться ботом или сменить зарегистрированного человека:\n" +
                    Command.FORGET_ME;

    public static final String OK = "Хорошо";
    public static final String COMMAND_FOR_ONLY_LEADER = "Это может сделать только лидер твоей группы";
    public static final String CANNOT_LOGIN =
            "➡ Мне не удалось проверить данные твоей группы. " +
                    "Лидер твоей группы не написал мне свой новый пароль. " +
                    "Я уже сказал ему об этом.";

    public static final String WRONG_SUBJECT_NUMBER = "Неправильный номер предмета";
    public static final String INTERVAL_CHANGED = "Интервал изменен";
    public static final String WRONG_INTERVAL = "Нельзя установить такой интервал обновления";
    public static final String SILENT_TIME_CHANGED = "Время тихого режима изменено";
    public static final String WRONG_SILENT_TIME = "Нельзя установить такое время тихого режима";
    public static final String TYPE_FORGET_ME = "Напиши " + quotes(Command.FORGET_ME) + ", чтобы перезайти в меня";

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

    public static String getNowICanSendSubjectsInfo(List<Subject> subjects) {
        return "Теперь я могу вывести тебе последнюю информацию из ЛК по данным предметам:\n" +
                getSubjectsNames(subjects);
    }

    public static String getNowYouCanUseCommands(Integer userId, Group group) {
        return "Также теперь ты можешь использовать эти команды:\n" +
                getUserCommands(userId, group);
    }

    public static String getUserCommands (Integer userId, Group group) {
        final LoggedUser loggedUser = group.getLoggedUser();

        if (loggedUser.is(userId))
            return BASIC_COMMANDS +
                    "\n🔶 Изменить интервал автоматического обновления (сейчас раз в " +
                    group.getUpdateInterval() / 60000 + " минут):\n" + // Целочисленное деление
                    "Новый интервал обновления: n (n - количество минут [5, 20160])\n"
                    +
                    "🔶 Изменить время тихого режима (сейчас с " +
                    group.getSilentModeStart() + " до " + group.getSilentModeEnd() + " часов):\n" +
                    "Новый тихий режим: с n до k (вместо n и k числа [0, 23])\n"
                    +
                    (loggedUser.isAlwaysNotify() ?
                            "🔶 Не присылать пустые результаты обновления:\n"+Command.WITHOUT_EMPTY_REPORTS :
                            "🔶 Присылать даже пустые результаты обновления:\n"+Command.WITH_EMPTY_REPORTS);

        else {
            return BASIC_COMMANDS + "\n" +
                    (group.getUserSchedulingEnabled(userId) ?
                            "🔶 Не присылать ежедневное расписание на завтра:\n"+Command.WITHOUT_EVERYDAY_SCHEDULE :
                            "🔶 Присылать ежедневное расписание на завтра:\n"+Command.WITH_EVERYDAY_SCHEDULE);
        }
    }

    public static String getNoNewSubjectInfo(String subjectName) {
        return "Нет новой информации по предмету:\n " + subjectName;
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

    public static String getNewLeaderIs(String leaderName) {
        return "Теперь в твоей группе новый лидер: " + leaderName;
    }

    public static String getSendMeCode(String leaderName) {
        return "Скажи мне проверочный код, присланный лидеру твоей группы. Его зовут: " + leaderName;
    }
}
