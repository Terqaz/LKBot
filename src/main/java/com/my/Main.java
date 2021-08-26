package com.my;

import com.my.models.AuthenticationData;
import com.my.models.Group;
import com.my.models.LoggedUser;
import com.my.models.SubjectData;
import com.my.services.ButtonsCreator;
import com.my.services.LstuAuthService;
import com.my.services.LstuParser;
import com.my.services.VkBotService;
import com.vk.api.sdk.objects.messages.Message;
import lombok.NonNull;
import lombok.SneakyThrows;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    static CipherService cipherService = null;
    static final VkBotService vkBotService = VkBotService.getInstance();
    static final LstuAuthService lstuAuthService = new LstuAuthService();
    static final LstuParser lstuParser = new LstuParser();

    static final Map<Integer, String> groupNameByUserId = new HashMap<>();

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

    static final int ADMIN_VK_ID = 173315241;

    private static final Pattern groupNamePatternOnlyUpperCase =
            Pattern.compile("((Т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,4}-)(П-)?\\d{2}(-\\d)?");

    private static String actualSemester;

    private static class PlannedSubjectsDataUpdate extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(60L * 1000); // 1 минута
                    final var newSemester = Utils.getNewScannedSemesterName();

                    for (Group group : groupsRepository.findAll()) {
                        LoggedUser loggedUser = group.getLoggedUser();


                        final GregorianCalendar calendar = new GregorianCalendar();
                        final var checkDate = calendar.getTime();
                        if (isSilentTime(group, calendar.get(Calendar.HOUR_OF_DAY)) ||
                                !checkDate.after(group.getNextCheckDate())) {
                            continue;
                        }

                        if (!group.isNotLoggedNow() &&
                                !lstuAuthService.login(cipherService.decrypt(loggedUser.getAuthData()))) {
                            vkBotService.sendMessageTo(loggedUser.getId(),
                                    "Не удалось обновить данные из ЛК по следующей причине:\n" +
                                            "Необходимо обновить данные для входа");
                            continue;
                        }
                        vkBotService.sendMessageTo(loggedUser.getId(), "Началось плановое обновление");

                        final var oldSubjectsData = group.getSubjectsData();

                        final List<SubjectData> newSubjectsData;

                        if (actualSemester.equals(newSemester))
                            newSubjectsData = lstuParser.getNewSubjectsData(oldSubjectsData, group.getLastCheckDate());
                        else {
                            actualSemester = newSemester;
                            newSubjectsData = lstuParser.getSubjectsDataFirstTime(actualSemester);
                        }
                        lstuAuthService.logout();
                        groupsRepository.updateSubjectsData(group.getName(), newSubjectsData, checkDate);
                        group.setLastCheckDate(checkDate);

                        String report;
                        if (actualSemester.equals(newSemester))
                            report = ReportUtils.getSubjectsData(
                                    Utils.removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData),
                                    group.getNextCheckDate());
                        else
                            report = "Данные теперь приходят из семестра: " + newSemester + "\n" +
                                    ReportUtils.getSubjectsData(newSubjectsData, group.getNextCheckDate());

                        if (!report.startsWith("Нет новой")) {
                            final var finalReport = "Плановое обновление:\n" + report;
                            vkBotService.sendLongMessageTo(group.getUsers(), finalReport);
                        } else {
                            if (loggedUser.isAlwaysNotify())
                                vkBotService.sendMessageTo(loggedUser.getId(), report);
                        }
                    }
                }
                catch (InterruptedException e) {
                    break;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private static boolean isSilentTime (Group group, int nowHour) {
            final int silentModeStart = group.getSilentModeStart();
            final int silentModeEnd = group.getSilentModeEnd();

            if (silentModeStart < silentModeEnd) {
                return silentModeStart <= nowHour && nowHour <= silentModeEnd;

            } else if (silentModeStart > silentModeEnd) {
                return silentModeStart <= nowHour || nowHour <= silentModeEnd;

            } else return true;
        }
    }

    public static void main (String[] args)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        cipherService = CipherService.getInstance();

        actualSemester = Utils.getNewScannedSemesterName();
        vkBotService.setOnline(true);
        fillGroupNameByUserId();

        final var plannedSubjectsDataUpdate = new PlannedSubjectsDataUpdate();
        plannedSubjectsDataUpdate.start();

        runCycle();
        vkBotService.setOnline(false);
        plannedSubjectsDataUpdate.interrupt();
        vkBotService.sendMessageTo(ADMIN_VK_ID, "WARNING: App closed");
    }

    private static void fillGroupNameByUserId () {
        for (Group group : groupsRepository.findAllUsersOfGroups()) {
            if (!group.isLoggedBefore())
                continue;

            final var groupName = group.getName();
            group.getUsers()
                    .forEach(userId -> groupNameByUserId.put(userId, groupName));
            group.getLoginWaitingUsers()
                    .forEach(userId -> groupNameByUserId.put(userId, groupName));
        }
    }

    private static void runCycle () {
        while (true) {
            final List<Message> messages;
            messages = vkBotService.getNewMessages();
            if (!messages.isEmpty()) {
                for (Message message : messages) {
                    final Integer userId = message.getFromId();

                    if (userId > 0) {
                        try {
                            executeBotDialog(userId, message.getText());
                        } catch (Exception e) {
                            vkBotService.sendMessageTo(userId, "Я не понял тебя или ошибся сам");
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private static void executeBotDialog (Integer userId, String messageText) {
        messageText = Utils.translateFromEnglishKeyboardLayoutIfNeeds(messageText);

        final var groupNameMatcher =
                groupNamePatternOnlyUpperCase.matcher(messageText.toUpperCase());
        if (groupNameMatcher.find()) {
            newUserGroupCheck(userId, messageText, groupNameMatcher);
            return;

        } else if (messageText.startsWith("Хочу войти в ЛК")) {
            onLoginMessages(userId, groupNameByUserId.get(userId), messageText);
            return;
        }

        if (!groupNameByUserId.containsKey(userId)) {
            vkBotService.sendMessageTo(userId,
                    "Напиши из какой ты группы (так же, как указано в ЛК). Например:\n" +
                            "Я из ПИ-19-1");
            return;
        }

        final var groupName = groupNameByUserId.get(userId);
        messageText = messageText.toLowerCase();

        final var optionalGroup = groupsRepository.findByGroupName(groupName);
        
        switch (messageText) {
            case "я готов на все ради своей группы!":
                if (optionalGroup.map(Group::isNotLoggedNow).orElse(true)) {
                    vkBotService.unsetKeyboard();
                    vkBotService.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так " +
                                    AUTH_COMMAND);
                } else
                    groupAlreadyRegisteredMessage(userId);
                return;

            case "лучше скажу другому":
                if (optionalGroup.map(Group::isNotLoggedNow).orElse(true)) {
                    vkBotService.unsetKeyboard();
                    groupNameByUserId.remove(userId);
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Напиши мне, когда человек из твоей группы зайдет через меня");
                } else
                    groupAlreadyRegisteredMessage(userId);
                return;

            case "я ошибся при вводе группы":
                if (optionalGroup.map(group -> group.getUsers().contains(userId)).orElse(false)) {
                    vkBotService.sendMessageTo(userId, "Напиши \"Забудь меня\", чтобы перезайти в меня");
                } else {
                    vkBotService.unsetKeyboard();
                    groupNameByUserId.remove(userId);
                    vkBotService.sendMessageTo(userId,
                            "Введи новое имя для группы (так же, как указано в ЛК). Например:\n" +
                                    "Я из ПИ-19-1");
                }
                return;
            default: break;
        }

        final var group = optionalGroup.get();
        final LoggedUser loggedUser = group.getLoggedUser();

        Integer subjectIndex = Utils.tryParseSubjectIndex(messageText);
        if (subjectIndex != null) {
            getActualSubjectDataMessage(userId, group, subjectIndex);
            return;

        } else if (messageText.startsWith("изменить интервал на ")) {
            final var minutes = Long.parseLong(messageText.substring(21));
            if (loggedUser.equals(userId)) {
                if (10 <= minutes && minutes <= 20160) {
                    final long newUpdateInterval = minutes * 60 * 1000;
                    groupsRepository.updateField(groupName,"updateInterval", newUpdateInterval);
                    vkBotService.sendMessageTo(userId, "Интервал изменен");
                    group.setUpdateInterval(newUpdateInterval);
                    nextUpdateDateMessage(userId, group.getNextCheckDate());
                } else
                    vkBotService.sendMessageTo(userId, "Нельзя установить такой интервал обновления");
            } else
                userInsufficientPermissionsMessage(userId);
            return;

        } else if (messageText.startsWith("тихий режим с ")) { // Тихий режим с n по k
            final String[] strings = messageText.split(" ");
            final var startHour = Integer.parseInt(strings[3]);
            final var endHour = Integer.parseInt(strings[5]);
            if (! (0 <= startHour && startHour <= 23 && 0 <= endHour && endHour <= 23)) {
                vkBotService.sendMessageTo(userId, "Нельзя установить такое время тихого режима");
            } else {
                groupsRepository.updateSilentMode(groupName, startHour, endHour);
                vkBotService.sendMessageTo(userId, "Время тихого режима изменено");
            }
            return;
        }

        switch (messageText) {
            case "предметы":
                vkBotService.sendMessageTo(userId, ReportUtils.getSubjectsNames(group.getSubjectsData()));
                break;

            case "команды":
                vkBotService.sendMessageTo(userId,
                        ButtonsCreator.getCommandsKeyboard(userId, group.getLoggedUser()),
                        getUserCommands(userId, group));
                break;

            case "без пустых отчетов":
                changeLoggedUserNotifying(userId, group, false);
                break;

            case "с пустыми отчетами":
                changeLoggedUserNotifying(userId, group, true);
                break;

            case "забудь меня":
                if (loggedUser.equals(userId))
                    vkBotService.sendMessageTo(userId,
                            "➡ Эта опция полезна, если тебе нужно изменить человека, " +
                                    "зарегистрированного от имени группы или если я тебе больше не нужен. " +
                                    "После твоего ухода кому-то нужно будет сказать мне логин и пароль от своего ЛК, " +
                                    "если вы хотите продолжать пользоваться мной. " +
                                    "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                    "Я уверен, что хочу, чтобы ты забыл меня");
                else
                    vkBotService.sendMessageTo(userId,
                            "➡ Эта опция будет полезна тебе, чтобы войти от имени группы после того, " +
                                    "как я забыл другого зарегистрированного человека из твоей группы, " +
                                    "или если я тебе больше не нужен. \n" +
                                    "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                    "Я уверен, что хочу, чтобы ты забыл меня");
                break;

            case "я уверен, что хочу, чтобы ты забыл меня":
                vkBotService.unsetKeyboard();
                if (loggedUser.equals(userId)) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Рекомендую тебе поменять пароль в ЛК (http://lk.stu.lipetsk.ru).\n" +
                                    "Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB");
                    groupsRepository.removeLoggedUser(groupName, loggedUser.getId());

                } else
                    vkBotService.sendMessageTo(userId,"Хорошо. Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB");
                groupNameByUserId.remove(userId);
                break;

            default:
                vkBotService.sendMessageTo(userId, "Я не понял тебя");
                break;
        }
    }

    private static void newUserGroupCheck (Integer userId, String messageText, Matcher groupNameMatcher) {
        if (groupNameByUserId.containsKey(userId)) {
            vkBotService.sendMessageTo(userId, "Я уже знаю, что ты из " + groupNameByUserId.get(userId));
            return;
        }

        final String groupName = messageText.substring(groupNameMatcher.start(), groupNameMatcher.end());
        groupNameByUserId.put(userId, groupName);

        final var optionalGroup = groupsRepository.findByGroupName(groupName);
        if (optionalGroup.map(Group::isLoggedBefore).orElse(false)) {
            if (optionalGroup.map(Group::isNotLoggedNow).orElse(false)) {
                vkBotService.sendMessageTo(userId,
                        "В этой группе был человек, вошедший от своего имени, но теперь его нет. " +
                                "Ты хочешь стать им?\n");
                newUserMessage(userId);
            } else {
                Group group = optionalGroup.get();
                vkBotService.sendMessageTo(userId, "О, я знаю эту группу!");
                groupsRepository.addUserTo(group.getName(), "users", userId);
                newUserSubjectsListMessage(userId, group);
            }
        } else {
            vkBotService.sendMessageTo(userId, "Я не знаю группы " + groupName);
            newUserMessage(userId);
        }

    }

    private static void newUserMessage (Integer userId) {
        vkBotService.sendMessageTo(userId, ButtonsCreator.KEYBOARD_1,
                "➡ Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию " +
                        "для тебя и твоих одногруппников.\n" +
                        "Можешь мне довериться ;-)\n" +
                        "➡ Если ты мне не доверяешь, то позволь ввести пароль другому человеку из твоей группы. " +
                        "Обещаю не писать тебе когда не надо.\n\n" +
                        "➡ Все мои возможности смотри в группе:\nhttps://vk.com/dorimelk\n" +
                        "Ты всегда можешь изучить мой внутренний мир по этой ссылке:\nhttps://github.com/Terqaz/LKBot\n" +
                        "И иногда обратиться к этому человеку за помощью:\nhttps://vk.com/terqaz");
    }

    private static void newUserSubjectsListMessage (Integer userId, Group group) {
        vkBotService.sendMessageTo(userId,
                "Теперь я могу вывести тебе последнюю информацию из ЛК по данным предметам " +
                        "(обновление было " + Utils.formatDate(group.getLastCheckDate()) + "):\n" +
                        ReportUtils.getSubjectsNames(group.getSubjectsData()));

        vkBotService.sendMessageTo(userId, ButtonsCreator.getCommandsKeyboard(userId, group.getLoggedUser()),
                "Также теперь ты можешь использовать эти команды:\n" + getUserCommands(userId, group));
    }

    private static String getUserCommands (Integer userId, Group group) {
        final LoggedUser loggedUser = group.getLoggedUser();

        if (loggedUser.equals(userId))
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
                            "🔶 Писать тебе, пока нет новой информации:\nС пустыми отчетами");

        else return BASIC_COMMANDS;
    }

    private static void groupAlreadyRegisteredMessage (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Ой, похоже ты опоздал! Эту группу уже успели зарегистрировать.");
    }

    private static void changeLoggedUserNotifying (Integer userId, Group group, boolean isAlwaysNotify) {
        if (group.getLoggedUser().equals(userId)) {
            groupsRepository.updateField(group.getName(), "loggedUser.alwaysNotify", isAlwaysNotify);
            group.getLoggedUser().setAlwaysNotify(isAlwaysNotify);
            vkBotService.sendMessageTo(userId,
                            ButtonsCreator.getCommandsKeyboard(userId, group.getLoggedUser()),
                    "Хорошо");
        } else
            userInsufficientPermissionsMessage(userId);
    }

    private static void nextUpdateDateMessage (Integer userId, Date nextCheckDate) {
        nextUpdateDateMessage(Collections.singletonList(userId), nextCheckDate);
    }

    private static void nextUpdateDateMessage (Collection<Integer> userIds, Date nextCheckDate) {
        vkBotService.sendMessageTo(userIds, ReportUtils.getNextUpdateDateText(nextCheckDate));
    }

    private static void userInsufficientPermissionsMessage (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Я разрешаю эту операцию только человеку, вошедшему от имени группы");
    }

    private static void getActualSubjectDataMessage (Integer userId, Group group, Integer subjectIndex) {
        final LoggedUser loggedUser = group.getLoggedUser();
        if (!lstuAuthService.login(cipherService.decrypt(loggedUser.getAuthData()))) {
            repeatLoginFailedMessages(userId, group);
            return;
        }

        final var optionalSubjectData = group.getSubjectsData().stream()
                .filter(subjectData1 -> subjectData1.getId() == subjectIndex)
                .findFirst();
        if (optionalSubjectData.isEmpty()) {
            vkBotService.sendMessageTo(userId, "Неправильный номер предмета");
            return;
        }
        final var oldSubjectData = optionalSubjectData.get();

        SubjectData newSubjectData = lstuParser.getNewSubjectData(
                oldSubjectData.getName(), oldSubjectData.getLocalUrl(), group.getLastCheckDate());
        lstuAuthService.logout();

        newSubjectData.setId(subjectIndex);
        newSubjectData.getDocumentNames()
                .removeAll(oldSubjectData.getDocumentNames());

        if (newSubjectData.isNotEmpty()) {
            vkBotService.sendLongMessageTo(userId,
                    ReportUtils.getSubjectsData(List.of(newSubjectData), null));
        } else {
            vkBotService.sendMessageTo(userId,
                    "Нет новой информации по предмету " + newSubjectData.getName());
        }
    }

    private static void newGroupLoginFailedMessages (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Что-то пошло не так. \n" +
                "Либо твои логин и пароль неправильные, либо я неправильно их прочитал");
    }

    private static void repeatLoginFailedMessages (Integer userId, Group group) {
        if (!group.getLoggedUser().equals(userId)) {
            vkBotService.sendMessageTo(userId,
                    "➡ Мне не удалось проверить данные твоей группы. " +
                            "Человек, вошедший от имени группы изменил свой пароль в ЛК и не сказал мне новый пароль. " +
                            "Я скажу ему об этом сам.");
            groupsRepository.addUserTo(group.getName(), "loginWaitingUsers", userId);
        }
        vkBotService.sendMessageTo(group.getLoggedUser().getId(),
                "➡ Похоже ты забыл сказать мне новый пароль после его обновления в ЛК." +
                        "Скажи мне новые данные для входа так " +
                        AUTH_COMMAND);
    }

    private static void onLoginMessages (Integer userId, @NonNull String groupName, String messageText) {
        final String[] chunks = messageText.split("\n");
        String login = chunks[1];
        String password = chunks[2];

        vkBotService.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
        if (!lstuAuthService.login(new AuthenticationData(login, password))) {
            newGroupLoginFailedMessages(userId);
            return;
        }
        //vkBotService.deleteLastMessage(message);

        groupName = actualizeGroupName(userId, groupName);
        var optionalGroup = groupsRepository.findByGroupName(groupName);
        if (optionalGroup.isPresent()) {
            final var oldGroup = optionalGroup.get();
            if (oldGroup.isLoggedBefore()) {
                if (oldGroup.getLoggedUser().equals(userId)) {
                    groupsRepository.updateAuthInfo(
                            oldGroup.getName(),
                            new LoggedUser(userId, cipherService.encrypt(login, password), true));

                    groupsRepository.moveLoginWaitingUsersToUsers(oldGroup.getName());
                    vkBotService.sendMessageTo(oldGroup.getLoginWaitingUsers(),
                            "Человек из твоей группы обновил пароль от ЛК. ");
                } else
                    groupAlreadyRegisteredMessage(userId);
            }
        } else {
            newGroupLoggedMessages(userId, groupName, login, password);
            lstuAuthService.logout();
        }
    }

    private static String actualizeGroupName (Integer userId, String groupName) {
        final var optionalLkGroupName = lstuParser.getGroupName();
        if (optionalLkGroupName.isPresent()) {
            final var lkGroupName = optionalLkGroupName.get();
            if (!lkGroupName.equals(groupName)) {
                vkBotService.sendMessageTo(userId,
                        "\uD83D\uDD34 Я поменял имя введенной тобой группы "+ groupName +" на: "+lkGroupName+
                                ", чтобы избежать неприятных ситуаций. \uD83D\uDD34");
                groupNameByUserId.replace(userId, lkGroupName);
                groupName = lkGroupName;
            }
        }
        return groupName;
    }

    private static void newGroupLoggedMessages (Integer userId, @NonNull String groupName, String login, String password) {
        vkBotService.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
        vkBotService.sendMessageTo(userId,
                "Ой, извини, случайно вырвалось)\n" +
                        "➡ Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК. " +
                        "Тебе нужно просто позвать их пообщаться со мной. " +
                        "Но позволь я сначала проверю твой ЛК...");

        List<SubjectData> newSubjectsData = lstuParser.getSubjectsDataFirstTime(actualSemester);
        final var newGroup = new Group(groupName)
                .setLoggedUser(new LoggedUser(userId, cipherService.encrypt(login, password), true))
                .setSubjectsData(newSubjectsData)
                .setLastCheckDate(new Date());
        newGroup.getUsers().add(userId);

        groupsRepository.insert(newGroup);
        newUserSubjectsListMessage(userId, newGroup);
        vkBotService.sendLongMessageTo(userId, "Результат последнего обновления: \n" +
                ReportUtils.getSubjectsData(newSubjectsData, newGroup.getNextCheckDate()));
    }


    // TODO Новый функционал и оптимизация
    //  функция: напомни имена и отчества преподавателей
    //  ответ на нецензурные и похвальные слова
    //  Зашедулить бота на сон с 23 по 6 (пока что не вышло)
    //  Удаление сообщения с данными входа (пока что не получилось, хотя согласно докам можно)

    // TODO Для массового распространения бота:
    //  Написать подробные возможности бота в группе
    //  добавить вход участника группы через проверочный код
    //  добавить асинхронное скачивание данных из лк по группам при глобальном обновлении
    //  оптимизация запросов к лк через очередь
}
