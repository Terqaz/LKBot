package com.my.services;

import com.mongodb.lang.Nullable;
import com.my.Utils;
import com.my.models.*;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Answer {

    private Answer() {}

    public static final String FOR_ADMIN_NEED_REGISTRATION = "Срочно скажи мне свой новый пароль.";

    public static final String WARNING_APP_STOPPED = "WARNING: APP STOPPED";
    public static final String LK_NOT_RESPONDING = "ЛК сейчас не работает, попробуй это позже";
    public static final String I_BROKEN = "Ой, я сломался Т_Т. Я уже написал администраторам, что не так";
    public static final String YOUR_MESSAGE_IS_SPAM = "Твое сообщение похоже на спам.\n Напиши корректную команду";

    public static final String WRITE_WHICH_GROUP = "Напиши мне из какой ты группы так же, как указано в ЛК";
    public static final String CHANGE_GROUP_HINT = "\n➡ Не уверен, что ввел группу также, как указано в твоем ЛК? Тогда напиши мне: " +
            quotes(Command.CHANGE_GROUP) + "\n";

    public static final String YOU_ALREADY_WRITE_YOUR_GROUP =
            "Ты уже указал мне имя своей группы. " + CHANGE_GROUP_HINT;

    public static final String YOUR_GROUP_IS_NEW =
            "Из твоей группы еще никто не работал со мной. " +
            CHANGE_GROUP_HINT +
            "➡ Если ты хочешь первый из своей группы получать информацию и настраивать обновления " +
            "из ЛК, то напиши мне "+quotes(Command.WANT_TO_LOGIN)+"\n" +
            "➡ Иначе просто ожидай, пока другой человек из твоей группы войдет в ЛК через меня ;-)\n";

    public static final String BECOME_NEW_LEADER_INSTRUCTION =
            "➡ Если ты хочешь продолжить получать информацию для " +
            "своей группы и настраивать обновления из ЛК, то напиши мне " + quotes(Command.WANT_TO_LOGIN) + "\n" +
            "➡ Иначе просто ожидай, пока другой человек из твоей группы войдет в ЛК через меня ;-)";

    public static final String LEADER_EXITED = "Лидер твоей группы вышел\n" +
            BECOME_NEW_LEADER_INSTRUCTION;

    public static final String FOR_NEW_USER_LEADER_EXITED =
            "Из твоей группы уже работали со мной, но ее лидер решил выйти. "+
                    CHANGE_GROUP_HINT +
                    BECOME_NEW_LEADER_INSTRUCTION;

    public static final String GROUP_NOT_LOGGED_AND_YOU_CAN_LOGIN =
            "Из твоей группы еще никто не работал со мной. " +
            "Если ты хочешь зайти, то напиши " + quotes(Command.WANT_TO_LOGIN);

    public static final String CANNOT_CHANGE_LEADER = "Я не могу просто так изменить лидера группы. " +
            "Если в вашей группе решили поменять лидера, то ему следует написать мне "+quotes(Command.FORGET_ME);

    public static final String INPUT_CREDENTIALS =
            "Хорошо.\nВведи свой логин и пароль подряд на двух строках, " +
            "а потом удали свое сообщение на всякий случай ;-)";
    public static final String UPDATE_CREDENTIALS =
            "Кажется ты забыл сказать мне свой новый пароль после изменения его в ЛК. " +
            "Введи свой логин и новый пароль подряд на двух строках, " +
            "а потом удали свое сообщение на всякий случай ;-)";

    public static final String CREDENTIALS_UPDATED = "Я обновил твой пароль";
    public static final String TRY_TO_LOGIN = "Пробую зайти в твой ЛК...";
    public static final String SUCCESSFUL_LOGIN = "Я успешно зашел в твой ЛК";
    public static final String YOU_ALREADY_LOGGED = "Ты уже лидер своей группы";
    public static final String LOGIN_FAILED =
            "Мне не удалось войти через твои логин и пароль. Проверь правильность их ввода";
    public static final String YOU_LATE_LOGIN = "Ой, похоже ты опоздал! Эту группу уже успели зарегистрировать.";
    public static final String I_CAN_SEND_INFO =
            "➡ Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК. " +
            "Тебе нужно просто позвать их пообщаться со мной.";

    public static final String I_KNOW_THIS_GROUP = "О, я знаю эту группу!";
    public static final String I_ALREADY_SENT_CODE = "Я уже прислал проверочный код лидеру твоей группы";
    public static final String TYPE_CODE_INITIALLY = "Сначала введи правильный код доступа";
    public static final String WRONG_CODE = "Ты ввел неправильный код доступа";
    public static final String LEADER_UPDATE_PASSWORD =
            "Лидер твоей группы сказал мне свой новый пароль от ЛК. " +
            "Теперь ты можешь продолжать использовать меня ;-)";

    private static final String BASIC_COMMANDS =
            "🔷 Вывести список предметов с номерами:\n" +
            Command.GET_SUBJECTS+"\n" +
            "🔷 Узнать самую свежую информацию по номеру предмета:\n" +
            "n\n" +
            "🔶 Показать эти команды:\n" +
            Command.COMMANDS+"\n" +
            "🔶 Выйти из бота:\n" +
            Command.FORGET_ME;

    public static final String OK = "Хорошо";
    public static final String COMMAND_FOR_ONLY_LEADER = "Это может сделать только лидер твоей группы";
    public static final String CANNOT_LOGIN =
            "➡ Мне не удалось проверить данные твоей группы. " +
            "Лидер твоей группы не написал мне свой новый пароль. " +
            "Я уже сказал ему об этом.";

    public static final String WRONG_SUBJECT_NUMBER = "Неправильный номер предмета";
    public static final String WRONG_DOCUMENT_NUMBER = "Неправильный номер файла";
    public static final String INTERVAL_CHANGED = "Интервал изменен";
    public static final String WRONG_INTERVAL = "Нельзя установить такой интервал обновления";
    public static final String SILENT_TIME_CHANGED = "Время тихого режима изменено";
    public static final String WRONG_SILENT_TIME = "Нельзя установить такое время тихого режима";
    public static final String TYPE_FORGET_ME = "Напиши " + quotes(Command.FORGET_ME) + ", чтобы перезайти в меня";

    public static final String LEADER_FORGET_ME_NOTICE =
            "➡ Эта опция позволит тебе прекратить пользоваться мной. " +
            "После твоего ухода кому-то из твоей группы нужно будет сказать мне " +
            "свой логин и пароль от ЛК, иначе никто из неё не сможет пользоваться мной.\n" +
            "➡ Если ты уверен, что правильно все делаешь, то напиши: " + quotes(Command.FINALLY_FORGET_ME);

    public static final String USER_FORGET_ME_NOTICE =
            "➡ Эта опция позволит тебе прекратить пользоваться мной." +
            "➡ Если ты уверен, что правильно все делаешь, то напиши: " + quotes(Command.FINALLY_FORGET_ME);
    public static final String AFTER_LEADER_FORGETTING =
            "Хорошо. Рекомендую тебе поменять пароль в ЛК (http://lk.stu.lipetsk.ru).\n" +
            "Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB";
    public static final String AFTER_USER_FORGETTING = "Хорошо. Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB";

    private static String quotes(String s) {
        return "\""+s+"\"";
    }

    public static String getGroupNameChanged(String groupName, String lkGroupName) {
        return "\uD83D\uDD34 Я поменял имя введенной тобой группы "+ groupName +" на: "+ lkGroupName +
                ", чтобы избежать неприятных ситуаций. \uD83D\uDD34";
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

    public static String getDocument(String subjectName, String documentName) {
        return subjectName + " документ:\n\"" + documentName + "\"";
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
                    group.getSilentModeStart() + " до " + group.getSilentModeEnd() + " часов):\n"
                    +
                    "Новый тихий режим: с n до k (вместо n и k числа [0, 23])\n"
                    +
                    getSchedingCommandDescription(group.getUserSchedulingEnabled(userId)) + "\n"
                    +
                    (loggedUser.isAlwaysNotify() ?
                            "🔶 Не присылать пустые результаты обновления:\n"+Command.WITHOUT_EMPTY_REPORTS :
                            "🔶 Присылать даже пустые результаты обновления:\n"+Command.WITH_EMPTY_REPORTS);
        else
            return BASIC_COMMANDS + "\n" +
                    getSchedingCommandDescription(group.getUserSchedulingEnabled(userId));
    }

    private static String getSchedingCommandDescription(boolean enabled) {
        return enabled ?
                "🔶 Не присылать ежедневное расписание на завтра:\n"+ Command.WITHOUT_EVERYDAY_SCHEDULE :
                "🔶 Присылать ежедневное расписание на завтра:\n"+Command.WITH_EVERYDAY_SCHEDULE;
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

        String reportPart = getNewMaterialsDocuments(subjects);
        if (reportPart.length() > 0)
            sb.append("\uD83D\uDD34 Новые документы из материалов:\n" + reportPart);

        reportPart = subjects.stream()
                .filter(data -> !data.getMessagesData().isEmpty())
                .map(data -> "➡ " + data.getId() + " " + data.getName() + ":\n" +
                        getSubjectMessages(data.getMessagesData())
                ).collect(Collectors.joining("\n\n"));

        if (reportPart.length() > 0)
            sb.append("\n\n\uD83D\uDD34 Новые сообщения:\n").append(reportPart);

        if (nextCheckDate != null)
            sb.append("\n\n").append(getNextUpdateDateText(nextCheckDate));

        return sb.toString();
    }

    private static String getNewMaterialsDocuments(List<Subject> subjects) {
        return subjects.stream()
                .filter(subject -> !subject.getMaterialsDocuments().isEmpty())
                .map(subject -> "➡ " + subject.getId() + " " + subject.getName() + ":\n" +
                        subject.getMaterialsDocuments().stream()
                                .sorted(Comparator.comparing(LkDocument::getName))
                                .map(lkDocument -> lkDocument.getId() + " " + lkDocument.getName())
                                .collect(Collectors.joining("\n"))
                ).collect(Collectors.joining("\n\n"));
    }

    public static String emptySubjectsReport(@Nullable Date nextCheckDate) {
        return "Нет новой информации по предметам\n" + getNextUpdateDateText(nextCheckDate);
    }

    private static String getSubjectMessages(List<LkMessage> messages) {
        return messages.stream()
                .map(lkMessage ->
                        "☑ " + lkMessage.getSender() + " в " +
                                Utils.formatDate(lkMessage.getDate()) + ":\n" +
                                lkMessage.getComment() +
                                (lkMessage.getDocument() == null ? "" :
                                        "\nДОКУМЕНТ: "+lkMessage.getDocument().getId()+" "+lkMessage.getDocument().getName())
                ).collect(Collectors.joining("\n\n"));
    }

    public static String getSubjectDocuments(Subject subject) {
        String report = "Документы предмета " + subject.getName() + "\n";
        String reportPart = getSubjectDocumentsPart(subject.getMaterialsDocuments());
        if (!reportPart.isEmpty())
            report += "Документы из материалов:\n" + reportPart;

        reportPart = getSubjectDocumentsPart(subject.getMessagesDocuments());
        if (!reportPart.isEmpty())
            report += "\nДокументы из сообщений:\n" + reportPart;

        return report;
    }

    private static String getSubjectDocumentsPart(Set<LkDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(LkDocument::getId))
                .map(document -> document.getId() + " " + document.getName())
                .collect(Collectors.joining("\n"));
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

    public static String getForAdminsIBroken(Integer userId, Exception e) {
        return "Меня сломал пользователь vk.com/id" + userId + ". Почини меня, хозяин :(\n" +
                ExceptionUtils.getStackTrace(e);
    }
}
