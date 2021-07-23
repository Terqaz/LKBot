package com.my;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.models.MessageData;
import com.my.models.SubjectData;
import com.my.services.LstuAuthService;
import com.my.services.NewInfoService;
import com.my.services.VkBotService;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import com.vk.api.sdk.objects.messages.Message;
import lombok.*;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

    static final String SUBJECTS_DATA_FILENAME_PREFIX = "info_";

    static final ObjectMapper objectMapper = new ObjectMapper();

    static final VkBotService vkBotService = VkBotService.getInstance();
    static final LstuAuthService lstuAuthService = new LstuAuthService();
    static final NewInfoService newInfoService = new NewInfoService();

    static final int ADMIN_VK_ID = 173315241;

    static final Map<Integer, UserContext> userContexts = new HashMap<>();
    static final Map<String, GroupData> groupDataByGroupName = new HashMap<>();

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

    private static final Pattern groupNamePattern =
            Pattern.compile("^((т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,5}-)(п-)?\\d{2}(-\\d)?$");

    private static final String BASIC_COMMANDS =
            "Если я не отвечу, повтори свою просьбу." +
            "-- Вывести список предметов:\n" +
            "Список предметов\n" +
            "-- Узнать самую свежую информацию по предмету из ЛК:\n" +
            "Предмет n (n - номер в списке выше)\n" +
            "-- Узнать последнюю проверенную информацию по всем предметам:\n" +
            "Предметы\n" +
            "-- Узнать самую свежую информацию по всем предметам:\n" +
            "Обнови предметы\n" +
            "-- Показать эти команды:\n" +
            "Команды\n" +
            "-- Прекратить пользоваться ботом или сменить зарегистрированного человека:\n" +
            "Забудь меня";

    private static final String LOGGED_USER_COMMANDS =
            BASIC_COMMANDS + "\n" +
            "-- Не писать тебе, пока нет новой информации:\n"+
            "Не пиши попусту\n"+
            "-- Изменить интервал автоматического обновления:\n" +
            "Изменить интервал на n (n - количество минут от 30, до 720)\n";
    
    private static final String AUTH_COMMAND =
            "(рекомендую сразу удалить это сообщение):\n" +
            "Хочу войти в ЛК\n" +
            "Мой_логин\n" +
            "Мой_пароль";

    private static final Set<UserContext> updateWaitingUsers = new HashSet<>();
    private static final MultiValuedMap<String, Integer> loginWaitingUsers = new ArrayListValuedHashMap<>();

    private static String scannedSemester;

    @Data
    @RequiredArgsConstructor
    @JsonIgnoreProperties({"nextCheckDate"})
    private static class GroupData {
        @NonNull
        private Integer loggedUserId;
        @NonNull
        private String groupName;

        @NonNull
        private String login;
        @NonNull
        private String password;

        @NonNull
        @Setter(AccessLevel.PRIVATE)
        private List<SubjectData> subjectsData;

        @NonNull
        private Date lastCheckDate;
        private long updateIntervalMillis = 12L * 3600 * 1000;
        private boolean updatingNow = false;

        private boolean alwaysNotifyLoggedUser = true;

        public void updateSubjectsData (List<SubjectData> subjectsData, Date lastCheckDate) {
            this.subjectsData = subjectsData;
            this.lastCheckDate = lastCheckDate;
        }

        public Date getNextCheckDate () {
            return new Date(lastCheckDate.getTime() + updateIntervalMillis);
        }
    }

    @Data
    @RequiredArgsConstructor
    private static class UserContext {
        @NonNull
        Integer userId;
        @NonNull
        String groupName;
    }

    public static void main (String[] args) throws InterruptedException {
        scannedSemester = getNewScannedSemesterName();

        vkBotService.updateTs();
        while (true) {
            final List<Message> messages = vkBotService.getNewMessages();
            if (!messages.isEmpty()) {
                for (Message message : messages) {
                    final Integer userId = message.getFromId();
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
            sendReportsToUpdateWaitingUsers();
            plannedSubjectsDataUpdate();
            vkBotService.updateTs();
            Thread.sleep(100);
        }
    }

    private static void sendReportsToUpdateWaitingUsers () {
        for (UserContext context : updateWaitingUsers) {
            vkBotService.sendMessageTo(context.getUserId(),
                    makeSubjectsDataReport(groupDataByGroupName.get(context.getGroupName()).getSubjectsData()));
        }
        updateWaitingUsers.clear();
    }

    private static void plannedSubjectsDataUpdate () {
        final var newSemesterName = getNewScannedSemesterName();
        groupDataByGroupName.keySet().forEach(checkingGroup -> {
            final var checkDate = new Date();
            final var groupData = groupDataByGroupName.get(checkingGroup);

            if (!checkDate.after(groupData.getNextCheckDate())) {
                return;
            }

            if (lstuAuthService.login(groupData.getLogin(), groupData.getPassword())) {
                final var oldSubjectsData = groupData.getSubjectsData();

                final List<SubjectData> newSubjectsData;
                String report;
                if (scannedSemester.equals(newSemesterName)) {
                    newSubjectsData = newInfoService.getNewSubjectsData(oldSubjectsData, groupData.getLastCheckDate());
                    report = makeSubjectsDataReport(NewInfoService.removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData));
                } else {
                    newSubjectsData = newInfoService.getSubjectsDataFirstTime(scannedSemester);
                    report = "Данные теперь приходят из семестра " + newSemesterName + "\n" +
                            makeSubjectsDataReport(newSubjectsData);
                }
                lstuAuthService.logout();
                groupData.updateSubjectsData(newSubjectsData, checkDate);
                writeGroupDataFile(groupData);

                if (!report.startsWith("Нет новой")) {
                    final var finalReport = "Плановое обновление:\n" + report;
                    getGroupUsers(checkingGroup).forEach(userId1 -> {
                                vkBotService.sendLongMessageTo(userId1, finalReport);
                                nextUpdateDateMessage(userId1, groupData);
                            });
                } else {
                    if (groupData.isAlwaysNotifyLoggedUser())
                        vkBotService.sendMessageTo(groupData.getLoggedUserId(), "Плановое обновление:\n" + report);
                }
            } else {
                vkBotService.sendMessageTo(groupData.getLoggedUserId(),
                        "Не удалось обновить данные из ЛК по следующей причине:\n" +
                                "Необходимо обновить данные для входа");
            }
        });
    }

    private static Stream<Integer> getGroupUsers (String checkingGroup) {
        return userContexts.values().stream()
                .filter(userContext -> userContext.getGroupName().equals(checkingGroup))
                .map(UserContext::getUserId);
    }

    private static void executeBotDialog (Integer userId, String messageText) {
        if (messageText.startsWith("Я из ")) {
            final var groupName = messageText.substring(5);
            if (groupNamePattern.matcher(groupName).find()) {
                userContexts.put(userId, new UserContext(userId, groupName));
                if (!groupRegistered(groupName)) {
                    newUserMessage(userId);
                } else {
                    final var groupData = groupDataByGroupName.get(groupName);
                    if (groupData.getLoggedUserId() != 0) {
                        newUserOldGroupMessages(userId, groupData);
                    } else {
                        vkBotService.sendMessageTo(userId,
                        "В этой группе был человек, вошедший от своего имени, но теперь его нет." +
                                "Ты хочешь стать им?\n");
                        newUserMessage(userId);
                    }
                }
            } else {
                vkBotService.sendMessageTo(userId,
                "Мне кажется, ты ввел неправильное имя для группы\n");
            }

        } else if (messageText.startsWith("Хочу войти в ЛК")) {
            tryToLoginMessages(userId, userContexts.get(userId).getGroupName(), messageText);

        } else {
            if (!userContexts.containsKey(userId)) {
                vkBotService.sendMessageTo(userId, "Напиши из какой ты группы (например: \"Я из ПИ-19\"):");
                return;
            }
            final var userContext = userContexts.get(userId);
            final var groupName = userContext.getGroupName();
            final var groupData = groupDataByGroupName.get(groupName);
            messageText = messageText.toLowerCase();

            if (messageText.startsWith("предмет ")) {
                getActualSubjectDataMessage(userId, groupData, messageText);

            } else if (messageText.equals("предметы")) {
                vkBotService.sendLongMessageTo(userId,
                        makeSubjectsDataReport(groupData.getSubjectsData()));
                nextUpdateDateMessage(userId, groupData);

            } else if (messageText.equals("список предметов")) {
                vkBotService.sendMessageTo(userId,
                        makeSubjectsListReport(groupData.getSubjectsData()));

            } else if (messageText.equals("обнови предметы")) {
                if (!groupData.isUpdatingNow()) {
                    updateSubjectsDataWarningMessage(userId);
                } else {
                    subjectsDataIsUpdatingMessage(userContext);
                }

            } else if (messageText.equals("сканируй все равно")) {
                vkBotService.unsetKeyboard();
                if (!groupData.isUpdatingNow()) {
                    updateSubjectsDataMessages(userId, groupData);
                } else {
                    subjectsDataIsUpdatingMessage(userContext);
                }

            } else if (messageText.startsWith("изменить интервал на ")) {
                final var minutes = Long.parseLong(messageText.substring(21));
                if (userIsLogged(userId, groupData)) {
                    if (30 <= minutes && minutes <= 720) {
                        groupData.setUpdateIntervalMillis(minutes * 60 * 1000);
                        vkBotService.sendMessageTo(userId, "Интервал изменен");
                    } else {
                        vkBotService.sendMessageTo(userId, "Нельзя установить такой интервал обновления");
                    }
                } else {
                    userInsufficientPermissionsMessage(userId);
                }

            } else if (messageText.equals("команды")) {
                vkBotService.sendMessageTo(userId,
                        (!userIsLogged(userId, groupData) ? BASIC_COMMANDS : LOGGED_USER_COMMANDS));

            } else if (messageText.equals("ладно, отдыхай")) {
                vkBotService.unsetKeyboard();
                vkBotService.sendMessageTo(userId, "Спасибо тебе, человек!");

            } else if (messageText.equals("я готов на все ради своей группы!")) {
                if (!groupRegistered(groupName)) {
                    vkBotService.unsetKeyboard();
                    vkBotService.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так " +
                                    AUTH_COMMAND);
                } else {
                    groupAlreadyRegisteredMessage(userId);
                }
            } else if (messageText.startsWith("лучше скажу другому")) {
                if (!groupRegistered(groupName)) {
                    vkBotService.unsetKeyboard();
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Я скажу, когда он сообщит мне свои логин и пароль");
                    loginWaitingUsers.put(groupName, userId);
                } else {
                    groupAlreadyRegisteredMessage(userId);
                }
            } else if (messageText.startsWith("я ошибся при вводе группы")) {
                vkBotService.unsetKeyboard();
                userContexts.remove(userId);
                vkBotService.sendMessageTo(userId, "Введи новое имя для группы (например: \"Я из ПИ-19\"):");

            } else if (messageText.equals("не пиши попусту")) {
                if (userIsLogged(userId, groupData)) {
                    groupData.setAlwaysNotifyLoggedUser(false);
                    vkBotService.sendMessageTo(userId, "Хорошо");
                } else {
                    userInsufficientPermissionsMessage(userId);
                }

            } else if (messageText.equals("забудь меня")) {
                if (userIsLogged(userId, groupData)) {
                    vkBotService.sendMessageTo(userId,
                            "Эта опция полезна будет полезна, если тебе нужно изменить человека, " +
                            "зарегистрированного от имени группы или если я тебе больше не нужен.\n" +
                            "После твоего ухода кому-то нужно будет сказать мне логин и пароль от своего ЛК, " +
                            "если вы хотите продолжать пользоваться мной.\n" +
                            "В дальнейшем у тебя есть возможность вернуться обратно в свою группу.\n" +
                            "Если ты уверен, что хочешь прекратить пользоваться ботом, то напиши:\n" +
                            "Я уверен, что хочу, чтобы ты забыл меня");
                } else {
                    vkBotService.sendMessageTo(userId,
                            "Эта опция полезна будет полезна тебе, чтобы войти от имени группы после того, " +
                                    "как я забыл другого зарегистрированного человека из твоей группы, " +
                                    "или если я тебе больше не нужен. \n" +
                                    "Если ты уверен, что правильно все делаешь, то напиши:\n" +
                                    "Я уверен, что хочу, чтобы ты забыл меня");
                }

            } else if (messageText.equals("я уверен, что хочу, чтобы ты забыл меня")) {
                if (userIsLogged(userId, groupData)) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Рекомендую тебе поменять пароль в ЛК (http://lk.stu.lipetsk.ru).\n" +
                                    "Я тебя забыл.");
                    groupData.setLoggedUserId(0);
                    groupData.setLogin("0");
                    groupData.setPassword("0");
                    getGroupUsers(groupName).forEach(groupUserId -> vkBotService.sendMessageTo(groupUserId,
                            "Человек больше не зарегистрирован от имени твоей группы.\n" +
                                    "Теперь кому-то из вас стоит написать мне \"Забудь меня\", " +
                                    "чтобы зарегистрироваться от имени группы"));
                } else {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Я тебя забыл.");
                }
                userContexts.remove(userId);

            } else {
                vkBotService.sendMessageTo(userId, "Я не понял тебя");
            }
        }
    }

    private static boolean groupRegistered (String groupName) {
        final var data = groupDataByGroupName.get(groupName);
        return data != null && data.getLoggedUserId() != 0;
    }

    private static void groupAlreadyRegisteredMessage (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Ой, похоже ты опоздал! Эту группу уже успели зарегистрировать.\n" +
                "Держи команды:\n" + BASIC_COMMANDS);
    }

    private static void nextUpdateDateMessage (Integer userId, GroupData groupData) {
        vkBotService.sendMessageTo(userId, "Следующее обновление будет в "
                + formatDate(groupData.getNextCheckDate()));
    }

    private static void userInsufficientPermissionsMessage (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Я разрешаю эту операцию только человеку, вошедшему от имени группы");
    }

    private static boolean userIsLogged (Integer userId, GroupData groupData) {
        return Objects.equals(groupData.getLoggedUserId(), userId);
    }

    private static void newUserMessage (Integer userId) {
        vkBotService.sendMessageTo(userId, keyboard1,
        "Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию " +
                "для тебя и твоих одногруппников.\n" +
                "А еще несколько минут, чтобы ты мне объяснил, чьи сообщения самые важные.\n" +
                "Можешь мне довериться ;-)\n" +
                "Если ты мне не доверяешь, то позволь ввести пароль другому человеку из твоей группы.\n" +
                "Этот человек сможет менять интервал обновления данных о предметах из ЛК.\n" +
                "Если нет новых данных о предметах, то я напишу об этом только ему.\n" +
                "Обещаю не писать тебе когда не надо.\n" +
                "Ты всегда можешь изучить мой внутренний мир по этой ссылке: https://github.com/Terqaz/LKBot\n" +
                "И иногда обратиться к этому человеку за помощью: https://vk.com/terqaz");
    }

    private static void newUserOldGroupMessages (Integer userId, GroupData groupData) {
        vkBotService.sendMessageTo(userId, "О, я знаю эту группу!");
        newUserSubjectsListMessage(userId, groupData);
    }

    private static void newUserSubjectsListMessage (Integer userId, GroupData groupData) {
        vkBotService.sendMessageTo(userId,
                "Теперь я могу вывести тебе последнюю информацию из ЛК по данным предметам\n" +
                "(обновление было в " + formatDate(groupData.getLastCheckDate()) + "):\n" +
                makeSubjectsListReport(groupData.getSubjectsData()) + "\n");
        vkBotService.sendMessageTo(userId, "Также теперь ты можешь использовать эти команды:\n" +
                (!userIsLogged(userId, groupData) ? BASIC_COMMANDS : LOGGED_USER_COMMANDS));
    }

    private static String makeSubjectsListReport (List<SubjectData> subjectsData) {
        var stringBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            stringBuilder.append(data.getId()).append(" ")
                    .append(data.getSubjectName()).append("\n");
        }
        return stringBuilder.toString();
    }

    private static void getActualSubjectDataMessage (Integer userId, GroupData groupData, String messageText) {
        var subjectIndex = Integer.parseInt(messageText.substring(8));
        if (lstuAuthService.login(groupData.getLogin(), groupData.getPassword())) {

            final var optionalSubjectData = groupData.getSubjectsData().stream()
                    .filter(subjectData1 -> subjectData1.getId() == subjectIndex)
                    .findFirst();
            if (optionalSubjectData.isEmpty()) {
                vkBotService.sendMessageTo(userId, "Неправильный номер предмета");
                return;
            }
            final var oldSubjectData = optionalSubjectData.get();

            SubjectData newSubjectData = newInfoService.getNewSubjectData(
                    oldSubjectData.getSubjectName(), oldSubjectData.getLocalUrl(), groupData.getLastCheckDate());
            lstuAuthService.logout();

            newSubjectData.removeOldDocuments(oldSubjectData);

            if (newSubjectData.isNotEmpty()) {
                vkBotService.sendLongMessageTo(userId,
                        makeSubjectsDataReport(Collections.singletonList(newSubjectData)));
            } else {
                vkBotService.sendMessageTo(userId,
                        "Нет новой информации по предмету " + newSubjectData.getSubjectName());
            }
        } else {
            repeatLoginFailedMessages(userId, groupData);
        }
    }

    private static void updateSubjectsDataWarningMessage (Integer userId) {
        vkBotService.sendMessageTo(userId, keyboard2,
                "Это может быть довольно долгая операция. Я и так выполняю ее регулярно\n" +
                        "Я такой же ленивый как и ты, человек. Может мне не стоит сканировать весь ЛК?");

    }

    private static void updateSubjectsDataMessages (Integer userId, GroupData groupData) {
        vkBotService.sendMessageTo(userId,
                "Ладно, можешь пока отдохнуть\n" +
                        "Я тебе напишу, как проверю");
        if (lstuAuthService.login(groupData.getLogin(), groupData.getPassword())) {
            groupData.setUpdatingNow(true);
            final var checkDate = new Date();

            final var oldSubjectsData = groupData.getSubjectsData();
            List<SubjectData> newSubjectsData = newInfoService.getNewSubjectsData(oldSubjectsData, groupData.getLastCheckDate());

            lstuAuthService.logout();

            groupData.updateSubjectsData(newSubjectsData, checkDate);
            writeGroupDataFile(groupData);
            groupData.setUpdatingNow(false);

            String report = makeSubjectsDataReport(
                    NewInfoService.removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData));
            vkBotService.sendLongMessageTo(userId, report);
        } else {
            repeatLoginFailedMessages(userId, groupData);
        }
    }

    private static void subjectsDataIsUpdatingMessage (UserContext userContext) {
        vkBotService.sendMessageTo(userContext.getUserId(), "Данные о предметах уже обновляет кто-то другой\n" +
                "Я пришлю тебе отчет после обновления");
        updateWaitingUsers.add(userContext);
    }

    private static void newGroupLoggedMessages (Integer userId, String groupName, String login, String password) {
        vkBotService.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
        vkBotService.sendMessageTo(userId,
                "Ой, извини, случайно вырвалось)\n" +
                        "Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК\n" +
                        "Тебе нужно просто позвать их пообщаться со мной\n" +
                        "Но позволь я сначала проверю твой ЛК"); //и попытаюсь определить преподавателей...");

        List<SubjectData> newSubjectsData = newInfoService.getSubjectsDataFirstTime(scannedSemester);
        final var newGroupData = new GroupData(userId, groupName, login, password, newSubjectsData, new Date());
        groupDataByGroupName.put(
                groupName,
                newGroupData
        );
        writeGroupDataFile(newGroupData);
        newUserSubjectsListMessage(userId, newGroupData);
        notifyLoginWaitingUsers(groupName);
    }

    private static void notifyLoginWaitingUsers (String groupName) {
        loginWaitingUsers.get(groupName).forEach(userId ->
                vkBotService.sendMessageTo(userId,
                        "Человек из твоей группы зарегистрировался в ЛК. " +
                                "Теперь ты можешь использовать эти команды:\n" + BASIC_COMMANDS));
        loginWaitingUsers.remove(groupName);
    }

    private static void newLoginFailedMessages (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Что-то пошло не так. \n" +
                "Либо твои логин и пароль неправильные, либо я неправильно их прочитал");
    }

    private static void repeatLoginFailedMessages (Integer userId, GroupData groupData) {
        String messageToLoggedUser =
                "Похоже ты забыл сказать мне новый пароль после обновления в ЛК\n" +
                        "Скажи мне новые данные для входа так " +
                        AUTH_COMMAND;

        final var loggedUserId = groupData.getLoggedUserId();
        if (!userIsLogged(userId, groupData)) {
            vkBotService.sendMessageTo(userId,
                    "Мне не удалось проверить данные твоей группы \n" +
                            "Человек, вошедший от имени группы изменил свой пароль в ЛК и не сказал мне новый пароль\n" +
                            "Я скажу ему об этом сам. Когда он введет новый пароль, напиши мне еще раз");

            vkBotService.sendMessageTo(loggedUserId, "Привет. " + messageToLoggedUser);
        } else {
            vkBotService.sendMessageTo(loggedUserId, messageToLoggedUser);
        }

    }

    private static String makeSubjectsDataReport (List<SubjectData> subjectsData) {
        if (subjectsData.isEmpty()) {
            return "Нет новой информации по предметам";
        }
        final var builder = new StringBuilder();
        var partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            if (!data.getNewDocumentNames().isEmpty()) {
                partBuilder.append(data.getSubjectName())
                        .append(": ")
                        .append(String.join(", ", data.getNewDocumentNames()))
                        .append("\n\n");
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("Новые документы:\n").append(partBuilder);
        }
        partBuilder = new StringBuilder();
        for (SubjectData subjectData : subjectsData) {
            final List<MessageData> messagesData = subjectData.getMessagesData();
            if (!messagesData.isEmpty()) {
                var messagesBuilder = new StringBuilder();
                for (MessageData messageData : messagesData) {
                    final String shortName = makeShortSenderName(messageData.getSender());
                    messagesBuilder.append(shortName)
                            .append(" в ")
                            .append(formatDate(messageData.getDate()))
                            .append(":\n")
                            .append(messageData.getComment())
                            .append("\n\n");
                }
                partBuilder.append("-- ")
                        .append(subjectData.getSubjectName())
                        .append(":\n")
                        .append(messagesBuilder);
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("Новые сообщения:\n").append(partBuilder);
        }
        return builder.toString();
    }

    private static String formatDate (Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }

    private static String makeShortSenderName (String name) {
        final String[] chunks = name.split(" ");
        return chunks[0] + " " + chunks[1].charAt(0) + " " + chunks[2].charAt(0);
    }

    private static void tryToLoginMessages (Integer userId, @NonNull String groupName, String messageText) {
        final String[] chunks = messageText.split("\n");
        String login = chunks[1];
        String password = chunks[2];

        vkBotService.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
        if (lstuAuthService.login(login, password)) {
            var groupData = groupDataByGroupName.get(groupName);
            // TODO нужно удостовериться согласно ЛК, что человек действительно из указанной им группы
            if (groupData == null) {
                newGroupLoggedMessages(userId, groupName, login, password);
            } else {
                vkBotService.sendMessageTo(userId, "Спасибо, что обновил данные :-)");
            }
            lstuAuthService.logout();
        } else {
            newLoginFailedMessages(userId);
        }
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

    private static GroupData readGroupDataFile (String groupName) {
        try {
            return objectMapper.readValue(new File(makeFileName(groupName)), new TypeReference<>() {});
        } catch (Exception e) { //JsonProcessingException
            return null;
        }
    }

    private static void writeGroupDataFile (GroupData groupData) {
        if (groupData.getSubjectsData().isEmpty()) {
            return;
        }
        try {
            objectMapper.writeValue(new File(makeFileName(groupData.getGroupName())), groupData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String makeFileName (String groupName) {
        return SUBJECTS_DATA_FILENAME_PREFIX + groupName + "_" + scannedSemester + ".json";
    }
}
