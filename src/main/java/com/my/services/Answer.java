package com.my.services;

import com.my.Utils;
import com.my.models.*;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Answer {

    public static final String TODAY_EMPTY_SCHEDULE = "Если я не ошибаюсь, сегодня у тебя нет пар ;-)";
    public static final String NO_ACCESS_TO_FILE = "Не удалось загрузить этот файл из ЛК, возможно он удален. Попробуй позже";
    public static final String GROUP_ALREADY_EXISTS = "После уточнения имени твоей группы из ЛК, " +
            "я узнал, что она уже существует";
    public static final String VK_LOAD_FILE_FAILED = "Не удалось загрузить этот файл в ВКонтакте";
    public static final String I_CANT_GOT_SUBJECTS =
            "Сейчас мне не удалось загрузить данные по твоим предметам. " +
            "Когда у меня получится загрузить их, я сразу пришлю тебе отчет";

    public static final String WAIT_WHEN_SUBJECTS_LOADED =
            "Пока что я не могу выполнить эту команду, так как мне не удалось загрузить данные по твоим предметам " +
                    "за этот семсетр. Я напишу тебе во время обновления, когда загружу предметы.";
    public static final String FOR_ADMIN_WRITE_WEEK_TYPE = "Напиши мне тип недели (\"белая/зеленая\")";

    private Answer() {}

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
            "а потом удали свое сообщение на всякий случай. Пример ввода:\nмой_логин\nмой_пароль";
    public static final String UPDATE_CREDENTIALS =
            "При обновлении мне не удалось зайти через твои логин и пароль в ЛК.\n" +
            "➡ Если ты не менял свой пароль от ЛК, то все в порядке, и это моя ошибка." +
            "➡ Иначе, введи свой логин и новый пароль подряд на двух строках, " +
            "а потом удали свое сообщение на всякий случай ;-)\n";

    public static final String CREDENTIALS_UPDATED = "Я обновил твой пароль";
    public static final String TRY_TO_LOGIN = "Пробую зайти в твой ЛК...";
    public static final String SUCCESSFUL_LOGIN = "Я успешно зашел в твой ЛК";
    public static final String YOU_ALREADY_LOGGED = "Ты уже лидер своей группы";
    public static final String LOGIN_FAILED =
            "Мне не удалось войти через твои логин и пароль. Проверь правильность их ввода";
    public static final String YOU_LATE_LOGIN = "Ой, похоже ты опоздал! Эту группу уже успели зарегистрировать.";
    public static final String I_CAN_SEND_INFO =
            "➡ Ура, ты лидер своей группы! Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК. " +
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
            "🔷 Получить список документов предмета под номером n:\n" +
            "Документы n\n" +
            "🔷 Получить документ k предмета под номером n:\n" +
            "n k\n" +
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

    public static String getDocument(String subjectName, String documentName, Boolean isExtensionChanged) {
        return subjectName + "\nДокумент: " + quotes(documentName)
                + ((isExtensionChanged != null && !isExtensionChanged) ? "\nУбери из расширения файла единицу, переименовав его. " +
                "ВКонтакте не разрешил отправку этого файла с исходным расширением" : "");
    }

    public static String getDocumentUrl(String subjectName, String documentName, String url) {
        return subjectName + "\nСсылка " + quotes(documentName) + ": " + url;
    }

    public static String getUserCommands (Integer userId, Group group) {
        final LoggedUser loggedUser = group.getLoggedUser();

        if (loggedUser.is(userId))
            return BASIC_COMMANDS + "\n"
                    +
                    getSchedulingCommandDescription(group.getUserSchedulingEnabled(userId))
                    +
                    "\n🔶 Изменить время тихого режима (сейчас с " +
                    group.getSilentModeStart() + " до " + group.getSilentModeEnd() + " часов):\n" +
                    "Новый тихий режим: с n по k (вместо n и k числа от 0 до 23)";

        else
            return BASIC_COMMANDS + "\n" +
                    getSchedulingCommandDescription(group.getUserSchedulingEnabled(userId));
    }

    private static String getSchedulingCommandDescription(boolean enabled) {
        return enabled ?
                "🔶 Не присылать ежедневное расписание в 18 часов на завтра:\n"+ Command.WITHOUT_EVERYDAY_SCHEDULE :
                "🔶 Присылать ежедневное расписание в 18 часов на завтра:\n"+Command.WITH_EVERYDAY_SCHEDULE;
    }

    public static String getTodaySchedule(String dayScheduleReport) {
        return "Держи расписание на сегодня в качестве примера:\n" + dayScheduleReport;
    }

    public static String getTomorrowSchedule(String dayScheduleReport) {
        return "Расписание на завтра:\n" + dayScheduleReport;
    }

    public static String getSubjectsNames (List<Subject> subjects) {
        final var sb = new StringBuilder();
        for (Subject data : subjects) {
            sb.append("➡ ").append(data.getId()).append(" ")
                    .append(data.getName()).append("\n");
        }
        return sb.toString();
    }

    public static String getSubjectsFirstTime(List<Subject> subjects) {
        return "Это данные по твоим предметам: \n" + Answer.getSubjects(subjects);
    }

    public static String getSubjectsSuccessful(List<Subject> subjects) {
        return "Ура! У меня получилось загрузить данные по твоим предметам: \n" + Answer.getSubjects(subjects);
    }

    public static String getSubjects(List<Subject> subjects) {
        if (subjects.isEmpty())
            return "";

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

    private static String getSubjectMessages(List<LkMessage> messages) {
        return messages.stream()
                .map(lkMessage -> {
                    String s = "☑ " + lkMessage.getSender() +
                            Utils.reportFormatMessageDate(lkMessage.getDate()) + ":";

                    if (!lkMessage.getComment().isBlank())
                        s += "\n" + lkMessage.getComment();

                    if (lkMessage.getDocument() != null) {
                        if (lkMessage.getDocument().getUrl() == null)
                            s += "\nДОКУМЕНТ: " + lkMessage.getDocument().getId() + " " + lkMessage.getDocument().getName();
                        else
                            s += "\nССЫЛКА: " + lkMessage.getDocument().getId() + " " + lkMessage.getDocument().getName();
                    }
                    return s;
                }).collect(Collectors.joining("\n\n"));
    }

    public static String getSubjectDocuments(Subject subject) {
        if (!subject.hasDocuments())
            return "У предмета " + subject.getName() + " нет документов";

        String report = "\uD83D\uDD34 Документы предмета " + subject.getName();
        String reportPart = getMaterialsDocuments(subject.getMaterialsDocuments());
        if (!reportPart.isEmpty())
            report += "\n➡ Документы из материалов:\n" + reportPart;

        reportPart = getMessagesDocuments(subject.getMessagesDocuments());
        if (!reportPart.isEmpty())
            report += "\n➡ Документы из сообщений:\n" + reportPart;

        return report;
    }

    private static String getMaterialsDocuments(Set<LkDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(LkDocument::getId))
                .map(document -> document.getId() + " " + document.getName())
                .collect(Collectors.joining("\n"));
    }

    private static String getMessagesDocuments(Set<LkDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(LkDocument::getId))
                .map(document -> document.getSender() + ": " + document.getId() + " " + document.getName())
                .collect(Collectors.joining("\n"));
    }

    public static String getDaySchedule(Group group, int weekDay, boolean isWeekWhite) {
        if (weekDay >= 6)
            return "";

        else return getDaySchedule(isWeekWhite ?
                        group.getTimetable().getWhiteSubjects().get(weekDay) :
                        group.getTimetable().getGreenSubjects().get(weekDay),
                isWeekWhite);
    }

    public static String getDaySchedule (List<TimetableSubject> subjects, boolean isWhiteWeek) {
        return subjects.isEmpty() ? "" :
                "\uD83D\uDD36 " + (isWhiteWeek ? "Белая" : "Зеленая") + " неделя" + " \uD83D\uDD36\n" +
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
