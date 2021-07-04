package com.my;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.exceptions.UserTriesLimitExhaustedException;
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
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    static final String SUBJECTS_DATA_FILENAME_PREFIX = "info_";
    static final long HALF_DAY = 12L * 3600 * 1000;

    static final ObjectMapper objectMapper = new ObjectMapper();

    static final VkBotService vkBotService = VkBotService.getInstance();
    static final int ADMIN_VK_ID = 173315241;
    static final Map<Integer, UserContext> userContexts = new HashMap<>();
    static final Map<String, LoggedUser> groupLoggedUser = new HashMap<>();

    static final LstuAuthService lstuAuthService = new LstuAuthService();
    static final NewInfoService newInfoService = new NewInfoService();

    static final Keyboard keyboard1 = new Keyboard().setOneTime(true)
            .setButtons(Arrays.asList(
                    Collections.singletonList(
                            VkBotService.generateButton("Я готов на все ради своей группы!", KeyboardButtonColor.POSITIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Лучше доверюсь своему старосте", KeyboardButtonColor.NEGATIVE))
            ));
    static final Keyboard keyboard2 = new Keyboard().setOneTime(true)
            .setButtons(Arrays.asList(
                    Collections.singletonList(
                            VkBotService.generateButton("Сканируй все равно", KeyboardButtonColor.POSITIVE)),
                    Collections.singletonList(
                            VkBotService.generateButton("Ладно, отдыхай", KeyboardButtonColor.NEGATIVE))
            ));

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

    private static String scannedSemester;
    private static Date lastGlobalCheckDate;

    @Data
    @RequiredArgsConstructor
    private static class LoggedUser {
        @NonNull
        private Integer userId;
        @NonNull
        private String login;
        @NonNull
        private String password;
        private Boolean passwordNotActual = false;
    }

    @Data
    @RequiredArgsConstructor
    private static class UserContext {
        @NonNull
        Integer userId;
        @NonNull
        String groupName;
        int lastSentMessageIndex = 0;
        int triesCount = 0;

        public void incrementTriesCount () {
            triesCount++;
            if (triesCount > 4) {
                throw new UserTriesLimitExhaustedException("User with id:" + userId + "has a problem");
            }
        }
    }

    @Data
    @NoArgsConstructor
    @RequiredArgsConstructor
    private static class Info {
        @NonNull
        private Date lastCheckDate;
        @NonNull
        private List<SubjectData> subjectsData;
    }

    public static void main (String[] args) throws InterruptedException {
        lastGlobalCheckDate = new Date();
        scannedSemester = checkNewScannedSemester();

        vkBotService.updateTs();
        while (true) {
            final List<Message> messages = vkBotService.getNewMessages();
            if (!messages.isEmpty()) {
                for (Message message : messages) {

                    System.out.println(message.toString());
                    final Integer userId = message.getFromId();

                    try {
                        executeBotDialog(userId, message.getText());

                    } catch (UserTriesLimitExhaustedException e) {
                        vkBotService.sendMessageTo(ADMIN_VK_ID, "Проблема у пользователя: vk.com/id" + userId);
                        userContexts.get(userId).setTriesCount(0);
                        vkBotService.sendMessageTo(userId, "Я написал своему создателю о твоей проблеме. Ожидай его сообщения ;-)");

                    } catch (Exception e) {
                        userContexts.get(userId).incrementTriesCount();
                    }

                    if (new Date().getTime() > lastGlobalCheckDate.getTime() + HALF_DAY) {
                        lastGlobalCheckDate = new Date();
                        plannedSubjectsDataUpdate();
                    }
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
                userContexts.put(userId, new UserContext(userId, groupName));
                if (!groupLoggedUser.containsKey(groupName)) {
                    newUserAndGroupMessage(userId);
                } else {
                    newUserOldGroupMessages(userId, groupName);
                }

            } else {
                final UserContext userContext = userContexts.get(userId);
                messageText = messageText.toLowerCase();

                if (messageText.startsWith("предмет ")) {
                    getActualSubjectDataMessage(userId, userContext, messageText);

                } else if (messageText.equals("предметы")) {
                    getSubjectsDataMessage(userId, userContext);

                } else if (messageText.equals("список предметов")) {
                    Info info = readSubjectsDataFile(userContext.getGroupName());
                    vkBotService.sendMessageTo(userId, makeSubjectsList(info.getSubjectsData()));

                } else if (messageText.equals("обнови предметы")) {
                    updateSubjectsDataWarningMessage(userId);

                } else if (messageText.equals("сканируй все равно")) {
                    updateSubjectsDataMessages(userId, userContext);

                } else if (messageText.startsWith("команды")) {
                    vkBotService.sendMessageTo(userId, BASIC_COMMANDS);

                } else if (messageText.equals("ладно, отдыхай")) {
                    vkBotService.sendMessageTo(userId, "Спасибо тебе, человек!");

                } else if (messageText.equals("я готов на все ради своей группы!")) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так:\n" +
                                    "Хочу войти в ЛК\n" +
                                    "Мой_логин\n" +
                                    "Мой_пароль");

                } else if (messageText.startsWith("хочу войти в лк")) {
                    tryToLoginMessages(userId, userContext, messageText);

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
        final var newSemesterName = checkNewScannedSemester();
        groupLoggedUser.keySet().forEach(checkingGroup -> {
            var loggedUser = groupLoggedUser.get(checkingGroup);
            if (lstuAuthService.login(loggedUser.getLogin(), loggedUser.getPassword())) {
                final List<SubjectData> newSubjectData;

                if (scannedSemester.equals(newSemesterName)) {
                    newSubjectData = checkNewSubjectsData(checkingGroup);
                } else {
                    scannedSemester = newSemesterName;
                    newSubjectData = getSubjectsDataFirstTime(checkingGroup);
                }

                lstuAuthService.logout();
                String report = makeSubjectsDataReport(newSubjectData);
                userContexts.values().stream()
                        .filter(userContext -> userContext.getGroupName().equals(checkingGroup))
                        .map(UserContext::getUserId)
                        .forEach(userId1 -> vkBotService.sendMessageTo(userId1, report));
            } else {
                vkBotService.sendMessageTo(loggedUser.getUserId(),
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
        newUserSubjectsListMessage(userId, readSubjectsDataFile(groupName));
    }

    private static void newUserSubjectsListMessage (Integer userId, Info info) {

        vkBotService.sendMessageTo(userId,
                "Теперь я могу вывести тебе последнюю информацию из ЛК по данным предметам\n" +
                "(обновление было в " + formatDate(info.getLastCheckDate()) + "):\n" +
                makeSubjectsList(info.getSubjectsData()) + "\n");
        vkBotService.sendMessageTo(userId,"Также теперь ты можешь использовать эти команды:\n" + BASIC_COMMANDS);
    }

    private static String makeSubjectsList (List<SubjectData> subjectsData) {
        var stringBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            stringBuilder.append(data.getId()).append(" ")
                    .append(data.getSubjectName()).append("\n");
        }
        return stringBuilder.toString();
    }

    private static void getActualSubjectDataMessage (Integer userId, UserContext userContext, String messageText) {
        var subjectIndex = Integer.parseInt(messageText.substring(8));

        Info info = readSubjectsDataFile(userContext.getGroupName());

        final SubjectData oldSubjectData = info.getSubjectsData().stream()
                .filter(subjectData1 -> subjectData1.getId() == subjectIndex)
                .findFirst().orElse(null);

        final LoggedUser loggedUser = groupLoggedUser.get(userContext.getGroupName());

        if (lstuAuthService.login(loggedUser.getLogin(), loggedUser.getPassword())) {
            SubjectData newSubjectData = newInfoService.getNewSubjectData(
                    oldSubjectData.getSubjectName(), oldSubjectData.getLocalUrl(), info.getLastCheckDate());
            lstuAuthService.logout();

            NewInfoService.removeOldSubjectDocuments(oldSubjectData, newSubjectData);

            if (newSubjectData.isNotEmpty()) {
                vkBotService.sendMessageTo(userId,
                        makeSubjectsDataReport(Collections.singletonList(newSubjectData)));
            } else {
                vkBotService.sendMessageTo(userId,
                        "Нет новой информации по предмету " + newSubjectData.getSubjectName());
            }
        } else {
            loginFailedMessages(userId, loggedUser);
        }
    }

    private static void getSubjectsDataMessage (Integer userId, UserContext userContext) {
        Info info = readSubjectsDataFile(userContext.getGroupName());
        vkBotService.sendMessageTo(userId, makeSubjectsDataReport(info.getSubjectsData()));
    }

    private static void updateSubjectsDataWarningMessage (Integer userId) {
        vkBotService.sendMessageTo(userId, keyboard2,
                "Это самая долгая операция. Я итак выполняю ее регулярно\n" +
                        "Я такой же ленивый как и ты, человек. Может мне не стоит сканировать весь ЛК?");
    }

    private static void updateSubjectsDataMessages (Integer userId, UserContext userContext)
            throws ClientException, ApiException {
        vkBotService.sendMessageTo(userId,
                "Ладно, уговорил. Можешь пока отдохнуть\n" +
                        "Я тебе напишу, как проверю");

        var loggedUser = groupLoggedUser.get(userContext.getGroupName());

        if (lstuAuthService.login(loggedUser.getLogin(), loggedUser.getPassword())) {
            final List<SubjectData> newSubjectData = checkNewSubjectsData(userContext.getGroupName());
            lstuAuthService.logout();
            vkBotService.sendMessageTo(userId, makeSubjectsDataReport(newSubjectData));
        } else {
            loginFailedMessages(userId, loggedUser);
        }
    }

    private static void loginFailedMessages (Integer userId, LoggedUser loggedUser) {
        vkBotService.sendMessageTo(userId,
                "Мне не удалось проверить данные твоей группы \n" +
                        "Человек, вошедший от имени группы изменил свой пароль. Я скажу ему об этом сам\n" +
                        "Когда он введет новый пароль, напиши мне еще раз");

        loggedUser.passwordNotActual = true;
        vkBotService.sendMessageTo(loggedUser.getUserId(),
                "Привет. Человек из твоей группы не смог зайти в лк. Скажи мне новые данные для входа так:\n" +
                        "Хочу войти в ЛК\n" +
                        "Мой_логин\n" +
                        "Мой_пароль");
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

    private static void tryToLoginMessages (Integer userId, UserContext userContext, String messageText) {
        final String[] chunks = messageText.split("\n");
        String login = chunks[1];
        String password = chunks[2];

        var loggedUser = new LoggedUser(userId, login, password);
        groupLoggedUser.put(
                userContext.getGroupName(),
                loggedUser
        );
        vkBotService.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
        if (lstuAuthService.login(login, password)) {
            userContext.setTriesCount(0);
            if (!loggedUser.getPasswordNotActual()) {
                newGroupLoggedMessages(userId, userContext);
            } else {
                loggedUser.passwordNotActual = false;
                vkBotService.sendMessageTo(userId, "Спасибо, что обновил данные :-)");
            }
            lstuAuthService.logout();
        } else {
            newGroupLoggedErrorMessages(userId, userContext);
        }
    }

    private static void newGroupLoggedMessages (Integer userId, UserContext userContext) {
        vkBotService.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
        vkBotService.sendMessageTo(userId,
                "Ой, извини, случайно вырвалось)\n" +
                        "Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК\n" +
                        "Тебе нужно просто позвать их пообщаться со мной\n" +
                        "Но позволь я сначала проверю твой ЛК"); //и попытаюсь определить преподавателей...");

        final List<SubjectData> subjectsData = getSubjectsDataFirstTime(userContext.getGroupName());
        newUserSubjectsListMessage(userId, new Info(lastGlobalCheckDate, subjectsData));
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

    private static void newGroupLoggedErrorMessages (Integer userId, UserContext userContext) {
        vkBotService.sendMessageTo(userId,
                "Что-то пошло не так. \n" +
                        "Либо твои логин и пароль неправильные, либо я неправильно их прочитал.\n" +
                        "Если после четырех попыток не получится зайти, то я сам напишу своему создателю о твоей проблеме.");

        userContext.incrementTriesCount();
    }

    private static String checkNewScannedSemester () {
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

    public static List<SubjectData> getSubjectsDataFirstTime (@NonNull String groupName) {
        final Date checkDate = new Date();

        List<SubjectData> newSubjectsData = newInfoService.getSubjectsDataFirstTime(scannedSemester);

        if (!newSubjectsData.isEmpty()) {
            writeSubjectsData(newSubjectsData, groupName, checkDate);
        }
        return newSubjectsData;
    }

    public static List<SubjectData> checkNewSubjectsData (@NonNull String groupName) {
        final Date checkDate = new Date();

        Info oldInfo = readSubjectsDataFile(groupName);
        List<SubjectData> newSubjectsData =
                newInfoService.getNewSubjectsData(oldInfo.getSubjectsData(), oldInfo.getLastCheckDate());

        if (!newSubjectsData.isEmpty()) {
            writeSubjectsData(newSubjectsData, groupName, checkDate);
        }
        return NewInfoService.removeOldSubjectsDocuments(
                new HashSet<>(oldInfo.getSubjectsData()), new HashSet<>(newSubjectsData));
    }

    private static Info readSubjectsDataFile (String groupName) {
        try {
            return objectMapper.readValue(new File(makeFileName(groupName)), new TypeReference<>() {});
        } catch (Exception e) { //JsonProcessingException
            return new Info();
        }
    }

    private static void writeSubjectsData (List<SubjectData> newSubjectsData, @NonNull String groupName, Date checkDate) {
        try {
            objectMapper.writeValue(
                    new File(makeFileName(groupName)),
                    new Info(checkDate, newSubjectsData.stream()
                            .sorted(Comparator.comparing(SubjectData::getSubjectName))
                            .collect(Collectors.toList())));
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
}
