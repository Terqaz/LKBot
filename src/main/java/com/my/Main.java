package com.my;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.models.MessageData;
import com.my.models.SubjectData;
import com.my.services.LstuAuthService;
import com.my.services.NewInfoService;
import com.my.services.VkBotService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import com.vk.api.sdk.objects.messages.Message;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {

    static final String SUBJECTS_DATA_FILENAME_PREFIX = "info_";

    static final ObjectMapper objectMapper = new ObjectMapper();

    static final VkBotService vkBotService = VkBotService.getInstance();
    static final int ADMIN_VK_ID = 173315241;
    static final Map<Integer, UserContext> userContexts = new HashMap<>();
    static final Map<String, GroupData> groupDataByGroupName = new HashMap<>();

    static final LstuAuthService lstuAuthService = new LstuAuthService();
    static final NewInfoService newInfoService = new NewInfoService();

    static final Keyboard keyboard1 = new Keyboard().setOneTime(true)
            .setButtons(Arrays.asList(
                    Collections.singletonList(
                            VkBotService.generateButton("Я готов на все ради своей группы!", KeyboardButtonColor.POSITIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Лучше скажу другому", KeyboardButtonColor.NEGATIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Я ошибся при вводе группы", KeyboardButtonColor.DEFAULT))
            ));

    static final Keyboard keyboard2 = new Keyboard().setOneTime(true)
            .setButtons(Arrays.asList(
                    Collections.singletonList(
                            VkBotService.generateButton("Сканируй все равно", KeyboardButtonColor.POSITIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Ладно, отдыхай", KeyboardButtonColor.NEGATIVE))
            ));

    private static final Pattern groupNamePattern =
            Pattern.compile("^((т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,5}-)(п-)?\\d{2}(-\\d)?$");

    private static final String BASIC_COMMANDS =
            "-- Вывести список предметов:\n" +
            "Список предметов\n" +
            "-- Узнать самую свежую информацию по предмету:\n" +
            "Предмет n (n - номер в списке выше)\n" +
            "-- Узнать последнюю проверенную информацию по всем предметам:\n" +
            "Предметы\n" +
            "-- Узнать самую новую информацию по всем предметам:\n" +
            "Обнови предметы\n" +
            "-- Показать эти команды:\n" +
            "Команды";
    
    private static final String AUTH_COMMAND =
            "Хочу войти в ЛК\n" +
            "Мой_логин\n" +
            "Мой_пароль";

    private static String scannedSemester;

    @Data
    @RequiredArgsConstructor
    private static class GroupData {
        @NonNull
        private Integer loggedUserId;
        @NonNull
        private String groupName;

        @NonNull
        private String login;
        @NonNull
        private String password;

        private Date lastCheckDate;
        private TimeInterval updateInterval = TimeInterval.HALF_DAY;

        @NonNull
        private List<SubjectData> subjectsData;
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
                    System.out.println(message.toString());
                    final Integer userId = message.getFromId();

                    try {
                        executeBotDialog(userId, message.getText());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    plannedSubjectsDataUpdate();
                }
            }
            vkBotService.updateTs();
            Thread.sleep(500);
        }
    }

    private static void executeBotDialog (Integer userId, String messageText) {
        try {
            if (messageText.startsWith("Я из ")) {
                String groupName = messageText.substring(5);
                if (groupNamePattern.matcher(groupName).find()) {
                    userContexts.put(userId, new UserContext(userId, groupName));
                    if (!groupDataByGroupName.containsKey(groupName)) {
                        newUserAndGroupMessage(userId);
                    } else {
                        newUserOldGroupMessages(userId, groupName);
                    }
                } else {
                    vkBotService.sendMessageTo(userId,
                    "Мне кажется, ты ввел неправильное имя для группы\n");
                }
            } else {
                final var groupName = userContexts.get(userId).getGroupName();
                messageText = messageText.toLowerCase();

                if (messageText.startsWith("предмет ")) {
                    getActualSubjectDataMessage(userId, groupName, messageText);

                } else if (messageText.equals("предметы")) {
                    vkBotService.sendMessageTo(userId,
                            makeSubjectsDataReport(groupDataByGroupName.get(groupName).getSubjectsData()));

                } else if (messageText.equals("список предметов")) {
                    vkBotService.sendMessageTo(userId,
                            makeSubjectsListReport(groupDataByGroupName.get(groupName).getSubjectsData()));

                } else if (messageText.equals("обнови предметы")) {
                    updateSubjectsDataWarningMessage(userId);

                } else if (messageText.equals("сканируй все равно")) {
                    updateSubjectsDataMessages(userId, groupName);

                } else if (messageText.startsWith("команды")) {
                    vkBotService.sendMessageTo(userId, BASIC_COMMANDS);

                } else if (messageText.equals("ладно, отдыхай")) {
                    vkBotService.sendMessageTo(userId, "Спасибо тебе, человек!");

                } else if (messageText.equals("я готов на все ради своей группы!")) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так:\n" +
                                    AUTH_COMMAND);

                } else if (messageText.startsWith("лучше скажу другому")) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо. Когда он введет свой пароль ты сможешь использовать эти команды:\n" +
                                    BASIC_COMMANDS);

                } else if (messageText.startsWith("я ошибся при вводе группы")) {
                    userContexts.remove(userId);
                    vkBotService.sendMessageTo(userId, "Введи новое имя для группы (например: \"Я из ПИ-19\"):");

                } else if (messageText.startsWith("хочу войти в лк")) {
                    tryToLoginMessages(userId, groupName, messageText);

//                 } else if (messageText.startsWith("Обнови")) {
//                    final String[] chunks = messageText.split(" ");
//                    Integer academicNumber = Integer.parseInt(chunks[1]);
//                    String academicName = chunks[2] + chunks[3] + chunks[4];
//                    updateAcademicName(userContext.getGroupName(), academicNumber, academicName);

                } else if (!userContexts.containsKey(userId)) {
                    vkBotService.sendMessageTo(userId, "Напиши из какой ты группы (например: \"Я из ПИ-19\"):");

                } else {
                    vkBotService.sendMessageTo(userId, "Я не понял тебя");
                }
            }
        } catch (ApiException | ClientException e) {
            e.printStackTrace();
        }
    }

    private static void plannedSubjectsDataUpdate () {
        final var newSemesterName = getNewScannedSemesterName();
        groupDataByGroupName.keySet().forEach(checkingGroup -> {
            final var checkDate = new Date();

            var oldGroupData = groupDataByGroupName.get(checkingGroup);

            if (checkDate.getTime() < oldGroupData.getLastCheckDate().getTime() +
                    oldGroupData.getUpdateInterval().getValue()) {
                return;
            }

            if (lstuAuthService.login(oldGroupData.getLogin(), oldGroupData.getPassword())) {

                final var oldSubjectsData = oldGroupData.getSubjectsData();

                final List<SubjectData> newSubjectsData;
                String report;
                if (scannedSemester.equals(newSemesterName)) {
                    newSubjectsData = newInfoService.getNewSubjectsData(oldSubjectsData, oldGroupData.getLastCheckDate());
                    report = makeSubjectsDataReport(NewInfoService.removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData));
                } else {
                    newSubjectsData = newInfoService.getSubjectsDataFirstTime(scannedSemester);
                    report = "Данные теперь приходят из семестра " + newSemesterName + makeSubjectsDataReport(newSubjectsData);
                }
                lstuAuthService.logout();
                updateGroupDataFile(oldGroupData, newSubjectsData, checkDate);

                userContexts.values().stream()
                        .filter(userContext -> userContext.getGroupName().equals(checkingGroup))
                        .map(UserContext::getUserId)
                        .forEach(userId1 -> vkBotService.sendMessageTo(userId1, report));
            } else {
                vkBotService.sendMessageTo(oldGroupData.getLoggedUserId(),
                        "Не удалось обновить данные из ЛК по следующей причине:\n" +
                                "Необходимо обновить данные для входа");
            }
        });
    }

    private static void newUserAndGroupMessage (Integer userId) {
        vkBotService.sendMessageTo(userId, keyboard1,
        "Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию " +
                "для тебя и твоих одногруппников\n" +
                "А еще несколько минут, чтобы ты мне объяснил, чьи сообщения самые важные\n" +
                "Можешь мне довериться ;-)\n" +
                "Если ты мне не доверяешь, то позволь ввести пароль другому человеку из твоей группы\n" +
                "Ты всегда можешь изучить мой внутренний мир по этой ссылке: https://github.com/Terqaz/LKBot\n" +
                "И обратиться к этому человеку за помощью (ну, почти всегда): https://vk.com/terqaz");
    }

    private static void newUserOldGroupMessages (Integer userId, String groupName) {
        vkBotService.sendMessageTo(userId,"О, я знаю эту группу!");
        newUserSubjectsListMessage(userId, readGroupDataFile(groupName));
    }

    private static void newUserSubjectsListMessage (Integer userId, GroupData data) {
        vkBotService.sendMessageTo(userId,
                "Теперь я могу вывести тебе последнюю информацию из ЛК по данным предметам\n" +
                "(обновление было в " + formatDate(data.getLastCheckDate()) + "):\n" +
                makeSubjectsListReport(data.getSubjectsData()) + "\n");
        vkBotService.sendMessageTo(userId,"Также теперь ты можешь использовать эти команды:\n" + BASIC_COMMANDS);
    }

    private static String makeSubjectsListReport (List<SubjectData> subjectsData) {
        var stringBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            stringBuilder.append(data.getId()).append(" ")
                    .append(data.getSubjectName()).append("\n");
        }
        return stringBuilder.toString();
    }

    private static void getActualSubjectDataMessage (Integer userId, String groupName, String messageText) {
        var subjectIndex = Integer.parseInt(messageText.substring(8));

        final GroupData groupData = groupDataByGroupName.get(groupName);

        if (lstuAuthService.login(groupData.getLogin(), groupData.getPassword())) {

            final SubjectData oldSubjectData = groupData.getSubjectsData().stream()
                    .filter(subjectData1 -> subjectData1.getId() == subjectIndex)
                    .findFirst().orElse(null);

            SubjectData newSubjectData = newInfoService.getNewSubjectData(
                    oldSubjectData.getSubjectName(), oldSubjectData.getLocalUrl(), groupData.getLastCheckDate());
            lstuAuthService.logout();

            newSubjectData.removeOldDocuments(oldSubjectData);

            if (newSubjectData.isNotEmpty()) {
                vkBotService.sendMessageTo(userId,
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
                "Это самая долгая операция. Я итак выполняю ее регулярно\n" +
                        "Я такой же ленивый как и ты, человек. Может мне не стоит сканировать весь ЛК?");
    }

    private static void updateSubjectsDataMessages (Integer userId, String groupName)
            throws ClientException, ApiException {
        vkBotService.sendMessageTo(userId,
                "Ладно, уговорил. Можешь пока отдохнуть\n" +
                        "Я тебе напишу, как проверю");

        var oldGroupData = groupDataByGroupName.get(groupName);
        if (lstuAuthService.login(oldGroupData.getLogin(), oldGroupData.getPassword())) {
            final var checkDate = new Date();

            final var oldSubjectsData = oldGroupData.getSubjectsData();
            List<SubjectData> newSubjectsData = newInfoService.getNewSubjectsData(oldSubjectsData, oldGroupData.getLastCheckDate());

            lstuAuthService.logout();

            updateGroupDataFile(oldGroupData, newSubjectsData, checkDate);

            String report = makeSubjectsDataReport(
                    NewInfoService.removeOldSubjectsDocuments(oldSubjectsData, newSubjectsData));

            vkBotService.sendMessageTo(userId, report);
        } else {
            repeatLoginFailedMessages(userId, oldGroupData);
        }
    }

    private static void newGroupLoggedMessages (Integer userId, String groupName, String login, String password) {
        vkBotService.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
        vkBotService.sendMessageTo(userId,
                "Ой, извини, случайно вырвалось)\n" +
                        "Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК\n" +
                        "Тебе нужно просто позвать их пообщаться со мной\n" +
                        "Но позволь я сначала проверю твой ЛК"); //и попытаюсь определить преподавателей...");

        List<SubjectData> newSubjectsData = newInfoService.getSubjectsDataFirstTime(scannedSemester);
        final var newGroupData = new GroupData(userId, groupName, login, password, newSubjectsData);
        groupDataByGroupName.put(
                groupName,
                newGroupData
        );
        writeGroupDataFile(newGroupData);
        newUserSubjectsListMessage(userId, newGroupData);

//        StringBuilder stringBuilder = new StringBuilder("Преподаватели:\n");
//        int i = 1;
//        for (SubjectData subjectData : getInfoFirstTime("2021-В", userContext.getGroupName())) {
//            stringBuilder.append(i).append(" ").append(subjectData.getSubjectName()).append("\n")
//                    .append("- ").append(NewInfoService.findPrimaryAcademic(subjectData.getMessagesData())).append("\n");
//            i++;
//        }
//        vkBotService.sendMessageTo(userId, stringBuilder.toString());
//        vkBotService.sendMessageTo(userId,
//                "Если я неправильно отгадал преподавателя, то укажи нового вот так:\n" +
//                        "Обнови 3 Иванов Иван Иванович");
    }

    private static void newLoginFailedMessages (Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Что-то пошло не так. \n" +
                "Либо твои логин и пароль неправильные, либо я неправильно их прочитал");
    }

    private static void repeatLoginFailedMessages (Integer userId, GroupData groupData) {
        String messageToLoggedUser =
                "Похоже ты забыл сказать мне новый пароль после обновления в ЛК\n" +
                        "Скажи мне новые данные для входа так:\n" +
                        AUTH_COMMAND;

        final var loggedUserId = groupData.getLoggedUserId();
        if (!userId.equals(loggedUserId)) {
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
        final var builder = new StringBuilder();
        var partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            if (!data.getDocumentNames().isEmpty()) {
                partBuilder.append(data.getSubjectName())
                        .append(": ")
                        .append(String.join(" ", data.getDocumentNames()));
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("Новые документы:\n").append(partBuilder);
        }
        partBuilder = new StringBuilder();
        for (SubjectData subjectData : subjectsData) {
            final List<MessageData> messagesData = subjectData.getMessagesData();
            if (!messagesData.isEmpty()) {
                var messageBuilder = new StringBuilder();
                for (MessageData messageData : messagesData) {
                    final String shortName = makeShortSenderName(messageData.getSender());
                    messageBuilder.append(shortName)
                            .append(" в ")
                            .append(formatDate(messageData.getDate()))
                            .append(":\n")
                            .append(messageData.getComment())
                            .append("\n");
                }
                partBuilder.append(subjectData.getSubjectName())
                        .append(":\n")
                        .append(messageBuilder);
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("Новые сообщения:\n").append(partBuilder);
        }
        return builder.toString();
    }

    private static String formatDate (Date lastGlobalCheckDate) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(lastGlobalCheckDate);
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
        final String newSemesterName;
        if (now.after(springSemesterStart) && now.before(autumnSemesterStart))
            newSemesterName = year + "-В";
        else
            newSemesterName = year + "-О";

        return newSemesterName;
    }

    private static GroupData readGroupDataFile (String groupName) {
        try {
            return objectMapper.readValue(new File(makeFileName(groupName)), new TypeReference<>() {});
        } catch (Exception e) { //JsonProcessingException
            return null;
        }
    }

    private static void updateGroupDataFile (GroupData groupData, List<SubjectData> newSubjectsData, Date checkDate) {
        if (!newSubjectsData.isEmpty()) {
            groupData.setLastCheckDate(checkDate);
            groupData.setSubjectsData(newSubjectsData);
            writeGroupDataFile(groupData);
        }
    }

    private static void writeGroupDataFile (GroupData groupData) {
        try {
            objectMapper.writeValue(
                    new File(makeFileName(groupData.getGroupName())),
                    groupData.getSubjectsData().stream()
                            .sorted(Comparator.comparing(SubjectData::getSubjectName))
                            .collect(Collectors.toList()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String makeFileName (String groupName) {
        return SUBJECTS_DATA_FILENAME_PREFIX + groupName + "_" + scannedSemester + ".json";
    }

    // TODO
    private static void updateAcademicName (String group, Integer academicNumber, String academicName) {

    }
    
    // TODO Люди в группе могут постоянно обновлять предметы из лк и поэтому не все могут увдеть изменения из прошлых
    //    проверок. Поэтому в идеале: добавить для каждого пользователя свою историю просмотра
}
