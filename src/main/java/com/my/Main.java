package com.my;

import com.my.exceptions.CloseAppNeedsException;
import com.my.models.Group;
import com.my.models.MessageData;
import com.my.models.SubjectData;
import com.my.services.LstuAuthService;
import com.my.services.LstuParser;
import com.my.services.VkBotService;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import com.vk.api.sdk.objects.messages.Message;
import lombok.NonNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class Main {

    static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    static final Map<Integer, String> groupNameByUserId = new HashMap<>();

    static final VkBotService vkBotService = VkBotService.getInstance();
    static final LstuAuthService lstuAuthService = new LstuAuthService();
    static final LstuParser lstuParser = new LstuParser();

    static final Keyboard keyboard1 = new Keyboard()
            .setButtons(Arrays.asList(
                    Collections.singletonList(
                            VkBotService.generateButton("Я готов на все ради своей группы!", KeyboardButtonColor.POSITIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Лучше скажу другому", KeyboardButtonColor.NEGATIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Я ошибся при вводе группы", KeyboardButtonColor.DEFAULT))
            ));

    static final Keyboard keyboard2 = new Keyboard()
            .setButtons(Arrays.asList(
                    Collections.singletonList(
                            VkBotService.generateButton("Сканируй все равно", KeyboardButtonColor.POSITIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Ладно, отдыхай", KeyboardButtonColor.NEGATIVE))
            ));

    private static final String BASIC_COMMANDS =
            "🔷 Вывести список предметов:\n" +
                    "Список предметов\n" +
                    "🔷 Узнать самую свежую информацию по предмету из ЛК:\n" +
                    "Предмет n (n - номер в списке выше)\n" +
                    "🔷 Узнать последнюю проверенную информацию по всем предметам:\n" +
                    "Предметы\n" +
                    "🔷 Узнать самую свежую информацию по всем предметам:\n" +
                    "Обнови предметы\n" +
                    "🔶 Показать эти команды:\n" +
                    "Команды\n" +
                    "🔶 Прекратить пользоваться ботом или сменить зарегистрированного человека:\n" +
                    "Забудь меня";

    private static final String LOGGED_USER_COMMANDS =
            BASIC_COMMANDS + "\n" +
                    "🔶 Не писать тебе, пока нет новой информации:\n"+
                    "Не пиши попусту\n"+
                    "🔶 Изменить интервал автоматического обновления:\n" +
                    "Изменить интервал на n (n - количество минут от 30, до 720)\n";

    private static final String AUTH_COMMAND =
            "(рекомендую сразу удалить это сообщение):\n" +
                    "Хочу войти в ЛК\n" +
                    "Мой_логин\n" +
                    "Мой_пароль";

    static final int ADMIN_VK_ID = 173315241;
    private static final Pattern groupNamePattern = LstuUtils.groupNamePattern;

    private static String actualSemester;

    public static void main (String[] args) {
        actualSemester = getNewScannedSemesterName();
        vkBotService.setOnline(true);
        fillGroupNameByUserId();
        try {
            runCycle();
        } catch (CloseAppNeedsException ignored) {}
        vkBotService.sendMessageTo(ADMIN_VK_ID, "WARNING: App closed");
        vkBotService.setOnline(false);
    }

    private static void fillGroupNameByUserId () {
        for (Group group : groupsRepository.findAllUsersOfGroups()) {
            if (!group.isLoggedBefore())
                continue;

            final var groupName = group.getName();
            groupNameByUserId.put(group.getLoggedUserId(), groupName);
            group.getUsers().forEach(userId ->
                    groupNameByUserId.put(userId, groupName));
            group.getLoginWaitingUsers().forEach(userId ->
                    groupNameByUserId.put(userId, groupName));
        }
    }

    private static void runCycle () {
        while (true) {
            final List<Message> messages;
            messages = vkBotService.getNewMessages();
            if (!messages.isEmpty()) {
                for (Message message : messages) {
                    final Integer userId = message.getFromId();
                    if (userId.equals(ADMIN_VK_ID) && message.getText().equals("close")) {
                        throw new CloseAppNeedsException();
                    }
                    if (userId > 0) {
                        System.out.println(message); // TODO логирование
                        try {
                            executeBotDialog(userId, message);
                        } catch (Exception e) {
                            vkBotService.sendMessageTo(userId, "Я не понял тебя или ошибся сам");
                            e.printStackTrace();
                        }
                    }
                }
            }
            plannedSubjectsDataUpdate();
        }
    }

    private static void plannedSubjectsDataUpdate () {
        final var newSemester = getNewScannedSemesterName();

        for (Group group : groupsRepository.findAllLogged()) {
            final var checkDate = new Date();
            if (!checkDate.after(group.getNextCheckDate())) {
                continue;
            }

            if (group.getLoggedUserId() == null || !lstuAuthService.login(group.getLogin(), group.getPassword())) {
                vkBotService.sendMessageTo(group.getLoggedUserId(),
                        "Не удалось обновить данные из ЛК по следующей причине:\n" +
                                "Необходимо обновить данные для входа");
                continue;
            }

            final var oldSubjectsData = group.getSubjectsData();
            final List<SubjectData> newSubjectsData;
            String report;
            if (actualSemester.equals(newSemester)) {
                newSubjectsData = lstuParser.getNewSubjectsData(oldSubjectsData, group.getLastCheckDate());
                report = makeSubjectsDataReport(LstuParser.removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData));
            } else {
                actualSemester = newSemester;
                newSubjectsData = lstuParser.getSubjectsDataFirstTime(actualSemester);
                report = "Данные теперь приходят из семестра " + newSemester + "\n" +
                        makeSubjectsDataReport(newSubjectsData);
            }
            lstuAuthService.logout();
            groupsRepository.updateSubjectsData(group.getName(), newSubjectsData, checkDate);

            if (!report.startsWith("Нет новой")) {
                final var finalReport = "Плановое обновление:\n" + report;
                //TODO Отправка сообщения разом
                group.getUsers().forEach(userId1 -> {
                    vkBotService.sendLongMessageTo(userId1, finalReport);
                    nextUpdateDateMessage(userId1, group);
                });
            } else {
                if (group.isAlwaysNotifyLoggedUser())
                    vkBotService.sendMessageTo(group.getLoggedUserId(), "Плановое обновление:\n" + report);
            }
        }
    }

    private static void executeBotDialog (Integer userId, Message message) {
        String messageText = message.getText();
        if (messageText.startsWith("Я из ")) {
            newUserGroupCheck(userId, messageText);
            return;

        } else if (messageText.startsWith("Хочу войти в ЛК")) {
            onLoginMessages(userId, groupNameByUserId.get(userId), message);
            return;
        }

        if (!groupNameByUserId.containsKey(userId)) {
            vkBotService.sendMessageTo(userId, "Напиши из какой ты группы (например: \"Я из ПИ-19-1\"):");
            return;
        }

        final var groupName = groupNameByUserId.get(userId);
        messageText = messageText.toLowerCase();

        // TODO Разделить на запросы по действительно нужным полям для команд
        final var optionalGroup = groupsRepository.findByGroupName(groupName);

        switch (messageText) {
            case "я готов на все ради своей группы!":
                if (optionalGroup.map(Group::isNotLoggedNow).orElse(true)) {
                    vkBotService.unsetKeyboard();
                    vkBotService.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так " +
                                    AUTH_COMMAND);
                } else {
                    groupAlreadyRegisteredMessage(userId);
                }
                return;

            case "лучше скажу другому":
                if (optionalGroup.map(Group::isNotLoggedNow).orElse(true)) {
                    vkBotService.unsetKeyboard();
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Я скажу, когда он сообщит мне свои логин и пароль");
                    groupsRepository.addUserTo(groupName, "loginWaitingUsers", userId);
                } else {
                    groupAlreadyRegisteredMessage(userId);
                }
                return;

            case "я ошибся при вводе группы":
                vkBotService.unsetKeyboard();
                groupNameByUserId.remove(userId);
                vkBotService.sendMessageTo(userId, "Введи новое имя для группы (например: \"Я из ПИ-19\"):");
                return;
        }

        final var group = optionalGroup.get();

        if (messageText.startsWith("предмет ")) {
            getActualSubjectDataMessage(userId, group, messageText);

        } else if (messageText.equals("предметы")) {
            vkBotService.sendLongMessageTo(userId,
                    makeSubjectsDataReport(group.getSubjectsData()));
            nextUpdateDateMessage(userId, group);

        } else if (messageText.equals("список предметов")) {
            vkBotService.sendMessageTo(userId,
                    makeSubjectsListReport(group.getSubjectsData()));

        } else if (messageText.equals("обнови предметы")) {
            updateSubjectsDataWarningMessage(userId);

        } else if (messageText.equals("сканируй все равно")) {
            vkBotService.unsetKeyboard();
            updateSubjectsDataMessages(userId, group);

        } else if (messageText.startsWith("изменить интервал на ")) {
            final var minutes = Long.parseLong(messageText.substring(21));
            if (group.userIsLogged(userId)) {
                if (30 <= minutes && minutes <= 720) {
                    groupsRepository.updateField(groupName,
                            "updateInterval", minutes * 60 * 1000);
                    vkBotService.sendMessageTo(userId, "Интервал изменен");
                } else {
                    vkBotService.sendMessageTo(userId, "Нельзя установить такой интервал обновления");
                }
            } else {
                userInsufficientPermissionsMessage(userId);
            }

        } else if (messageText.equals("команды")) {
            vkBotService.sendMessageTo(userId,
                    (!group.userIsLogged(userId) ? BASIC_COMMANDS : LOGGED_USER_COMMANDS));

        } else if (messageText.equals("ладно, отдыхай")) {
            vkBotService.unsetKeyboard();
            vkBotService.sendMessageTo(userId, "Спасибо тебе, человек!");

        } else if (messageText.equals("не пиши попусту")) {
            if (group.userIsLogged(userId)) {
                groupsRepository.updateField(groupName,"alwaysNotifyLoggedUser", false);
                vkBotService.sendMessageTo(userId, "Хорошо");
            } else {
                userInsufficientPermissionsMessage(userId);
            }

        } else if (messageText.equals("забудь меня")) {
            if (group.userIsLogged(userId)) {
                vkBotService.sendMessageTo(userId,
                        "➡ Эта опция полезна, если тебе нужно изменить человека, " +
                        "зарегистрированного от имени группы или если я тебе больше не нужен. " +
                        "После твоего ухода кому-то нужно будет сказать мне логин и пароль от своего ЛК, " +
                        "если вы хотите продолжать пользоваться мной. " +
                        "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                        "Я уверен, что хочу, чтобы ты забыл меня");
            } else {
                vkBotService.sendMessageTo(userId,
                        "➡ Эта опция полезна будет полезна тебе, чтобы войти от имени группы после того, " +
                                "как я забыл другого зарегистрированного человека из твоей группы, " +
                                "или если я тебе больше не нужен. \n" +
                                "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                "Я уверен, что хочу, чтобы ты забыл меня");
            }

        } else if (messageText.equals("я уверен, что хочу, чтобы ты забыл меня")) {
            if (group.userIsLogged(userId)) {
                vkBotService.sendMessageTo(userId,
                        "Хорошо. Рекомендую тебе поменять пароль в ЛК (http://lk.stu.lipetsk.ru).\n" +
                                "Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB");
                groupsRepository.updateAuthInfo(groupName, null, null, null);
                //TODO Отправка сообщения разом
                group.getUsers().forEach(groupUserId -> vkBotService.sendMessageTo(groupUserId,
                        "➡ Человек больше не зарегистрирован от имени твоей группы. " +
                                "Теперь кому-то из вас стоит написать мне \"Забудь меня\", " +
                                "чтобы зарегистрироваться от имени группы"));
            } else {
                vkBotService.sendMessageTo(userId,
                        "Хорошо. Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB");
            }
            groupNameByUserId.remove(userId);

        } else {
            vkBotService.sendMessageTo(userId, "Я не понял тебя");
        }
    }

    private static void newUserGroupCheck (Integer userId, String messageText) {
        final var groupName = messageText.substring(5);
        if (groupNamePattern.matcher(groupName).find()) {
            groupNameByUserId.put(userId, groupName);

            final var optionalGroup = groupsRepository.findByGroupName(groupName);
            if (optionalGroup.map(Group::isLoggedBefore).orElse(false)) {
                if (optionalGroup.map(Group::isNotLoggedNow).orElse(false)) {
                    vkBotService.sendMessageTo(userId,
                            "В этой группе был человек, вошедший от своего имени, но теперь его нет. " +
                                    "Ты хочешь стать им?\n");
                    newUserMessage(userId);
                } else {
                    newUserOldGroupMessages(userId, optionalGroup.get());
                }
            } else {
                groupsRepository.initialInsert(new Group(groupName));
                newUserMessage(userId);
            }

        } else {
            vkBotService.sendMessageTo(userId,
            "Мне кажется, ты ввел неправильное имя для группы\n");
        }
    }

    private static void newUserMessage (Integer userId) {
        vkBotService.sendMessageTo(userId, keyboard1,
                "➡ Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию " +
                        "для тебя и твоих одногруппников.\n" +
                        "Можешь мне довериться ;-)\n" +
                        "➡ Если ты мне не доверяешь, то позволь ввести пароль другому человеку из твоей группы. " +
                        "Обещаю не писать тебе когда не надо.\n\n" +
                        "➡ Все мои возможности смотри в группе:\nhttps://vk.com/dorimelk\n" +
                        "Ты всегда можешь изучить мой внутренний мир по этой ссылке:\nhttps://github.com/Terqaz/LKBot\n" +
                        "И иногда обратиться к этому человеку за помощью:\nhttps://vk.com/terqaz");
    }

    private static void newUserOldGroupMessages (Integer userId, Group group) {
        vkBotService.sendMessageTo(userId, "О, я знаю эту группу!");
        groupsRepository.addUserTo(group.getName(), "users", userId);
        newUserSubjectsListMessage(userId, group);
    }

    private static void newUserSubjectsListMessage (Integer userId, Group group) {
        vkBotService.sendMessageTo(userId,
                "Теперь я могу вывести тебе последнюю информацию из ЛК по данным предметам\n" +
                        "(обновление было в " + formatDate(group.getLastCheckDate()) + "):\n" +
                        makeSubjectsListReport(group.getSubjectsData()) + "\n");
        vkBotService.sendMessageTo(userId, "Также теперь ты можешь использовать эти команды:\n" +
                (!group.userIsLogged(userId) ? BASIC_COMMANDS : LOGGED_USER_COMMANDS));
    }

    private static void groupAlreadyRegisteredMessage (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Ой, похоже ты опоздал! Эту группу уже успели зарегистрировать. " +
                "Держи команды:\n" + BASIC_COMMANDS);
    }

    private static void nextUpdateDateMessage (Integer userId, Group group) {
        vkBotService.sendMessageTo(userId, "Следующее обновление будет в "
                + formatDate(group.getNextCheckDate()));
    }

    private static void userInsufficientPermissionsMessage (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Я разрешаю эту операцию только человеку, вошедшему от имени группы");
    }

    private static String makeSubjectsListReport (List<SubjectData> subjectsData) {
        var stringBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            stringBuilder.append("➡ ").append(data.getId()).append(" ")
                    .append(data.getName()).append("\n");
        }
        return stringBuilder.toString();
    }

    private static void getActualSubjectDataMessage (Integer userId, Group group, String messageText) {
        var subjectIndex = Integer.parseInt(messageText.substring(8));
        if (!lstuAuthService.login(group.getLogin(), group.getPassword())) {
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

        newSubjectData.getNewDocumentNames()
                .removeAll(oldSubjectData.getOldDocumentNames());
        newSubjectData.getNewDocumentNames()
                .removeAll(oldSubjectData.getNewDocumentNames());

        if (newSubjectData.isNotEmpty()) {
            vkBotService.sendLongMessageTo(userId,
                    makeSubjectsDataReport(Collections.singletonList(newSubjectData)));
        } else {
            vkBotService.sendMessageTo(userId,
                    "Нет новой информации по предмету " + newSubjectData.getName());
        }
    }

    private static void updateSubjectsDataWarningMessage (Integer userId) {
        vkBotService.sendMessageTo(userId, keyboard2,
                "➡ Это может быть довольно долгая операция. Я и так выполняю ее регулярно. " +
                        "Я такой же ленивый как и ты, человек. Может мне не стоит сканировать весь ЛК?");

    }

    private static void updateSubjectsDataMessages (Integer userId, Group group) {
        vkBotService.sendMessageTo(userId,
                "Ладно, можешь пока отдохнуть\n" +
                        "Я тебе напишу, как проверю");

        if (!lstuAuthService.login(group.getLogin(), group.getPassword())) {
            repeatLoginFailedMessages(userId, group);
            return;
        }
        final var checkDate = new Date();

        final var oldSubjectsData = group.getSubjectsData();
        List<SubjectData> newSubjectsData =
                lstuParser.getNewSubjectsData(oldSubjectsData, group.getLastCheckDate());
        lstuAuthService.logout();

        groupsRepository.updateSubjectsData(group.getName(), newSubjectsData, checkDate);

        String report = makeSubjectsDataReport(
                LstuParser.removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData));
        vkBotService.sendLongMessageTo(userId, report);
    }

    private static void newGroupLoginFailedMessages (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Что-то пошло не так. \n" +
                "Либо твои логин и пароль неправильные, либо я неправильно их прочитал");
    }

    private static void repeatLoginFailedMessages (Integer userId, Group group) {
        final var loggedUserId = group.getLoggedUserId();
        if (!group.userIsLogged(userId)) {
            vkBotService.sendMessageTo(userId,
                    "➡ Мне не удалось проверить данные твоей группы. " +
                            "Человек, вошедший от имени группы изменил свой пароль в ЛК и не сказал мне новый пароль. " +
                            "Я скажу ему об этом сам.");
            groupsRepository.addUserTo(group.getName(), "loginWaitingUsers", userId);
        }
        vkBotService.sendMessageTo(loggedUserId,
                "➡ Похоже ты забыл сказать мне новый пароль после обновления в ЛК." +
                        "Скажи мне новые данные для входа так " +
                        AUTH_COMMAND);
    }

    private static void notifyLoginWaitingUsers (Group group, String message) {
        final var users = group.getLoginWaitingUsers();
        users.forEach(userId -> vkBotService.sendMessageTo(userId, message)); //TODO Отправка сообщения разом
        groupsRepository.moveLoginWaitingUsersToUsers(group.getName());
    }

    private static String makeSubjectsDataReport (List<SubjectData> subjectsData) {
        if (subjectsData.isEmpty()) {
            return "Нет новой информации по предметам";
        }
        final var builder = new StringBuilder();
        var partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            if (!data.getNewDocumentNames().isEmpty()) {
                partBuilder.append("➡ ").append(data.getName())
                        .append(": ")
                        .append(String.join(", ", data.getNewDocumentNames()))
                        .append("\n\n");
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("\uD83D\uDD34 Новые документы:\n").append(partBuilder);
        }
        partBuilder = new StringBuilder();
        for (SubjectData subjectData : subjectsData) {
            final List<MessageData> messagesData = subjectData.getMessagesData();
            if (!messagesData.isEmpty()) {
                var messagesBuilder = new StringBuilder();
                for (MessageData messageData : messagesData) {
                    final String shortName = messageData.getSender();
                    messagesBuilder.append("☑ ").append(shortName)
                            .append(" в ")
                            .append(formatDate(messageData.getDate()))
                            .append(":\n")
                            .append(messageData.getComment())
                            .append("\n\n");
                }
                partBuilder.append("➡ ")
                        .append(subjectData.getName())
                        .append(":\n")
                        .append(messagesBuilder);
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("\uD83D\uDD34 Новые сообщения:\n").append(partBuilder);
        }
        return builder.toString();
    }

    private static String formatDate (Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }

    private static void onLoginMessages (Integer userId, @NonNull String groupName, Message message) {
        final String[] chunks = message.getText().split("\n");
        String login = chunks[1];
        String password = chunks[2];

        vkBotService.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
        if (!lstuAuthService.login(login, password)) {
            newGroupLoginFailedMessages(userId);
            return;
        }
        //vkBotService.deleteLastMessage(message);

        var optionalGroup = groupsRepository.findByGroupName(groupName);
        if (optionalGroup.isEmpty()) return; // Не должно быть

        final var oldGroup = optionalGroup.get();
        if (oldGroup.isLoggedBefore()) {
            groupsRepository.updateAuthInfo(oldGroup.getName(), userId, login, password);
            notifyLoginWaitingUsers(oldGroup, "Человек из твоей группы обновил пароль от ЛК. ");
            return;
        }

        newGroupLoginMessages(userId, groupName, login, password);
        notifyLoginWaitingUsers(oldGroup,
                "Человек из твоей группы вошел в ЛК через меня. " +
                        "Теперь ты можешь использовать эти команды:\n" + BASIC_COMMANDS);

        lstuAuthService.logout();
    }

    private static void newGroupLoginMessages (Integer userId, String groupName, String login, String password) {
        final var optionalLkGroupName = lstuParser.getGroupName();
        if (optionalLkGroupName.isPresent()) {
            final var lkGroupName = optionalLkGroupName.get();
            if (!lkGroupName.equals(groupName)) {
                vkBotService.sendMessageTo(userId,
                        "\uD83D\uDD34 Я поменяю имя введенной тобой группы "+ groupName +" на: "+lkGroupName+
                                ", чтобы твои одногруппники ничего не перепутали. \uD83D\uDD34");
                groupNameByUserId.replace(userId, lkGroupName);
                // correctLoginWaitingUsersGroup(); TODO
                newGroupLoggedMessages(userId, lkGroupName, groupName, login, password);
            }
        }
        newGroupLoggedMessages(userId, groupName, groupName, login, password);
    }

    private static void newGroupLoggedMessages (Integer userId, @NonNull String newName, @NonNull String oldName, String login, String password) {
        vkBotService.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
        vkBotService.sendMessageTo(userId,
                "Ой, извини, случайно вырвалось)\n" +
                        "➡ Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК. " +
                        "Тебе нужно просто позвать их пообщаться со мной. " +
                        "Но позволь я сначала проверю твой ЛК...");

        List<SubjectData> newSubjectsData = lstuParser.getSubjectsDataFirstTime(actualSemester);
        final var newGroup = new Group(newName)
                .setLoggedUserId(userId)
                .setLogin(login)
                .setPassword(password)
                .setSubjectsData(newSubjectsData)
                .setLastCheckDate(new Date());
        groupsRepository.updateAuthInfoAndSubjectsData(newGroup, oldName);
        newUserSubjectsListMessage(userId, newGroup);
    }

    private static String getNewScannedSemesterName () {
        Calendar now = new GregorianCalendar();
        Calendar autumnSemesterStart = new GregorianCalendar();
        autumnSemesterStart.set(Calendar.MONTH, 8);
        autumnSemesterStart.set(Calendar.DAY_OF_MONTH, 1);

        Calendar springSemesterStart = new GregorianCalendar();
        springSemesterStart.set(Calendar.MONTH, 2);
        springSemesterStart.set(Calendar.DAY_OF_MONTH, 15);

        final int year = now.get(Calendar.YEAR);
        if (now.after(springSemesterStart) && now.before(autumnSemesterStart))
            return year + "-В";
        else
            return year + "-О";
    }

    // TODO Написать подробные возможности бота в группе
    // TODO Удаление сообщения с данными входа (пока что не получилось, хотя согласно докам можно)
    // TODO Массовое отправление через один запрос с множеством айдишников
    // TODO Вынести команды и их методы в мапу

    // TODO Шифрование? (только для MongoDB Enterprise, но можно самому написать)
    // TODO функция: напомни имена и отчества преподавателей
    // TODO Распознавание сообщений на англ. раскладке
}
