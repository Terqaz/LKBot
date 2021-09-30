package com.my;

import com.my.exceptions.ApplicationStopNeedsException;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import com.my.exceptions.ReloginNeedsException;
import com.my.models.*;
import com.my.services.CipherService;
import com.my.services.ReportsMaker;
import com.my.services.lk.LkParser;
import com.my.services.text.KeyboardLayoutConverter;
import com.my.services.vk.KeyboardService;
import com.my.services.vk.VkBotService;
import com.my.threads.PlannedScheduleSending;
import com.my.threads.PlannedSubjectsUpdate;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static final String APPLICATION_STOP_TEXT = "I WANT TO STOP THE APPLICATION";

    static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    static final VkBotService vkBot = VkBotService.getInstance();
    static CipherService cipherService;

    static final Map<Integer, String> groupNameByUserId = new HashMap<>();
    @Getter
    static final Map<String, Group> groupByGroupName = new HashMap<>();

    private static final String BASIC_COMMANDS =
                    "🔷 Вывести список предметов:\n" +
                    "Предметы\n" +
                    "🔷 Узнать самую свежую информацию по предмету из ЛК:\n" +
                    "n (n - номер в моем списке предметов)\n" +
                    "🔶 Показать эти команды:\n" +
                    "Команды\n" +
                    "🔶 Прекратить пользоваться ботом или сменить зарегистрированного человека:\n" +
                    "Забудь меня";

    private static final String AUTH_COMMAND =
            "(рекомендую сразу удалить это сообщение):\n" +
                    "Хочу войти в ЛК\n" +
                    "Мой_логин\n" +
                    "Мой_пароль";

    static final int APP_ADMIN_ID = 173315241;

    private static final Pattern groupNamePatternOnlyUpperCase =
            Pattern.compile("((Т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,4}-)(П-)?\\d{2}(-\\d)?");

    @Getter @Setter
    private volatile static String actualSemester;
    private static boolean isActualWeekWhite;

    public static void main (String[] args)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {

        cipherService = CipherService.getInstance();
        actualSemester = Utils.getSemesterName();
        fillCaches();
        try {
            actualizeWeekType();
        } catch (Exception e) {
            e.printStackTrace();
        }

        final var plannedSubjectsUpdate = new PlannedSubjectsUpdate();
        plannedSubjectsUpdate.start();
        final var plannedScheduleSending = new PlannedScheduleSending();
        plannedScheduleSending.start();

        vkBot.setOnline(true);
        vkBot.sendMessageTo(APP_ADMIN_ID,
                "APP STARTED.\nTO STOP TYPE: "+ APPLICATION_STOP_TEXT);
        try {
            runCycle();
        } catch (ApplicationStopNeedsException ignored) {}

        vkBot.setOnline(false);
        plannedSubjectsUpdate.interrupt();
        plannedScheduleSending.interrupt();
        vkBot.sendMessageTo(APP_ADMIN_ID, "WARNING: APP STOPPED");
    }

    private static void fillCaches() {
        groupsRepository.findAll()
                .forEach(group -> {
                    groupByGroupName.put(group.getName(), group);
                    group.setLkParser(new LkParser());
                });

        groupByGroupName.values().forEach(group -> {
            if (!group.isLoggedBefore())
                return;

            final var groupName = group.getName();
            group.getUsers()
                    .forEach(user -> groupNameByUserId.put(user.getId(), groupName));
            group.getLoginWaitingUsers()
                    .forEach(userId -> groupNameByUserId.put(userId, groupName));
        });
    }

    private static void runCycle () {
        while (true) {
            vkBot.getNewMessages().forEach(message -> {
                final Integer userId = message.getFromId();
                final var messageText = message.getText();
                if (userId.equals(APP_ADMIN_ID) && messageText.equals(APPLICATION_STOP_TEXT))
                    throw new ApplicationStopNeedsException();
                try {
                    Thread.sleep(10); // Чтобы не ддосили ЛК
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (userId > 0) {
                    // TODO если добавить описание ошибок и пожелания, то изменить условие
                    if (messageText.length() > 100)
                        vkBot.sendMessageTo(userId,
                                "Твое сообщение похоже на спам.\n Напиши корректную команду");
                    CompletableFuture.runAsync(() -> {
                        try {
                            executeBotDialog(userId, messageText);

                        } catch (AuthenticationException e) {
                            loginFailedMessages(userId, groupByGroupName.get(groupNameByUserId.get(userId)));

                        } catch (ReloginNeedsException e) {
                            login(groupByGroupName.get(groupNameByUserId.get(userId)));
                            executeBotDialog(userId, messageText);

                        } catch (LkNotRespondingException e) {
                            vkBot.sendMessageTo(userId, "Кажется ЛК сейчас не работает, попробуй это позже");
                        } catch (Exception e) {
                            vkBot.sendMessageTo(userId, "Я не понял тебя или ошибся сам.");
                            e.printStackTrace();
                        }
                    });
                }
            });
        }
    }

    private static void executeBotDialog (Integer userId, String messageText) {
        messageText = KeyboardLayoutConverter.translateFromEnglishLayoutIfNeeds(messageText);
//        SpecialWordsFinder.findSpecialWords(userId, messageText);

        final var groupNameMatcher =
                groupNamePatternOnlyUpperCase.matcher(messageText.toUpperCase());
        if (groupNameMatcher.find()) { // Я из ПИ-19-1 и тд.
            newUserGroupCheck(userId, messageText, groupNameMatcher);
            return;

        } else if (messageText.startsWith("Хочу войти в ЛК")) {
            onLoginMessages(userId, groupNameByUserId.get(userId), messageText);
            return;
        }

        if (!groupNameByUserId.containsKey(userId)) {
            vkBot.sendMessageTo(userId,
                    "Напиши из какой ты группы (так же, как указано в ЛК). Например:\n" +
                            "Я из ПИ-19-1");
            return;
        }

        final var groupName = groupNameByUserId.get(userId);
        final var optionalGroup = Optional.ofNullable(groupByGroupName.get(groupName));

        messageText = messageText.toLowerCase();
        switch (messageText) {
            case "я готов на все ради своей группы!":
                if (optionalGroup.map(Group::isNotLoggedNow).orElse(true)) {
                    vkBot.unsetKeyboard();
                    vkBot.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так " +
                                    AUTH_COMMAND);
                } else
                    groupAlreadyRegisteredMessage(userId);
                return;

            case "лучше скажу другому":
                if (optionalGroup.map(Group::isNotLoggedNow).orElse(true)) {
                    vkBot.unsetKeyboard();
                    groupNameByUserId.remove(userId);
                    vkBot.sendMessageTo(userId,
                            "Хорошо. Напиши мне, когда человек из твоей группы зайдет через меня");
                } else
                    groupAlreadyRegisteredMessage(userId);
                return;

            case "я ошибся при вводе группы":
                if (optionalGroup.map(group -> group.containsUser(userId)).orElse(false)) {
                    vkBot.sendMessageTo(userId, "Напиши \"Забудь меня\", чтобы перезайти в меня");
                } else {
                    vkBot.unsetKeyboard();
                    groupNameByUserId.remove(userId);
                    vkBot.sendMessageTo(userId,
                            "Введи новое имя для группы (так же, как указано в ЛК). Например:\n" +
                                    "Я из ПИ-19-1");
                }
                return;
            default: break;
        }

        if (optionalGroup.isEmpty()) {
            vkBot.sendMessageTo(userId, "Сначала нужно зарегистрироваться от имени твоей группы");
            return;
        }
        final var group = optionalGroup.get();
        final LoggedUser loggedUser = group.getLoggedUser();

        Integer integer = Utils.tryParseInteger(messageText);
        if (integer != null) {
            if (group.containsUser(userId) && integer < 100) { // Если номер предмета (с запасом)
                login(group);
                getActualSubjectMessage(userId, group, integer);
            }

            else if (!group.containsUser(userId) && 100_000 <= integer && integer < 1_000_000) // Если проверочный код
                addUserByVerificationCode(group, userId, integer);

            else vkBot.sendMessageTo(userId, "Я не понял тебя");
            return;
        }

        if (!group.containsUser(userId)) {
            vkBot.sendMessageTo(userId,
                    "Сначала присоединись к своей группе");
            return;
        }

        if (messageText.startsWith("изменить интервал на ")) {
            if (loggedUser.is(userId)) {
                final var minutes = Long.parseLong(messageText.substring(21));
                if (10 <= minutes && minutes <= 20160) {
                    final long newUpdateInterval = minutes * 60 * 1000;

                    groupsRepository.updateField(groupName,"updateInterval", newUpdateInterval);
                    group.setUpdateInterval(newUpdateInterval);

                    vkBot.sendMessageTo(userId, "Интервал изменен");
                    group.setUpdateInterval(newUpdateInterval);
                    nextUpdateDateMessage(userId, group.getNextCheckDate());
                } else
                    vkBot.sendMessageTo(userId, "Нельзя установить такой интервал обновления");
            } else
                userInsufficientPermissionsMessage(userId);
            return;

        } else if (messageText.startsWith("тихий режим с ")) { // Тихий режим с n по k
            if (loggedUser.is(userId)) {
                final String[] strings = messageText.split(" ");
                final var startHour = Integer.parseInt(strings[3]);
                final var endHour = Integer.parseInt(strings[5]);
                if (!(0 <= startHour && startHour <= 23 && 0 <= endHour && endHour <= 23)) {
                    vkBot.sendMessageTo(userId, "Нельзя установить такое время тихого режима");
                } else {
                    groupsRepository.updateSilentMode(groupName, startHour, endHour);
                    group.setSilentModeStart(startHour).setSilentModeEnd(endHour);

                    vkBot.sendMessageTo(userId, "Время тихого режима изменено");
                }
            } else userInsufficientPermissionsMessage(userId);
            return;
        }

        switch (messageText) {
            case "предметы":
                vkBot.sendMessageTo(userId, ReportsMaker.getSubjectsNames(group.getSubjects()));
                break;

            case "команды":
                vkBot.sendMessageTo(userId,
                        KeyboardService.getCommands(userId, group),
                        getUserCommands(userId, group));
                break;

            case "присылай расписание":
                changeUserSchedulingEnable(userId, group, true);
                break;

            case "не присылай расписание":
                changeUserSchedulingEnable(userId, group, false);
                break;

            case "без пустых отчетов":
                changeLoggedUserNotifying(userId, group, false);
                break;

            case "с пустыми отчетами":
                changeLoggedUserNotifying(userId, group, true);
                break;

            case "обновить расписание":
                if (group.getLoggedUser().is(userId)) {
                    login(group);

                    final Timetable timetable = group.getLkParser().parseTimetable(group.getLkSemesterId(), group.getLkId());
                    groupsRepository.updateField(groupName,"timetable", timetable);
                    group.setTimetable(timetable);

                    vkBot.sendMessageTo(group.getLoggedUser().getId(), "Расписание обновлено");
                } else
                    userInsufficientPermissionsMessage(userId);
                break;

            case "забудь меня":
                if (loggedUser.is(userId))
                    vkBot.sendMessageTo(userId,
                            "➡ Эта опция полезна, если тебе нужно изменить человека, " +
                                    "зарегистрированного от имени группы или если я тебе больше не нужен. " +
                                    "После твоего ухода кому-то нужно будет сказать мне логин и пароль от своего ЛК, " +
                                    "если вы хотите продолжать пользоваться мной. " +
                                    "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                    "Я уверен, что хочу, чтобы ты забыл меня");
                else
                    vkBot.sendMessageTo(userId,
                            "➡ Эта опция будет полезна тебе, чтобы войти от имени группы после того, " +
                                    "как я забыл другого зарегистрированного человека из твоей группы, " +
                                    "или если я тебе больше не нужен. \n" +
                                    "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                    "Я уверен, что хочу, чтобы ты забыл меня");
                break;

            case "я уверен, что хочу, чтобы ты забыл меня":
                vkBot.unsetKeyboard();
                if (loggedUser.is(userId)) {
                    vkBot.sendMessageTo(userId,
                            "Хорошо. Рекомендую тебе поменять пароль в ЛК (http://lk.stu.lipetsk.ru).\n" +
                                    "Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB");

                    groupsRepository.removeLoggedUser(groupName, loggedUser.getId());
                    group.removeLoggedUser(loggedUser.getId());

                } else
                    vkBot.sendMessageTo(userId,"Хорошо. Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB");
                groupNameByUserId.remove(userId);
                groupsRepository.removeUserFromGroup(groupName, userId);
                group.removeUserFromGroup(userId);
                break;

            default:
                vkBot.sendMessageTo(userId, "Я не понял тебя");
                break;
        }
    }

    public static boolean login(Group group) {
        return group.getLkParser()
                .login(cipherService.decrypt(group.getLoggedUser().getAuthData()));
    }

    private static void addUserByVerificationCode (Group group, Integer userId, Integer code) {
        final UserToVerify userToVerify = new UserToVerify(userId, code);
        if (groupsRepository.moveVerifiedUserToUsers(group.getName(), userToVerify)) {
            group.moveVerifiedUserToUsers(userToVerify);
            vkBot.sendMessageTo(group.getLoggedUser().getId(),
                    "Пользователь "+vkBot.getUserName(userId)+" добавлен в группу");
            vkBot.sendMessageTo(userId, "Я добавил тебя в группу "+group.getName());
            newUserSubjectsListMessage(userId, group);
        } else {
            vkBot.sendMessageTo(userId, "Ты ввел неправильный код доступа");
        }
    }

    private static void newUserGroupCheck (Integer userId, String messageText, Matcher groupNameMatcher) {
        if (groupNameByUserId.containsKey(userId)) {
            vkBot.sendMessageTo(userId, "Я уже знаю, что ты из " + groupNameByUserId.get(userId));
            return;
        }

        final String groupName = messageText.substring(groupNameMatcher.start(), groupNameMatcher.end());
        groupNameByUserId.put(userId, groupName);

        final var optionalGroup = Optional.ofNullable(groupByGroupName.get(groupName));

        if (!optionalGroup.map(Group::isLoggedBefore).orElse(false)) {
            vkBot.sendMessageTo(userId, "Я не знаю группы " + groupName);
            newUserMessage(userId);
            return;
        }
        if (optionalGroup.map(Group::isNotLoggedNow).orElse(false)) {
            vkBot.sendMessageTo(userId,
                    "В этой группе был человек, вошедший от своего имени, но теперь его нет. " +
                            "Ты хочешь стать им?\n");
            newUserMessage(userId);
            return;
        }

        Group group = optionalGroup.get();
        vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_2, "О, я знаю эту группу!");

        final Integer verificationCode = Utils.generateVerificationCode();

        final UserToVerify userToVerify = new UserToVerify(userId, verificationCode);
        if (groupsRepository.addUserToUsersToVerify(group.getName(), userToVerify)) {
            group.getUsersToVerify().add(userToVerify);

            vkBot.sendMessageTo(group.getLoggedUser().getId(),
                    "Проверочный код для входа в группу пользователя "+
                            vkBot.getUserName(userId)+": "+ verificationCode);
            vkBot.sendMessageTo(userId, "Скажи мне проверочный код, присланный лидеру твоей группы");
        } else
            vkBot.sendMessageTo(userId, "Я уже присылал проверочный код лидеру твоей группы");
    }

    private static void newUserMessage (Integer userId) {
        vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_1,
                "➡ Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию " +
                        "для тебя и твоих одногруппников.\n" +
                        "Можешь мне довериться ;-)\n" +
                        "➡ Если ты мне не доверяешь, то позволь ввести пароль другому человеку из твоей группы. " +
                        "Обещаю не писать тебе, когда в этом нет необходимости.\n\n" +
                        "➡ Все мои возможности смотри в группе:\nhttps://vk.com/dorimelk");
    }

    private static void newUserSubjectsListMessage (Integer userId, Group group) {
        vkBot.sendMessageTo(userId,
                "Теперь я могу вывести тебе последнюю информацию из ЛК по данным предметам:\n" +
                        ReportsMaker.getSubjectsNames(group.getSubjects()));

        group.getUsers().add(new GroupUser(userId));
        vkBot.sendMessageTo(userId, KeyboardService.getCommands(userId, group),
                "Также теперь ты можешь использовать эти команды:\n" + getUserCommands(userId, group));
    }

    private static String getUserCommands (Integer userId, Group group) {
        final LoggedUser loggedUser = group.getLoggedUser();

        if (loggedUser.is(userId))
            return BASIC_COMMANDS +
                    "\n🔶 Изменить интервал автоматического обновления (сейчас раз в " +
                    group.getUpdateInterval() / 60000 + " минут):\n" + // Целочисленное деление
                    "Изменить интервал на n (n - количество минут [10, 20160])\n"
                    +
                    "🔶 Изменить время тихого режима (сейчас с " +
                    group.getSilentModeStart() + " до " + group.getSilentModeEnd() + " часов):\n" +
                    "Тихий режим с n по k (вместо n и k числа [0, 23])\n"
                    +
                    (loggedUser.isAlwaysNotify() ?
                            "🔶 Не писать тебе, пока нет новой информации:\nБез пустых отчетов\n" :
                            "🔶 Писать тебе, пока нет новой информации:\nС пустыми отчетами")
                    +
                    "🔶 Обновить расписание из ЛК для группы:\n" +
                    "Обновить расписание";

        else {
            return BASIC_COMMANDS + "\n" +
                    (group.getUserSchedulingEnabled(userId) ?
                                    "🔶 Не присылать тебе ежедневное расписание:\nНе присылай расписание" :
                                    "🔶 Присылать тебе ежедневное расписание:\nПрисылай расписание");
        }
    }

    private static void groupAlreadyRegisteredMessage (Integer userId) {
        vkBot.sendMessageTo(userId,
                "Ой, похоже ты опоздал! Эту группу уже успели зарегистрировать.");
    }

    private static void changeUserSchedulingEnable(Integer userId, Group group, boolean isEnable) {
        groupsRepository.updateUserScheduling(group.getName(), userId, isEnable);
        group.getUsers().stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst().get()
                .setEverydayScheduleEnabled(isEnable);

        vkBot.sendMessageTo(userId, KeyboardService.getCommands(userId, group),"Хорошо");
        if (isEnable) {
            final String dayScheduleReport = getDayScheduleReport(Utils.mapWeekDayFromCalendar(), group);
            if (!dayScheduleReport.isEmpty())
                vkBot.sendMessageTo(userId, "Держи расписание на сегодня ;-)\n"+
                        dayScheduleReport);
        }
    }

    public static String getDayScheduleReport(int weekDay, Group group) {
        return ReportsMaker.getDaySchedule(isActualWeekWhite ?
                        group.getTimetable().getWhiteWeekDaySubjects().get(weekDay) :
                        group.getTimetable().getGreenWeekDaySubjects().get(weekDay),
                isActualWeekWhite);
    }

    private static void changeLoggedUserNotifying (Integer userId, Group group, boolean isEnable) {
        if (group.getLoggedUser().is(userId)) {
            groupsRepository.updateField(group.getName(), "loggedUser.alwaysNotify", isEnable);
            group.getLoggedUser().setAlwaysNotify(isEnable);
            vkBot.sendMessageTo(userId, KeyboardService.getLoggedUserCommands(group),"Хорошо");
        } else
            userInsufficientPermissionsMessage(userId);
    }

    private static void nextUpdateDateMessage (Integer userId, Date nextCheckDate) {
        nextUpdateDateMessage(Collections.singletonList(userId), nextCheckDate);
    }

    private static void nextUpdateDateMessage (Collection<Integer> userIds, Date nextCheckDate) {
        vkBot.sendMessageTo(userIds, ReportsMaker.getNextUpdateDateText(nextCheckDate));
    }

    private static void userInsufficientPermissionsMessage (Integer userId) {
        vkBot.sendMessageTo(userId,
                "Я разрешаю эту операцию только человеку, вошедшему от имени группы");
    }

    private static void getActualSubjectMessage (Integer userId, Group group, Integer subjectIndex) {
        final var optionalSubject = group.getSubjects().stream()
                .filter(subject1 -> subject1.getId() == subjectIndex)
                .findFirst();
        if (optionalSubject.isEmpty()) {
            vkBot.sendMessageTo(userId, "Неправильный номер предмета");
            return;
        }
        final var oldSubject = optionalSubject.get();

        Subject newSubject = group.getLkParser().getNewSubject(oldSubject, group);

        newSubject.setId(subjectIndex);
        newSubject.getDocumentNames()
                .removeAll(oldSubject.getDocumentNames());

        if (newSubject.isNotEmpty()) {
            vkBot.sendLongMessageTo(userId,
                    ReportsMaker.getSubjects(List.of(newSubject), null));
        } else {
            vkBot.sendMessageTo(userId,
                    "Нет новой информации по предмету " + newSubject.getName());
        }
    }

    private static void newGroupLoginFailedMessages (Integer userId) {
        vkBot.sendMessageTo(userId,
                "Что-то пошло не так. \n" +
                "Либо твои логин и пароль неправильные, либо я неправильно их прочитал");
    }

    private static void loginFailedMessages(Integer userId, Group group) {
        String groupName = group.getName();
        LoggedUser loggedUser = group.getLoggedUser();

        final boolean isNotLoggedUser = !loggedUser.is(userId);
        if (isNotLoggedUser) {
            vkBot.sendMessageTo(userId,
                    "➡ Мне не удалось проверить данные твоей группы. " +
                            "Человек, вошедший от имени группы, не сказал мне новый пароль от ЛК или вышел. " +
                            "Я скажу ему об этом сам, если он не вышел.");
            groupsRepository.addToIntegerArray(groupName, "loginWaitingUsers", userId);
            group.getLoginWaitingUsers().add(userId);
        }
        rememberUpdateAuthDataMessage(group, loggedUser, groupName, isNotLoggedUser);
    }

    public static void rememberUpdateAuthDataMessage (Group group,
                                                       @NonNull LoggedUser loggedUser,
                                                       @NonNull String groupName,
                                                       boolean anywayRemember) {
        if (anywayRemember || !loggedUser.isUpdateAuthDataNotified()) {
            vkBot.sendMessageTo(loggedUser.getId(),
                    "➡ Похоже ты забыл сказать мне новый пароль после его обновления в ЛК." +
                            "Скажи мне новые данные для входа так " +
                            AUTH_COMMAND);

            groupsRepository.updateField(groupName, "loggedUser.updateAuthDataNotified", true);
            loggedUser.setUpdateAuthDataNotified(true);
        }
    }

    private static void onLoginMessages (Integer userId, @NonNull String groupName, String messageText) {
        final String[] chunks = messageText.split("\n");
        String login = chunks[1];
        String password = chunks[2];

        vkBot.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
        final LkParser lkParser = new LkParser();
        if (!lkParser.login(new AuthenticationData(login, password))) {
            newGroupLoginFailedMessages(userId);
            return;
        }
        vkBot.sendMessageTo(userId, "Я успешно зашел в твой ЛК");
        //vkBotService.deleteLastMessage(message);

        groupName = actualizeGroupName(userId, groupName, lkParser);

        var optionalGroup = Optional.ofNullable(groupByGroupName.get(groupName));
        if (optionalGroup.isEmpty()) {
            newGroupLoggedMessages(userId, groupName, login, password, lkParser);
            return;
        }
        final var group = optionalGroup.get();
        if (!group.isLoggedBefore())
            return;

        if (!group.isNotLoggedNow() && !group.getLoggedUser().is(userId)) {
            groupAlreadyRegisteredMessage(userId);
            return;
        }

        final LoggedUser loggedUser = new LoggedUser()
                .setId(userId)
                .setAuthData(cipherService.encrypt(login, password))
                .setUpdateAuthDataNotified(false);

        groupsRepository.updateLoggedUser(group.getName(), loggedUser);
        group.setLoggedUser(loggedUser);
        group.getUsers().add(new GroupUser(userId));

        groupsRepository.moveLoginWaitingUsersToUsers(group.getName());
        vkBot.sendMessageTo(group.getLoginWaitingUsers(),
                "Человек из твоей группы зашел в ЛК через меня");
    }

    private static String actualizeGroupName(Integer userId, String groupName, LkParser lkParser) {
        final var optionalLkGroupName = lkParser.getGroupName();
        if (optionalLkGroupName.isEmpty())
            return groupName;

        final var lkGroupName = optionalLkGroupName.get();
        if (lkGroupName.equals(groupName))
            return groupName;

        vkBot.sendMessageTo(userId,
                "\uD83D\uDD34 Я поменял имя введенной тобой группы "+ groupName +" на: "+lkGroupName+
                        ", чтобы избежать неприятных ситуаций. \uD83D\uDD34");
        groupNameByUserId.replace(userId, lkGroupName);
        return lkGroupName;
    }

    private static void newGroupLoggedMessages(Integer userId, @NonNull String groupName,
                                               String login, String password, LkParser lkParser) {
        vkBot.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
        vkBot.sendMessageTo(userId,
                "Ой, извини, случайно вырвалось)\n" +
                        "➡ Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК. " +
                        "Тебе нужно просто позвать их пообщаться со мной. " +
                        "Но позволь я сначала проверю твой ЛК...");

        List<Subject> newSubjects = lkParser.getSubjectsFirstTime(actualSemester);
        final var newGroup = new Group(groupName)
                .setLoggedUser(new LoggedUser().setId(userId).setAuthData(cipherService.encrypt(login, password)))
                .setSubjects(newSubjects)
                .setLastCheckDate(new Date());
        newGroup.getUsers().add(new GroupUser(userId));

        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(actualSemester);
        newGroup.setLkIds(
                lkIds.get(LkParser.SEMESTER_ID),
                lkIds.get(LkParser.GROUP_ID),
                lkIds.get(LkParser.CONTINGENT_ID)
        );

        newUserSubjectsListMessage(userId, newGroup);
        vkBot.sendLongMessageTo(userId, "Результат последнего обновления: \n" +
                ReportsMaker.getSubjects(newSubjects, newGroup.getNextCheckDate()));

        newGroup.setTimetable(lkParser.parseTimetable(newGroup.getLkSemesterId(), newGroup.getLkId()));

        groupsRepository.insert(newGroup);
        groupByGroupName.put(groupName, newGroup);
    }

    public static void actualizeWeekType() throws AuthenticationException {
        Optional.ofNullable(groupByGroupName.get("ПИ-19-1"))
                .ifPresentOrElse(group -> {
                    final LkParser lkParser = new LkParser();
                    if (lkParser.login(cipherService.decrypt(group.getLoggedUser().getAuthData())))
                        isActualWeekWhite = lkParser.parseWeekType(group.getLkSemesterId());
                    else {
                        vkBot.sendMessageTo(APP_ADMIN_ID, "Срочно скажи мне свой новый пароль.");
                        throw new AuthenticationException("Не удалось зайти в группу для актуализации типа недели.");
                    }

                }, () -> vkBot.sendMessageTo(APP_ADMIN_ID,
                "Не удалось загрузить твою группу. Войди и перезапусти бота."));
    }

    // TODO Новый функционал и оптимизация
    //  ответ на нецензурные и похвальные слова
    //  Получить 1к бесплатных часов на heroku (пока что не вышло)
    //  Удаление сообщения с данными входа (пока что не получилось, хотя согласно докам можно)

    // TODO Для массового распространения бота:
    //  оптимизация запросов к лк через очередь
}
