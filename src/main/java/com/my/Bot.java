package com.my;

import com.my.exceptions.ApplicationStopNeedsException;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import com.my.exceptions.LoginNeedsException;
import com.my.models.*;
import com.my.services.Answer;
import com.my.services.CipherService;
import com.my.services.lk.LkParser;
import com.my.services.text.KeyboardLayoutConverter;
import com.my.services.vk.KeyboardService;
import com.my.services.vk.VkBotService;
import com.my.threads.PlannedScheduleSending;
import com.my.threads.PlannedSubjectsUpdate;
import com.vk.api.sdk.objects.messages.Message;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Bot {
    static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    static final VkBotService vkBot = VkBotService.getInstance();
    static CipherService cipherService;

    static final Map<Integer, UserStatus> userStatuses = new HashMap<>();

    static final Map<Integer, String> groupNameByUserId = new HashMap<>();
    @Getter
    static final Map<String, Group> groupByGroupName = new HashMap<>();

    static final int APP_ADMIN_ID = 173315241;
    public static final String APPLICATION_STOP_TEXT = "I WANT TO STOP THE APPLICATION";

    @Getter @Setter
    private static volatile String actualSemester;
    private static boolean isActualWeekWhite;


    private static final String AUTH_COMMAND =
            "(рекомендую сразу удалить это сообщение):\n" +
                    "Хочу войти в ЛК\n" +
                    "Мой_логин\n" +
                    "Мой_пароль";

    private static PlannedSubjectsUpdate plannedSubjectsUpdate;
    private static PlannedScheduleSending plannedScheduleSending;

    public Bot () {}

    public void startProcessing() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        cipherService = CipherService.getInstance();
        actualSemester = Utils.getSemesterName();

        fillCaches();
        actualizeWeekType();

        plannedSubjectsUpdate = new PlannedSubjectsUpdate();
        plannedScheduleSending = new PlannedScheduleSending();
        vkBot.setOnline(true);
        vkBot.sendMessageTo(APP_ADMIN_ID,
                "APP STARTED.\nTO STOP TYPE: "+ APPLICATION_STOP_TEXT);

        while (true)
            vkBot.getNewMessages()
                    .map(Bot::stopAppOnSpecialMessage)
                    .forEach(Bot::processMessage);
    }

    public void endProcessing() {
        groupByGroupName.values().forEach(group -> group.getLkParser().logout());
        vkBot.setOnline(false);
        plannedSubjectsUpdate.interrupt();
        plannedScheduleSending.interrupt();
        vkBot.sendMessageTo(APP_ADMIN_ID, Answer.WARNING_APP_STOPPED);
    }

    private static void fillCaches() {
        groupsRepository.findAll()
                .forEach(group -> {
                    groupByGroupName.put(group.getName(), group);
                    group.setLkParser(new LkParser());
                });

        groupByGroupName.values().forEach(group -> {
            if (group.isNotLoggedBefore())
                return;

            final var groupName = group.getName();
            group.getUsers()
                    .forEach(user -> groupNameByUserId.put(user.getId(), groupName));
            group.getLoginWaitingUsers()
                    .forEach(userId -> groupNameByUserId.put(userId, groupName));
        });
    }

    private static void processMessage(Message message) {
        try {
            Thread.sleep(10); // Чтобы не ддосили ЛК
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        final Integer userId = message.getFromId();
        final var messageText = message.getText();
        CompletableFuture.runAsync(() -> {
            try {
                replyToMessage(userId, messageText);

            } catch (AuthenticationException e) {
                loginFailedMessages(userId, groupByGroupName.get(groupNameByUserId.get(userId)));

            } catch (LoginNeedsException e) {
                login(groupByGroupName.get(groupNameByUserId.get(userId)));
                replyToMessage(userId, messageText);

            } catch (LkNotRespondingException e) {
                vkBot.sendMessageTo(userId, Answer.LK_NOT_RESPONDING);
            } catch (Exception e) {
                vkBot.sendMessageTo(userId, Answer.NOT_UNDERSTAND_YOU_OR_MISTAKE);
                e.printStackTrace();
            }
        });
    }

    private static Message stopAppOnSpecialMessage(Message message) {
        if (message.getFromId().equals(APP_ADMIN_ID) &&
                message.getText().equals(APPLICATION_STOP_TEXT))
            throw new ApplicationStopNeedsException();
        else return message;
    }

    private static void replyToMessage(Integer userId, String messageText) {
        if (tryReplyTo(userId, messageText)) return;
        messageText = ParserUtils.capitalize(messageText);
        if (tryReplyTo(userId, messageText)) return;
        messageText = KeyboardLayoutConverter.convertFromEngIfNeeds(messageText);
        tryReplyTo(userId, messageText);
    }

    private static boolean tryReplyTo(Integer userId, String messageText) {
        // TODO если добавить описание ошибок и пожелания, то изменить условие
        if (messageText.length() > 100) {
            vkBot.sendMessageTo(userId, Answer.YOUR_MESSAGE_IS_SPAM);
            return true;
        }
//        SpecialWordsFinder.findSpecialWords(userId, messageText);

        Command command = new Command(messageText);

        if (replyForNewUser(userId, command)) return true;

        String groupName = groupNameByUserId.get(userId);
        final Group group = groupByGroupName.get(groupName);
        messageText = messageText.toLowerCase(Locale.ROOT);
        command = new Command(messageText);

        if (replyForGroupWroteUser(userId, command, group)) return true;

        if (!group.containsUser(userId) && command.is(Command.VERIFICATION_CODE)) {
            addUserByVerificationCode(group, userId, Utils.tryParseInteger(messageText));
            return true;
        }

        final LoggedUser loggedUser = group.getLoggedUser();

        if (loggedUser == null) {
            vkBot.sendMessageTo(userId, Answer.LEADER_EXITED);
            return true;
        }

        if (command.is(Command.CHANGE_UPDATE_INTERVAL)) {
            if (loggedUser.is(userId)) {
                final var minutes = command.parseNumbers().get(0);
                if (5 <= minutes && minutes <= 20160) {
                    final long newUpdateInterval = minutes * 60 * 1000;

                    groupsRepository.updateField(groupName,"updateInterval", newUpdateInterval);
                    group.setUpdateInterval(newUpdateInterval);

                    group.setUpdateInterval(newUpdateInterval);
                    vkBot.sendMessageTo(userId, Answer.INTERVAL_CHANGED + "\n" +
                            Answer.getNextUpdateDateText(group.getNextCheckDate()));

                } else vkBot.sendMessageTo(userId, Answer.WRONG_INTERVAL);

            } else vkBot.sendMessageTo(userId, Answer.COMMAND_FOR_ONLY_LEADER);

        } else if (command.is(Command.CHANGE_SILENT_TIME)) {
            if (loggedUser.is(userId)) {
                final List<Integer> integers = command.parseNumbers();
                final var startHour = integers.get(0);
                final var endHour = integers.get(1);
                if (0 <= startHour && startHour <= 23 && 0 <= endHour && endHour <= 23) {
                    groupsRepository.updateSilentMode(groupName, startHour, endHour);
                    group.setSilentModeStart(startHour).setSilentModeEnd(endHour);
                    vkBot.sendMessageTo(userId, Answer.SILENT_TIME_CHANGED);

                } else vkBot.sendMessageTo(userId, Answer.WRONG_SILENT_TIME);

            } else vkBot.sendMessageTo(userId, Answer.COMMAND_FOR_ONLY_LEADER);

        } else if (command.is(Command.GET_SUBJECT)) {
            login(group);
            getActualSubjectMessage(userId, group, Utils.tryParseInteger(command.getValue()));

        } else if (command.is(Command.GET_SUBJECTS))
            vkBot.sendMessageTo(userId, Answer.getSubjectsNames(group.getSubjects()));

        else if (command.is(Command.COMMANDS)) {
            vkBot.sendMessageTo(userId,
                    KeyboardService.getCommands(userId, group),
                    Answer.getUserCommands(userId, group));

        } else if (command.is(Command.WITH_EVERYDAY_SCHEDULE))
            changeUserSchedulingEnable(userId, group, true);

        else if (command.is(Command.WITHOUT_EVERYDAY_SCHEDULE))
            changeUserSchedulingEnable(userId, group, false);

        else if (command.is(Command.WITHOUT_EMPTY_REPORTS))
            changeLoggedUserNotifying(userId, group, false);

        else if (command.is(Command.WITH_EMPTY_REPORTS))
            changeLoggedUserNotifying(userId, group, true);

        else if (command.is(Command.FORGET_ME)) {
            if (loggedUser.is(userId))
                vkBot.sendMessageTo(userId, Answer.LEADER_FORGET_ME_NOTICE);
            else
                vkBot.sendMessageTo(userId, Answer.USER_FORGET_ME_NOTICE);

        } else if (command.is(Command.FINALLY_FORGET_ME)) {
            finallyForgetUser(userId, groupName, group, loggedUser);

        } else {
            vkBot.sendMessageTo(userId, "Я не понял тебя");
            return false;
        }
        return true;
    }



    private static boolean replyForNewUser(Integer userId, Command command) {
        String groupName = command.parseGroupName();
        if (groupName != null) { // Я из ПИ-19-1 и тд.
            newUserGroupCheck(userId, groupName);
            return true;

        } else if (!groupNameByUserId.containsKey(userId)) {
            vkBot.sendMessageTo(userId, Answer.WRITE_WHICH_GROUP);
            return true;
        }
        return false;
    }

    private static boolean replyForGroupWroteUser(Integer userId, Command command, Group group) {
        if (command.is(Command.WANT_TO_LOGIN)) {
            if (group == null || !group.isLoggedNow()) {
                vkBot.unsetKeyboard();
                vkBot.sendMessageTo(userId, Answer.INPUT_CREDENTIALS);
            } else
                groupAlreadyRegisteredMessage(userId, group);
            return true;

        } else if (command.is(Command.CHANGE_GROUP)) {
            if (group != null && group.containsUser(userId)) {
                vkBot.sendMessageTo(userId, Answer.TYPE_FORGET_ME);
            } else {
                vkBot.unsetKeyboard();
                groupNameByUserId.remove(userId);
                vkBot.sendMessageTo(userId, Answer.WRITE_WHICH_GROUP);
            }
            return true;

        } else if (command.is(Command.CREDENTIALS)) {
            onLoginMessages(userId, groupNameByUserId.get(userId), command.getValue());
            return true;

        } else if (group == null) {
            vkBot.sendMessageTo(userId, Answer.GROUP_NOT_LOGGED_AND_YOU_CAN_LOGIN);
            return true;

        } else return false;
    }

    public static boolean login(Group group) {
        return group.getLkParser()
                .login(cipherService.decrypt(group.getLoggedUser().getAuthData()));
    }

    private static void addUserByVerificationCode (Group group, Integer userId, Integer code) {
        if (code == null) {
            vkBot.sendMessageTo(userId, Answer.TYPE_CODE_INITIALLY);
            return;
        }
        final UserToVerify userToVerify = new UserToVerify(userId, code);

        if (!groupsRepository.moveVerifiedUserToUsers(group.getName(), userToVerify)) {
            vkBot.sendMessageTo(userId, Answer.WRONG_CODE);
            return;
        }
        group.moveVerifiedUserToUsers(userToVerify);
        vkBot.sendMessageTo(group.getLoggedUser().getId(), Answer.getUserAdded(vkBot.getUserName(userId)));
        vkBot.sendMessageTo(userId, Answer.getYouAddedToGroup(group.getName()));
        newUserSubjectsListMessage(userId, group);
    }

    private static void newUserGroupCheck (Integer userId, String groupName) {
        if (groupNameByUserId.containsKey(userId)) {
            vkBot.sendMessageTo(userId, Answer.YOU_ALREADY_WRITE_YOUR_GROUP);
            return;
        } else
            groupNameByUserId.put(userId, groupName);

        final var optionalGroup = Optional.ofNullable(groupByGroupName.get(groupName));
        if (optionalGroup.isEmpty()) {
            vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_1, Answer.YOUR_GROUP_IS_NEW);
            return;
        }

        Group group = optionalGroup.get();

        if (!group.isLoggedNow()) {
            vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_1, Answer.FOR_NEW_USER_LEADER_EXITED);
            return;
        }

        vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_2, Answer.I_KNOW_THIS_GROUP);
        startUserVerification(userId, group);
    }

    private static void startUserVerification(Integer userId, Group group) {

        final Integer verificationCode = Utils.generateVerificationCode();
        final UserToVerify userToVerify = new UserToVerify(userId, verificationCode);

        if (groupsRepository.addUserToUsersToVerify(group.getName(), userToVerify)) {
            group.getUsersToVerify().add(userToVerify);

            vkBot.sendMessageTo(group.getLoggedUser().getId(),
                    Answer.getVerificationCode(vkBot.getUserName(userId), verificationCode));
            vkBot.sendMessageTo(userId, Answer.getSendMeCode(vkBot.getUserName(group.getLoggedUser().getId())));
        } else
            vkBot.sendMessageTo(userId, Answer.I_ALREADY_SENT_CODE);
    }

    private static void newUserSubjectsListMessage (Integer userId, Group group) {
        vkBot.sendMessageTo(userId,
                Answer.getNowICanSendSubjectsInfo(group.getSubjects()));

        group.getUsers().add(new GroupUser(userId));
        vkBot.sendMessageTo(userId, KeyboardService.getCommands(userId, group),
                Answer.getNowYouCanUseCommands(userId, group));
    }

    private static void groupAlreadyRegisteredMessage(Integer userId, Group group) {
        if (!group.containsUser(userId)) {
            vkBot.sendMessageTo(userId, Answer.YOU_LATE_LOGIN);
            startUserVerification(userId, group);
        } else
            vkBot.sendMessageTo(userId, Answer.CANNOT_CHANGE_LEADER);
    }

    private static void changeUserSchedulingEnable(Integer userId, Group group, boolean isEnable) {
        groupsRepository.updateUserScheduling(group.getName(), userId, isEnable);
        group.getUsers().stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst().get()
                .setEverydayScheduleEnabled(isEnable);

        vkBot.sendMessageTo(userId, KeyboardService.getCommands(userId, group), Answer.OK);
        if (isEnable) {
            final String dayScheduleReport = getDayScheduleReport(Utils.mapWeekDayFromCalendar(), group);
            if (!dayScheduleReport.isEmpty())
                vkBot.sendMessageTo(userId, Answer.getTodaySchedule(dayScheduleReport));
        }
    }

    public static String getDayScheduleReport(int weekDay, Group group) {
        return Answer.getDaySchedule(isActualWeekWhite ?
                        group.getTimetable().getWhiteWeekDaySubjects().get(weekDay) :
                        group.getTimetable().getGreenWeekDaySubjects().get(weekDay),
                isActualWeekWhite);
    }

    private static void changeLoggedUserNotifying (Integer userId, Group group, boolean isEnable) {
        if (group.getLoggedUser().is(userId)) {
            groupsRepository.updateField(group.getName(), "loggedUser.alwaysNotify", isEnable);
            group.getLoggedUser().setAlwaysNotify(isEnable);
            vkBot.sendMessageTo(userId, KeyboardService.getLoggedUserCommands(group), Answer.OK);
        } else
            vkBot.sendMessageTo(userId, Answer.COMMAND_FOR_ONLY_LEADER);
    }

    private static void getActualSubjectMessage (Integer userId, Group group, Integer subjectIndex) {
        final var optionalSubject = group.getSubjects().stream()
                .filter(subject1 -> subject1.getId() == subjectIndex)
                .findFirst();
        if (optionalSubject.isEmpty()) {
            vkBot.sendMessageTo(userId, Answer.WRONG_SUBJECT_NUMBER);
            return;
        }
        final var oldSubject = optionalSubject.get();

        Subject newSubject = group.getLkParser().getNewSubject(oldSubject, group);

        newSubject.setId(subjectIndex);
        newSubject.getDocumentNames()
                .removeAll(oldSubject.getDocumentNames());

        if (newSubject.isNotEmpty()) {
            vkBot.sendLongMessageTo(userId,
                    Answer.getSubjects(List.of(newSubject), null));
        } else {
            vkBot.sendMessageTo(userId, Answer.getNoNewSubjectInfo(newSubject.getName()));
        }
    }

    private static void loginFailedMessages(Integer userId, Group group) {
        String groupName = group.getName();
        LoggedUser loggedUser = group.getLoggedUser();

        final boolean isNotLoggedUser = !loggedUser.is(userId);
        if (isNotLoggedUser) {
            vkBot.sendMessageTo(userId,
                    Answer.CANNOT_LOGIN);
            groupsRepository.addToIntegerArray(groupName, "loginWaitingUsers", userId);
            group.getLoginWaitingUsers().add(userId);
        }
        rememberUpdateAuthDataMessage(groupName, loggedUser, isNotLoggedUser);
    }

    public static void rememberUpdateAuthDataMessage(@NonNull String groupName,
                                                     @NonNull LoggedUser loggedUser,
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

        final String[] chunks = messageText.split(" ");
        String login = chunks[0];
        String password = chunks[1];

        Group group = groupByGroupName.get(groupName);
        if (group == null) { // Если не зарегана
            loginNewGroup(userId, groupName, login, password);
            return;
        }

        // Если зарегана сейчас и другой пользователь хочет войти
        if (group.isLoggedNow() && !group.getLoggedUser().is(userId)) {
            groupAlreadyRegisteredMessage(userId, group);
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
                Answer.getNewLeaderIs(vkBot.getUserName(userId)));
        group.setLoginWaitingUsers(new HashSet<>());
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

    private static void loginNewGroup(Integer userId, @NonNull String groupName, String login, String password) {

        vkBot.sendMessageTo(userId, Answer.TRY_TO_LOGIN);
        final LkParser lkParser = new LkParser();
        if (!lkParser.login(new AuthenticationData(login, password))) {
            vkBot.sendMessageTo(userId, Answer.LOGIN_FAILED);
            return;
        }
        vkBot.sendMessageTo(userId, Answer.SUCCESSFUL_LOGIN);
        //vkBotService.deleteLastMessage(message);

        groupName = actualizeGroupName(userId, groupName, lkParser);

        List<Subject> newSubjects = lkParser.getSubjectsFirstTime(actualSemester);
        final var newGroup = new Group(groupName)
                .setLoggedUser(new LoggedUser().setId(userId).setAuthData(cipherService.encrypt(login, password)))
                .setSubjects(newSubjects)
                .setLastCheckDate(new Date());
        newGroup.getUsers().add(new GroupUser(userId));
        newGroup.setLkParser(lkParser);

        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(actualSemester);
        newGroup.setLkIds(
                lkIds.get(LkParser.SEMESTER_ID),
                lkIds.get(LkParser.GROUP_ID),
                lkIds.get(LkParser.CONTINGENT_ID)
        );

        newUserSubjectsListMessage(userId, newGroup);
        vkBot.sendLongMessageTo(userId, "Результат последнего обновления: \n" +
                Answer.getSubjects(newSubjects, newGroup.getNextCheckDate()));

        newGroup.setTimetable(lkParser.parseTimetable(newGroup.getLkSemesterId(), newGroup.getLkId()));

        groupsRepository.insert(newGroup);
        groupByGroupName.put(groupName, newGroup);

        vkBot.sendMessageTo(userId, Answer.I_CAN_SEND_INFO);
    }

    private static void finallyForgetUser(Integer userId, String groupName, Group group, LoggedUser loggedUser) {
        vkBot.unsetKeyboard();
        if (loggedUser.is(userId)) {
            vkBot.sendMessageTo(userId, Answer.AFTER_LEADER_FORGETTING);

            groupsRepository.removeLoggedUser(groupName, loggedUser.getId());
            group.removeLoggedUser(loggedUser.getId());

        } else
            vkBot.sendMessageTo(userId, Answer.AFTER_USER_FORGETTING);
        groupsRepository.removeUserFromGroup(groupName, userId);
        groupNameByUserId.remove(userId);
        group.removeUserFromGroup(userId);
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
}
