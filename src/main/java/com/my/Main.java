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
import lombok.SneakyThrows;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private static final String BASIC_COMMANDS =
                    "🔷 Вывести список предметов:\n" +
                    "Предметы\n" +
                    "🔷 Узнать самую свежую информацию по предмету из ЛК:\n" +
                    "n (n - номер в моем списке предметов)\n" +
//                    "🔷 Узнать информацию от последнего обновления по всем предметам:\n" +
//                    "Предметы\n" +
//                    "🔷 Узнать самую свежую информацию по всем предметам:\n" +
//                    "Обнови предметы\n" +
                    "🔶 Показать эти команды:\n" +
                    "Команды\n" +
                    "🔶 Прекратить пользоваться ботом или сменить зарегистрированного человека:\n" +
                    "Забудь меня";

    private static final String LOGGED_USER_COMMANDS =
            BASIC_COMMANDS + "\n" +
                    "🔶 Не писать тебе, пока нет новой информации:\n"+
                    "Не пиши попусту\n"+
                    "🔶 Изменить интервал автоматического обновления:\n" +
                    "Изменить интервал на n (n - количество минут от 10, до 20160)\n";

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
                    final var newSemester = getNewScannedSemesterName();

                    for (Group group : groupsRepository.findAll()) {
                        final var checkDate = new Date();
                        if (!checkDate.after(group.getNextCheckDate())) {
                            continue;
                        }

                        if (group.getLoggedUserId() != null && !lstuAuthService.login(group.getLogin(), group.getPassword())) {
                            vkBotService.sendMessageTo(group.getLoggedUserId(),
                                    "Не удалось обновить данные из ЛК по следующей причине:\n" +
                                            "Необходимо обновить данные для входа");
                            continue;
                        }
                        vkBotService.sendMessageTo(group.getLoggedUserId(), "Началось плановое обновление");

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

                        String report;
                        if (actualSemester.equals(newSemester))
                            report = makeSubjectsDataReport(removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData));
                        else
                            report = "Данные теперь приходят из семестра " + newSemester + "\n" +
                                    makeSubjectsDataReport(newSubjectsData);

                        group.setLastCheckDate(checkDate);
                        if (!report.startsWith("Нет новой")) {
                            final var finalReport = "Плановое обновление:\n" + report;
                            vkBotService.sendLongMessageTo(group.getUsers(), finalReport);
                            nextUpdateDateMessage(group.getUsers(), group.getNextCheckDate());
                        } else {
                            if (group.isAlwaysNotifyLoggedUser())
                                vkBotService.sendMessageTo(group.getLoggedUserId(), report);
                            nextUpdateDateMessage(group.getLoggedUserId(), group.getNextCheckDate());
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
    }


    public static void main (String[] args) {
        actualSemester = getNewScannedSemesterName();
        vkBotService.setOnline(true);
        fillGroupNameByUserId();

        final var plannedSubjectsDataUpdate = new PlannedSubjectsDataUpdate();
        plannedSubjectsDataUpdate.start();

        try {
            runCycle();
        } catch (CloseAppNeedsException ignored) {}
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
                    if (userId.equals(ADMIN_VK_ID) && message.getText().equals("close")) {
                        throw new CloseAppNeedsException();
                    }
                    if (userId > 0) {
                        System.out.println(message); // TODO логирование
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

        Integer subjectIndex = tryParseSubjectIndex(messageText);
        if (subjectIndex != null) {
            getActualSubjectDataMessage(userId, group, subjectIndex);
            return;

        } else if (messageText.startsWith("изменить интервал на ")) {
            final var minutes = Long.parseLong(messageText.substring(21));
            if (group.userIsLogged(userId)) {
                if (10 <= minutes && minutes <= 20160) {
                    final long newUpdateInterval = minutes * 60 * 1000;
                    groupsRepository.updateField(groupName,
                            "updateInterval", newUpdateInterval);
                    vkBotService.sendMessageTo(userId, "Интервал изменен");
                    group.setUpdateInterval(newUpdateInterval);
                    nextUpdateDateMessage(userId, group.getNextCheckDate());
                } else
                    vkBotService.sendMessageTo(userId, "Нельзя установить такой интервал обновления");
            } else
                userInsufficientPermissionsMessage(userId);
            return;
        }

        switch (messageText) {
            case "предметы":
                vkBotService.sendMessageTo(userId, makeSubjectsListReport(group.getSubjectsData()));
                break;

            case "команды":
                vkBotService.sendMessageTo(userId,
                        (!group.userIsLogged(userId) ? BASIC_COMMANDS : LOGGED_USER_COMMANDS));
                break;

            case "не пиши попусту":
                if (group.userIsLogged(userId)) {
                    groupsRepository.updateField(groupName, "alwaysNotifyLoggedUser", false);
                    vkBotService.sendMessageTo(userId, "Хорошо");
                } else
                    userInsufficientPermissionsMessage(userId);
                break;

            case "забудь меня":
                if (group.userIsLogged(userId))
                    vkBotService.sendMessageTo(userId,
                            "➡ Эта опция полезна, если тебе нужно изменить человека, " +
                                    "зарегистрированного от имени группы или если я тебе больше не нужен. " +
                                    "После твоего ухода кому-то нужно будет сказать мне логин и пароль от своего ЛК, " +
                                    "если вы хотите продолжать пользоваться мной. " +
                                    "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                    "Я уверен, что хочу, чтобы ты забыл меня");
                else
                    vkBotService.sendMessageTo(userId,
                            "➡ Эта опция полезна будет полезна тебе, чтобы войти от имени группы после того, " +
                                    "как я забыл другого зарегистрированного человека из твоей группы, " +
                                    "или если я тебе больше не нужен. \n" +
                                    "➡ Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                    "Я уверен, что хочу, чтобы ты забыл меня");
                break;

            case "я уверен, что хочу, чтобы ты забыл меня":
                if (group.userIsLogged(userId)) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Рекомендую тебе поменять пароль в ЛК (http://lk.stu.lipetsk.ru).\n" +
                                    "Я тебя забыл. \uD83D\uDC4B\uD83C\uDFFB");
                    groupsRepository.removeLoggedUser(groupName, group.getLoggedUserId());

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

    private static void nextUpdateDateMessage (Integer userId, Date nextCheckDate) {
        nextUpdateDateMessage(Collections.singletonList(userId), nextCheckDate);
    }

    private static void nextUpdateDateMessage (Collection<Integer> userIds, Date nextCheckDate) {
        vkBotService.sendMessageTo(userIds, "Следующее обновление будет в "
                + formatDate(nextCheckDate));
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

    private static void getActualSubjectDataMessage (Integer userId, Group group, Integer subjectIndex) {
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

        newSubjectData.setId(subjectIndex);
        newSubjectData.getDocumentNames()
                .removeAll(oldSubjectData.getDocumentNames());

        if (newSubjectData.isNotEmpty()) {
            vkBotService.sendLongMessageTo(userId,
                    makeSubjectsDataReport(Collections.singletonList(newSubjectData)));
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
        final var loggedUserId = group.getLoggedUserId();
        if (!group.userIsLogged(userId)) {
            vkBotService.sendMessageTo(userId,
                    "➡ Мне не удалось проверить данные твоей группы. " +
                            "Человек, вошедший от имени группы изменил свой пароль в ЛК и не сказал мне новый пароль. " +
                            "Я скажу ему об этом сам.");
            groupsRepository.addUserTo(group.getName(), "loginWaitingUsers", userId);
        }
        vkBotService.sendMessageTo(loggedUserId,
                "➡ Похоже ты забыл сказать мне новый пароль после его обновления в ЛК." +
                        "Скажи мне новые данные для входа так " +
                        AUTH_COMMAND);
    }

    private static String makeSubjectsDataReport (List<SubjectData> subjectsData) {
        if (subjectsData.isEmpty()) {
            return "Нет новой информации по предметам";
        }
        final var builder = new StringBuilder();
        var partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            if (!data.getDocumentNames().isEmpty()) {
                partBuilder.append("➡ ").append(data.getId()).append(" ").append(data.getName()).append(": ")
                        .append(String.join(", ", data.getDocumentNames()))
                        .append("\n\n");
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("\uD83D\uDD34 Новые документы:\n").append(partBuilder);
        }
        partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            final List<MessageData> messagesData = data.getMessagesData();
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
                partBuilder.append("➡ ").append(data.getId()).append(" ").append(data.getName()).append(":\n")
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

    private static void onLoginMessages (Integer userId, @NonNull String groupName, String messageText) {
        final String[] chunks = messageText.split("\n");
        String login = chunks[1];
        String password = chunks[2];

        vkBotService.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
        if (!lstuAuthService.login(login, password)) {
            newGroupLoginFailedMessages(userId);
            return;
        }
        //vkBotService.deleteLastMessage(message);

        var optionalGroup = groupsRepository.findByGroupName(groupName);
        if (optionalGroup.isPresent()) {
            final var oldGroup = optionalGroup.get();
            if (oldGroup.isLoggedBefore()) {
                groupsRepository.updateAuthInfo(oldGroup.getName(), userId, login, password);
                groupsRepository.moveLoginWaitingUsersToUsers(oldGroup.getName());
                vkBotService.sendMessageTo(oldGroup.getLoginWaitingUsers(),
                        "Человек из твоей группы обновил пароль от ЛК. ");
            }
        } else {
            groupName = actualizeGroupName(userId, groupName);
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
                        "\uD83D\uDD34 Я поменяю имя введенной тобой группы "+ groupName +" на: "+lkGroupName+
                                ", чтобы твои одногруппники ничего не перепутали. \uD83D\uDD34");
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
                .setLoggedUserId(userId)
                .setLogin(login)
                .setPassword(password)
                .setSubjectsData(newSubjectsData)
                .setLastCheckDate(new Date());
        newGroup.getUsers().add(userId);

        groupsRepository.insert(newGroup);
        newUserSubjectsListMessage(userId, newGroup);
    }

    public static List<SubjectData> removeOldSubjectsDocuments (
            List<SubjectData> oldSubjectsData, List<SubjectData> newSubjectsData) {

        Map<String, SubjectData> oldDocumentsMap = new HashMap<>();
        for (SubjectData data : oldSubjectsData) {
            oldDocumentsMap.put(data.getName(), data);
        }

        Set<SubjectData> oldDataSet = new HashSet<>(oldSubjectsData);
        return newSubjectsData.stream()
                .map(newData -> {
                    final Set<String> newDocuments = newData.getDocumentNames();
                    if (oldDataSet.contains(newData)) {
                        final var oldSubjectData = oldDocumentsMap.get(newData.getName());
                        newDocuments.removeAll(oldSubjectData.getDocumentNames());
                    }
                    return newData;
                })
                .filter(SubjectData::isNotEmpty)
                .collect(Collectors.toList());
    }

    private static String getNewScannedSemesterName () {
        Calendar now = new GregorianCalendar();
        Calendar autumnSemesterStart = new GregorianCalendar();
        autumnSemesterStart.set(Calendar.MONTH, Calendar.AUGUST);
        autumnSemesterStart.set(Calendar.DAY_OF_MONTH, 18);

        Calendar springSemesterStart = new GregorianCalendar();
        springSemesterStart.set(Calendar.MONTH, Calendar.JANUARY);
        springSemesterStart.set(Calendar.DAY_OF_MONTH, 15);

        final int year = now.get(Calendar.YEAR);
        if (now.after(springSemesterStart) && now.before(autumnSemesterStart))
            return year + "-В";
        else
            return year + "-О";
    }

    private static Integer tryParseSubjectIndex (String messageText) {
        try {
            return Integer.parseInt(messageText);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // TODO Heroku

    // TODO Удаление сообщения с данными входа (пока что не получилось, хотя согласно докам можно)
    // TODO Шифрование? (только для MongoDB Enterprise, но можно самому написать)
    // TODO функция: напомни имена и отчества преподавателей
    // TODO Распознавание сообщений на англ. раскладке

    // TODO Для массового распространения бота:
    //  Написать подробные возможности бота в группе
    //  добавить вход участника группы через проверочный код
    //  добавить асинхронное скачивание данных из лк по группам
    //  Добавить ssl? и юзера в бд с паролем

}
