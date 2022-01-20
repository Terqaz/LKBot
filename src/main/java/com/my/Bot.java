package com.my;

import com.my.exceptions.ApplicationStopNeedsException;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.FileLoadingException;
import com.my.exceptions.LkNotRespondingException;
import com.my.models.*;
import com.my.repositories.GroupsRepository;
import com.my.services.Answer;
import com.my.services.CipherService;
import com.my.services.Command;
import com.my.services.lk.LkParser;
import com.my.services.vk.KeyboardService;
import com.my.services.vk.VkBotService;
import com.my.threads.PlannedScheduleSending;
import com.my.threads.PlannedSubjectsUpdate;
import com.vk.api.sdk.objects.messages.Message;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Log4j2
public class Bot {
    static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    static final VkBotService vkBot = VkBotService.getInstance();
    static CipherService cipherService;

    static final Map<Integer, String> groupNameByUserId = new HashMap<>();
    @Getter
    static final Map<String, Group> groupByGroupName = new HashMap<>();

    public static final int APP_ADMIN_ID1 = 173315241;
    public static final int APP_ADMIN_ID2 = 272910732;
    public static final List<Integer> ADMINS = List.of(APP_ADMIN_ID1, APP_ADMIN_ID2);

    @Getter
    private static boolean isActualWeekWhite = true;

    private static PlannedSubjectsUpdate plannedSubjectsUpdate;
    private static PlannedScheduleSending plannedScheduleSending;

    public Bot () {}

    public void startProcessing() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        cipherService = CipherService.getInstance();

        fillCaches();
        actualizeWeekType();

        plannedSubjectsUpdate = new PlannedSubjectsUpdate();
        plannedScheduleSending = new PlannedScheduleSending();
        vkBot.sendMessageTo(APP_ADMIN_ID1,
                "APP STARTED.\nTO STOP TYPE: "+ Command.APPLICATION_STOP);

        while (true) // Цикл обработки сообщений
            vkBot.getNewMessages()
                    .forEach(Bot::processMessage);
    }

    public void endProcessing() {
        groupByGroupName.values().forEach(group -> group.getLkParser().logout());
        vkBot.setOnline(false);
        plannedSubjectsUpdate.interrupt();
        plannedScheduleSending.interrupt();
        vkBot.sendMessageTo(APP_ADMIN_ID1, Answer.WARNING_APP_STOPPED);
    }

    public static void manualChangeWeekType() {
        isActualWeekWhite = !isActualWeekWhite;
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
            Thread.sleep(20); // Чтобы не ддосили ЛК
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

            } catch (LkNotRespondingException e) {
                vkBot.sendMessageTo(userId, Answer.LK_NOT_RESPONDING);

            } catch (ApplicationStopNeedsException e) {
                throw e;

            } catch (Exception e) {
                vkBot.sendMessageTo(userId, Answer.I_BROKEN);
                vkBot.sendMessageTo(ADMINS, Answer.getForAdminsIBroken(userId, e));
                e.printStackTrace();
            }
        });
    }

    private static void replyToMessage(Integer userId, String messageText) {
        Command command = new Command(messageText);
        if (ADMINS.contains(userId)) {
            if (command.is(Command.APPLICATION_STOP))
                throw new ApplicationStopNeedsException();

            else if (command.is(Command.WEEK_TYPE)) {
                isActualWeekWhite = command.getValue().equals("белая");
                vkBot.sendMessageTo(userId, "Я изменил тип недели");
                return;

            } else if (command.is(Command.WHICH_WEEK_TYPE)) {
                vkBot.sendMessageTo(userId, "Сейчас " + (isActualWeekWhite ? "белая" : "зеленая") + " неделя");
                return;
            }
        }

        // TODO если добавить описание ошибок и пожелания, то изменить условие
        if (messageText.length() > 100) {
            vkBot.sendMessageTo(userId, Answer.YOUR_MESSAGE_IS_SPAM);
            return;
        }
//        SpecialWordsFinder.findSpecialWords(userId, messageText);

        if (replyForNewUser(userId, command)) return;

        String groupName = groupNameByUserId.get(userId);
        final Group group = groupByGroupName.get(groupName);

        if (replyForGroupWroteUser(userId, command, group)) return;

        final LoggedUser loggedUser = group.getLoggedUser();

        if (command.is(Command.FORGET_ME)) {
            if (loggedUser != null && loggedUser.is(userId))
                vkBot.sendMessageTo(userId, Answer.LEADER_FORGET_ME_NOTICE);
            else
                vkBot.sendMessageTo(userId, Answer.USER_FORGET_ME_NOTICE);

        } else if (command.is(Command.FINALLY_FORGET_ME))
            finallyForgetUser(userId, groupName, group, loggedUser);

        else if (loggedUser == null) {
            vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_3, Answer.LEADER_EXITED);
            groupsRepository.addToIntegerArray(groupName, "loginWaitingUsers", userId);
            group.getLoginWaitingUsers().add(userId);
        }

        else if (!group.containsUser(userId) && command.is(Command.VERIFICATION_CODE))
            addUserByVerificationCode(group, userId, Utils.tryParseInteger(messageText));

        else if (command.is(Command.GET_SUBJECT_DOCUMENTS))
            getSubjectDocuments(userId, command, group);

        else if (command.is(Command.GET_SUBJECT_DOCUMENT))
            getSubjectDocument(userId, command, group);

        else if (command.is(Command.GET_SUBJECTS)) {
            final List<Subject> subjects = group.getSubjects();
            if (Utils.isNullOrEmptyCollection(subjects))
                vkBot.sendLongMessageTo(userId, Answer.WAIT_WHEN_SUBJECTS_LOADED);
            else
                vkBot.sendMessageTo(userId, Answer.getSubjectsNames(subjects));

        } else if (command.is(Command.COMMANDS))
            vkBot.sendMessageTo(userId,
                KeyboardService.getCommands(userId, group),
                Answer.getUserCommands(userId, group));

        else if (command.is(Command.WITH_EVERYDAY_SCHEDULE))
            changeUserSchedulingEnable(userId, group, true);

        else if (command.is(Command.WITHOUT_EVERYDAY_SCHEDULE))
            changeUserSchedulingEnable(userId, group, false);

        else if (command.is(Command.CHANGE_SILENT_TIME)) {
            if (loggedUser.is(userId))
                changeSilentTime(userId, command, group);
            else vkBot.sendMessageTo(userId, Answer.COMMAND_FOR_ONLY_LEADER);
        } else
            vkBot.sendMessageTo(userId, "Я не понял твою команду");
    }

    private static void getSubjectDocuments(Integer userId, Command command, Group group) {
        final List<Subject> subjects = group.getSubjects();
        if (Utils.isNullOrEmptyCollection(subjects)) {
            vkBot.sendLongMessageTo(userId, Answer.WAIT_WHEN_SUBJECTS_LOADED);
            return;
        }

        final var subjectId = command.parseNumbers().get(0);
        group.getSubjectById(subjectId)
                .ifPresentOrElse(
                        subject -> vkBot.sendMessageTo(userId, Answer.getSubjectDocuments(subject)),
                        () -> vkBot.sendMessageTo(userId, Answer.WRONG_SUBJECT_NUMBER));
    }

    private static void getSubjectDocument(Integer userId, Command command, Group group) {
        if (Utils.isNullOrEmptyCollection(group.getSubjects())) {
            vkBot.sendLongMessageTo(userId, Answer.WAIT_WHEN_SUBJECTS_LOADED);
            return;
        }

        final List<Integer> integers = command.parseNumbers();
        final var subjectId = integers.get(0);
        final var documentId = integers.get(1);

        group.getSubjectById(subjectId).ifPresentOrElse(
                subject -> {
                    final String subjectName = subject.getName();

                    boolean isFromMaterials = true;
                    LkDocument document = subject.getMaterialsDocumentById(documentId);
                    if (document == null) {
                        document = subject.getMessageDocumentById(documentId);
                        isFromMaterials = false;
                    }

                    if (document == null) {
                        vkBot.sendMessageTo(userId, Answer.WRONG_DOCUMENT_NUMBER);
                        return;

                    } else if (document.getUrl() != null) {
                        vkBot.sendMessageTo(userId, Answer.getDocumentUrl(subjectName, document.getName(), document.getUrl()));
                        return;

                    } else if (document.getVkAttachment() != null) {
                        vkBot.sendMessageTo(userId, document.getVkAttachment(),
                                    Answer.getDocument(subjectName, document.getName(), document.getIsExtChanged()));
                        return;
                    }

                    Bot.login(group);
                    Path path;
                    try {
                        if (isFromMaterials)
                            path = group.getLkParser().loadMaterialsFile(document);
                        else
                            path = group.getLkParser().loadMessageFile(document);

                    } catch (Exception e) {
                        e.printStackTrace();
                        vkBot.sendMessageTo(userId, Answer.NO_ACCESS_TO_FILE);
                        return;
                    }

                    path = Utils.correctFileExt(path);
                    document.setIsExtChanged(Utils.isExtensionChanged(path));

                    final File file = path.toFile();
                    try {
                        document.setVkAttachment(vkBot.sendMessageTo(userId, file,
                                Answer.getDocument(subjectName, document.getName(), document.getIsExtChanged())));

                        groupsRepository.updateDocumentVkAttachment(group.getName(), subjectId, documentId, isFromMaterials, document.getVkAttachment());

                    } catch (FileLoadingException e) {
                        vkBot.sendMessageTo(ADMINS, Answer.getForAdminsIBroken(0, e));
                        vkBot.sendMessageTo(userId, Answer.VK_LOAD_FILE_FAILED);
                        e.printStackTrace();
                    }
                    try {
                        FileUtils.forceDelete(file.getParentFile());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                },
                () -> vkBot.sendMessageTo(userId, Answer.WRONG_SUBJECT_NUMBER));
    }

    private static void changeSilentTime(Integer userId, Command command, Group group) {
        final List<Integer> integers = command.parseNumbers();
        final var startHour = integers.get(0);
        final var endHour = integers.get(1);
        if (0 <= startHour && startHour <= 23 && 0 <= endHour && endHour <= 23) {
            groupsRepository.updateSilentMode(group.getName(), startHour, endHour);
            group.setSilentModeStart(startHour).setSilentModeEnd(endHour);
            vkBot.sendMessageTo(userId, Answer.SILENT_TIME_CHANGED);

        } else vkBot.sendMessageTo(userId, Answer.WRONG_SILENT_TIME);
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
        if (command.is(Command.WANT_TO_LOGIN_PATTERN)) {
            if (group == null || !group.hasLeader()) {
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

    private static boolean tryLogin(Integer userId, LkParser lkParser, String login, String password) {
        vkBot.sendMessageTo(userId, Answer.TRY_TO_LOGIN);
        try {
            lkParser.login(new AuthenticationData(login, password));
        } catch (AuthenticationException e) {
            vkBot.sendMessageTo(userId, Answer.LOGIN_FAILED);
            return false;
        }
        vkBot.sendMessageTo(userId, Answer.SUCCESSFUL_LOGIN);
        return true;
    }

    public static void login(Group group) {
        group.getLkParser().login(cipherService.decrypt(group.getLoggedUser().getAuthData()));
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

        } else groupNameByUserId.put(userId, groupName);

        final Group group = groupByGroupName.get(groupName);

        if (group == null)
            vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_1, Answer.YOUR_GROUP_IS_NEW);

        else if (!group.hasLeader())
            vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_1, Answer.FOR_NEW_USER_LEADER_EXITED);

        else {
            vkBot.sendMessageTo(userId, KeyboardService.KEYBOARD_2, Answer.I_KNOW_THIS_GROUP);
            startUserVerification(userId, group);
        }
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
        } else {
            if (group.getLoggedUser().is(userId))
                vkBot.sendMessageTo(userId, Answer.YOU_ALREADY_LOGGED);

            else vkBot.sendMessageTo(userId, Answer.CANNOT_CHANGE_LEADER);
        }
    }

    private static void changeUserSchedulingEnable(Integer userId, Group group, boolean isEnable) {
        groupsRepository.updateUserScheduling(group.getName(), userId, isEnable);
        group.getUsers().stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst().get()
                .setEverydayScheduleEnabled(isEnable);
        vkBot.sendMessageTo(userId, KeyboardService.getCommands(userId, group), Answer.OK);

        if (isEnable) {
            String dayScheduleReport =
                    Answer.getDaySchedule(group, Utils.getThisWeekDayIndex(), isActualWeekWhite);

            if (!dayScheduleReport.isEmpty())
                vkBot.sendMessageTo(userId, Answer.getTodaySchedule(dayScheduleReport));
            else
                vkBot.sendMessageTo(userId, Answer.TODAY_EMPTY_SCHEDULE);
        }
    }

    private static void loginFailedMessages(Integer userId, Group group) {
        String groupName = group.getName();
        LoggedUser loggedUser = group.getLoggedUser();

        final boolean isNotLoggedUser = !loggedUser.is(userId);
        rememberUpdateAuthDataMessage(groupName, loggedUser, isNotLoggedUser);
        if (isNotLoggedUser) {
            vkBot.sendMessageTo(userId, Answer.CANNOT_LOGIN);
            groupsRepository.addToIntegerArray(groupName, "loginWaitingUsers", userId);
            group.getLoginWaitingUsers().add(userId);
        }
    }

    public static void rememberUpdateAuthDataMessage(String groupName, @NonNull LoggedUser loggedUser,
                                                     boolean isNotLoggedUser) {
        if (!isNotLoggedUser || !loggedUser.isUpdateAuthDataNotified()) {
            vkBot.sendMessageTo(loggedUser.getId(), Answer.UPDATE_CREDENTIALS);

            groupsRepository.updateField(groupName, GroupsRepository.UPDATE_AUTH_DATA_NOTIFIED, true);
            loggedUser.setUpdateAuthDataNotified(true);
        }
    }

    private static void onLoginMessages (Integer userId, @NonNull String groupName, String messageText) {

        final String[] chunks = messageText.split("\n");
        String login = chunks[0];
        String password = chunks[1];

        Group group = groupByGroupName.get(groupName);
        if (group == null) { // Если нет в бд
            try {
                loginNewGroup(userId, groupName, login, password);

            } catch (LkNotRespondingException | AuthenticationException e) {
                throw e;

            } catch (Exception e) {
                throw new RuntimeException("Не удалось добавить группу " + groupName + " пользователя " + userId, e);
            }
            return;
        }

        final LoggedUser loggedUser;

        if (group.hasLeader()) {
            if (group.getLoggedUser().is(userId)) {
                if (!tryLogin(userId, group.getLkParser(), login, password))
                    return;

                loggedUser = group.getLoggedUser();
                loggedUser.setAuthData(cipherService.encrypt(login, password));
                loggedUser.setUpdateAuthDataNotified(false);

                groupsRepository.updateLoggedUser(group.getName(), loggedUser);
                vkBot.sendMessageTo(loggedUser.getId(), Answer.CREDENTIALS_UPDATED);

                notifyLoginWaitingUsers(group, Answer.LEADER_UPDATE_PASSWORD);

            } else groupAlreadyRegisteredMessage(userId, group);
            return;
        }

        if (!tryLogin(userId, group.getLkParser(), login, password))
            return;

        loggedUser = new LoggedUser(userId, cipherService.encrypt(login, password));
        groupsRepository.updateLoggedUser(group.getName(), loggedUser);
        group.setLoggedUser(loggedUser);
        group.getUsers().add(new GroupUser(userId));
        vkBot.sendMessageTo(userId, KeyboardService.getCommands(userId, group),
                Answer.I_CAN_SEND_INFO);

        notifyLoginWaitingUsers(group, Answer.getNewLeaderIs(vkBot.getUserName(userId)));
    }

    private static void notifyLoginWaitingUsers(Group group, String message) {
        groupsRepository.moveLoginWaitingUsersToUsers(group.getName());
        group.getLoginWaitingUsers().forEach(userId ->
                vkBot.sendMessageTo(userId, KeyboardService.getCommands(userId, group), message));
        group.setLoginWaitingUsers(new HashSet<>());
    }

    private static String actualizeGroupName(Integer userId, String groupName, LkParser lkParser) {
        final var optionalLkGroupName = lkParser.getGroupName();
        if (optionalLkGroupName.isEmpty())
            return groupName;

        final var lkGroupName = optionalLkGroupName.get();
        if (lkGroupName.equals(groupName))
            return groupName;

        vkBot.sendMessageTo(userId, Answer.getGroupNameChanged(groupName, lkGroupName));
        groupNameByUserId.replace(userId, lkGroupName);
        return lkGroupName;
    }

    private static void loginNewGroup(Integer userId, @NonNull String groupName, String login, String password) {

        final LkParser lkParser = new LkParser();
        if (!tryLogin(userId, lkParser, login, password))
            return;

        //vkBotService.deleteLastMessage(message);

        groupName = actualizeGroupName(userId, groupName, lkParser);

        Group oldGroup = groupByGroupName.get(groupName);
        if (oldGroup != null) {
            vkBot.sendMessageTo(userId, Answer.GROUP_ALREADY_EXISTS);
            startUserVerification(userId, oldGroup);
            return;
        }

        String actualSemester = Utils.getActualSemester();

        List<Subject> newSubjects = lkParser.getSubjectsFirstTime(actualSemester);

        final var newGroup = new Group(groupName)
                .setLoggedUser(new LoggedUser().setId(userId).setAuthData(cipherService.encrypt(login, password)))
                .setSubjects(newSubjects)
                .setSemesterName(actualSemester);
        newGroup.getUsers().add(new GroupUser(userId));
        newGroup.setLkParser(lkParser);

        lkParser.setSubjectsGeneralLkIds(newGroup, actualSemester);

        newUserSubjectsListMessage(userId, newGroup);

        if (!newSubjects.isEmpty())
            vkBot.sendLongMessageTo(userId, Answer.getSubjectsFirstTime(newSubjects));
        else
            vkBot.sendLongMessageTo(userId, Answer.I_CANT_GOT_SUBJECTS);

        newGroup.setTimetable(lkParser.parseTimetable(newGroup.getLkSemesterId(), newGroup.getLkId()));

        groupsRepository.insert(newGroup);
        groupByGroupName.put(groupName, newGroup);

        vkBot.sendMessageTo(userId, Answer.I_CAN_SEND_INFO);
    }

    private static void finallyForgetUser(Integer userId, String groupName, Group group, LoggedUser loggedUser) {
        vkBot.unsetKeyboard();
        if (loggedUser != null && loggedUser.is(userId)) {
            vkBot.sendMessageTo(userId, Answer.AFTER_LEADER_FORGETTING);

            groupsRepository.removeLoggedUser(groupName, loggedUser.getId());
            group.removeLoggedUser(loggedUser.getId());

        } else
            vkBot.sendMessageTo(userId, Answer.AFTER_USER_FORGETTING);
        groupsRepository.removeUserFromGroup(groupName, userId);
        groupNameByUserId.remove(userId);
        group.removeUserFromGroup(userId);
    }

    @Getter @Setter
    private static volatile boolean isWeekTypeUpdated = false;

    public static void actualizeWeekType() {
        Optional.ofNullable(groupByGroupName.get("ПИ-19-1"))
                .ifPresentOrElse(group -> {
                    final LkParser lkParser = new LkParser();
                    if (!group.hasLeader()) {
                        vkBot.sendMessageTo(APP_ADMIN_ID1, Answer.FOR_ADMIN_WRITE_WEEK_TYPE);
                        return;
                    }
                    try {
                        lkParser.login(cipherService.decrypt(group.getLoggedUser().getAuthData()));
                        isActualWeekWhite = lkParser.parseWeekType(group.getLkSemesterId());
                        isWeekTypeUpdated = true;

                    } catch (AuthenticationException ignored) {
                        vkBot.sendMessageTo(APP_ADMIN_ID1, Answer.FOR_ADMIN_WRITE_WEEK_TYPE);
                    }

                }, () -> vkBot.sendMessageTo(APP_ADMIN_ID1, Answer.FOR_ADMIN_WRITE_WEEK_TYPE));
    }
}
