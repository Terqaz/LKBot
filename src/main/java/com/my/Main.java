package com.my;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.exceptions.UserTriesLimitExhaustedException;
import com.my.services.LstuAuthService;
import com.my.services.NewInfoService;
import com.my.services.VkBotService;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.queries.messages.MessagesGetLongPollHistoryQuery;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.http.auth.AuthenticationException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    static final String SUBJECTS_DATA_FILENAME_PREFIX = "info_";
    static final long HALF_DAY = 12L * 3600 * 1000;

    static final ObjectMapper objectMapper = new ObjectMapper();

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


    @Data
    @RequiredArgsConstructor
    private static class LoggedUser {
        @NonNull
        Integer userId;
        @NonNull
        String login;
        @NonNull
        String password;
        Boolean passwordNotActual = false;
    }

    @Data
    @NoArgsConstructor
    @RequiredArgsConstructor
    private static class Info {
        @NonNull
        Date lastCheckDate;
        @NonNull
        List<SubjectData> subjectsData;
    }

    public static void main (String[] args) throws ClientException, ApiException, InterruptedException {
        var lastGlobalCheckDate = new Date();

        var transportClient = new HttpTransportClient();
        var vk = new VkApiClient(transportClient);
        final var actor = new GroupActor(205287906, BotSecretInfoContainer.VK_TOKEN.getValue());

        final var vkBotService = new VkBotService(vk, actor);

        Integer ts = vk.messages().getLongPollServer(actor).execute().getTs();
        while (true) {
            MessagesGetLongPollHistoryQuery historyQuery = vk.messages().getLongPollHistory(actor).ts(ts);
            List<Message> messages = historyQuery.execute().getMessages().getItems();
            if (!messages.isEmpty()) {
                for (Message message : messages) {
                    System.out.println(message.toString());
                    final Integer userId = message.getFromId();
                    try {
                        lastGlobalCheckDate = executeBotDialog(vkBotService, userId, message.getText(), lastGlobalCheckDate);
                    } catch (UserTriesLimitExhaustedException e) {
                        vkBotService.sendMessageTo(ADMIN_VK_ID, "Проблема у пользователя: vk.com/id" + userId);
                        userContexts.get(userId).setTriesCount(0);
                        vkBotService.sendMessageTo(userId, "Я написал своему создателю о твоей проблеме. Ожидай его сообщения ;-)");
                    } catch (Exception e) {
                        userContexts.get(userId).incrementTriesCount();
                    }
                }
            }
            ts = vk.messages().getLongPollServer(actor).execute().getTs();
            Thread.sleep(500);
        }
    }

    private static Date executeBotDialog (VkBotService vkBotService, Integer userId, String messageText, Date lastGlobalCheckDate) {
        try {
            if (messageText.startsWith("Я из ")) {
                String groupName = messageText.substring(5);
                userContexts.put(userId, new UserContext(userId, groupName));
                if (!groupLoggedUser.containsKey(groupName)) {
                    newUserAndGroupMessage(vkBotService, userId);
                } else {
                    newUserOldGroupMessages(vkBotService, userId, groupName, lastGlobalCheckDate);
                }

            } else if (messageText.equals("Обнови предметы")) {
                updateSubjectsInfoWarningMessage(vkBotService, userId);

            } else {
                final UserContext userContext = userContexts.get(userId);

                if (messageText.startsWith("Предмет ")) {
                    getSubjectInfoMessage(vkBotService, userId, userContext, messageText);

                } else if (messageText.equals("Предметы")) {
                    getSubjectsInfoMessage(vkBotService, userId, userContext);

                } else if (messageText.equals("Сканируй все равно")) {
                    updateSubjectInfoMessages(vkBotService, userId, userContext);

                } else if (messageText.equals("Ладно, отдыхай")) {
                    vkBotService.sendMessageTo(userId, "Спасибо тебе, человек!");

                } else if (messageText.equals("Я готов на все ради своей группы!")) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так:\n" +
                                    "Хочу войти в ЛК\n" +
                                    "Мой_логин\n" +
                                    "Мой_пароль");

                } else if (messageText.startsWith("Хочу войти в ЛК")) {
                    tryToLoginMessages(vkBotService, userId, userContext, messageText);

//                 } else if (messageText.startsWith("Обнови")) {
//                    final String[] chunks = messageText.split(" ");
//                    Integer academicNumber = Integer.parseInt(chunks[1]);
//                    String academicName = chunks[2] + chunks[3] + chunks[4];
//                    updateAcademicName(userContext.getGroupName(), academicNumber, academicName);

                } else if (!userContexts.containsKey(userId)) {
                    vkBotService.sendMessageTo(userId, "Напиши из какой ты группы (например: \"Я из ПИ-19\"):");

                } else {
                    vkBotService.sendMessageTo(userId, "Ты говоришь будто на другом языке! Прочитай внимательнее, что я хочу узнать");
                }
            }

            if (new Date().getTime() > lastGlobalCheckDate.getTime() + HALF_DAY) {
                lastGlobalCheckDate = new Date();
                plannedSubjectsInfoUpdate(vkBotService);
            }
        } catch (ApiException | ClientException | AuthenticationException | IOException e) {
            e.printStackTrace();
        }
        return lastGlobalCheckDate;
    }

    private static void plannedSubjectsInfoUpdate (VkBotService vkBotService) throws AuthenticationException, IOException {
        for (String checkingGroup : groupLoggedUser.keySet()) {
            LoggedUser loggedUser = groupLoggedUser.get(checkingGroup);

            try {
                lstuAuthService.login(loggedUser.getLogin(), loggedUser.getPassword());
            } catch (AuthenticationException e) {
                plannedLoginFailedMessage(vkBotService, loggedUser.getUserId());
            }

            final List<SubjectData> newSubjectData = checkNewInfo("2021-В", userContexts.get(checkingGroup).getGroupName());
            lstuAuthService.logout();
            String report = makeSubjectsInfoReport(newSubjectData);
            userContexts.values().stream()
                    .filter(userContext -> userContext.groupName.equals(checkingGroup))
                    .map(UserContext::getUserId)
                    .forEach(userId1 -> vkBotService.sendMessageTo(userId1, report));
        }
    }

    private static void plannedLoginFailedMessage (VkBotService vkBotService, Integer userId) {
        vkBotService.sendMessageTo(userId,
                "Не удалось обновить ЛК по следующей причине:\n" +
                        "Необходимо обновить данные для входа");
    }

    private static void newUserAndGroupMessage (VkBotService vkBotService, Integer userId) throws ClientException, ApiException {
        vkBotService.sendMessageTo(userId, keyboard1,
                "Я еще не могу смотреть данные из твоей группы\n" +
                        "Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию для всей этой группы\n" +
                        "А еще несколько минут, чтобы ты мне объяснил, чьи сообщения самые важные\n" +
                        "Можешь мне довериться ;-)\n" +
                        "Если тебе стремно и ты не староста, то пни своего старосту добавить свои логин и пароль)0))0\n" +
                        "Ты всегда можешь изучить мой внутренний мир по этой ссылке: https://github.com/Terqaz/LKBot\n" +
                        "И обратиться к этому человеку за помощью (ну, почти всегда): https://vk.com/terqaz");
    }

    private static void newUserOldGroupMessages (VkBotService vkBotService, Integer userId, String groupName, Date lastGlobalCheckDate)
            throws ClientException, ApiException {
        Info info = readSubjectsDataFile(groupName);

        StringBuilder stringBuilder = new StringBuilder("О, я знаю эту группу!\n" +
                "Могу вывести тебе последнюю информацию из ЛК по данным предметам\n" +
                "(глобальное обновление было в" +
                formatDate(lastGlobalCheckDate) + "):\n");
        int i = 1;
        for (SubjectData subjectData : info.getSubjectsData()) {
            stringBuilder.append(i).append(" ").append(subjectData.getSubjectName()).append("\n");
            i++;
        }
        vkBotService.sendMessageTo(userId, stringBuilder.toString());
        vkBotService.sendMessageTo(userId,
                "Чтобы узнать самую свежую информацию по предмету скажи:\n" +
                        "Предмет n (n - номер в списке выше)\n" +
                        "Чтобы узнать последнюю проверенную информацию по всем предметам скажи:\n" +
                        "Предметы\n" +
                        "Чтобы узнать самую новую информацию по всем предметам скажи:\n" +
                        "Обнови предметы\n" +
                        "Чтобы показать эту справку скажи:\n" +
                        "Справка\n");
    }

    private static void getSubjectInfoMessage (VkBotService vkBotService, Integer userId, UserContext userContext, String messageText) {
        final int subjectIndex = Integer.parseInt(messageText.substring(8));
        Info info = readSubjectsDataFile(userContext.getGroupName());
        vkBotService.sendMessageTo(userId, makeSubjectsInfoReport(
                info.getSubjectsData().subList(subjectIndex, subjectIndex+1)));

    }

    private static void getSubjectsInfoMessage (VkBotService vkBotService, Integer userId, UserContext userContext) {
        Info info = readSubjectsDataFile(userContext.getGroupName());
        vkBotService.sendMessageTo(userId, makeSubjectsInfoReport(info.getSubjectsData()));

    }

    private static void updateSubjectsInfoWarningMessage (VkBotService vkBotService, Integer userId)
            throws ClientException, ApiException {
        vkBotService.sendMessageTo(userId, keyboard2,
                "Это самая долгая операция. Я итак выполняю ее регулярно\n" +
                        "Я такой же ленивый как и ты, человек. Может мне не стоит сканировать весь ЛК?");
    }

    private static void updateSubjectInfoMessages (VkBotService vkBotService, Integer userId, UserContext userContext)
            throws ClientException, ApiException, AuthenticationException, IOException {
        vkBotService.sendMessageTo(userId,
                "Ладно, уговорил. Можешь пока отдохнуть\n" +
                        "Я тебе напишу, как проверю");
        final LoggedUser loggedUser1 = groupLoggedUser.get(userContext.getGroupName());
        if (lstuAuthService.login(loggedUser1.getLogin(), loggedUser1.getPassword())) {
            final List<SubjectData> newSubjectData = checkNewInfo("2021-В", userContext.getGroupName());
            lstuAuthService.logout();
            vkBotService.sendMessageTo(userId, makeSubjectsInfoReport(newSubjectData));
        } else {
            vkBotService.sendMessageTo(userId,
                    "Мне не удалось проверить данные твоей группы \n" +
                            "Человек, вошедший от имени группы изменил свой пароль. Я скажу ему об этом сам\n" +
                            "Когда он введет новый пароль, напиши мне еще раз");
            final LoggedUser loggedUser = groupLoggedUser.get(userContext.getGroupName());
            loggedUser.passwordNotActual = true;
            vkBotService.sendMessageTo(loggedUser.getUserId(),
                    "Привет. Человек из твоей группы не смог зайти в лк. Скажи мне новые данные для входа так:\n" +
                            "Хочу войти в ЛК\n" +
                            "Мой_логин\n" +
                            "Мой_пароль");
        }
    }

    private static void tryToLoginMessages (VkBotService vkBotService, Integer userId, UserContext userContext, String messageText)
            throws AuthenticationException {
        final String[] chunks = messageText.split("\n");
        String login = chunks[1];
        String password = chunks[2];
        groupLoggedUser.put(
                userContext.getGroupName(),
                new LoggedUser(userId, login, password)
        );

        vkBotService.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
        if (lstuAuthService.login(login, password)) {
            userContext.setTriesCount(0);
            final LoggedUser loggedUser = groupLoggedUser.get(userId);
            if (!loggedUser.getPasswordNotActual()) {
                newGroupLoggedMessages(vkBotService, userId, userContext);
            } else {
                loggedUser.passwordNotActual = false;
                vkBotService.sendMessageTo(userId, "Спасибо, что обновил данные :-)");
            }
            lstuAuthService.logout();
        } else {
            newGroupLoggedErrorMessages(vkBotService, userId, userContext);
        }
    }

    private static void newGroupLoggedMessages (VkBotService vkBotService, Integer userId, UserContext userContext)
            throws AuthenticationException {
        vkBotService.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
        vkBotService.sendMessageTo(userId,
                "Ой, извини, случайно вырвалось)\n" +
                        "Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК\n" +
                        "Тебе нужно просто позвать их пообщаться со мной\n" +
                        "Но позволь я сначала проверю твой ЛК"); //и попытаюсь определить преподавателей...");

        StringBuilder stringBuilder = new StringBuilder("Преподаватели:\n");
        int i = 1;
        for (SubjectData subjectData : getInfoFirstTime("2021-В", userContext.getGroupName())) {
            stringBuilder.append(i).append(" ").append(subjectData.getSubjectName()).append("\n")
                    .append("- ").append(NewInfoService.findPrimaryAcademic(subjectData.getMessagesData())).append("\n");
            i++;
        }
        vkBotService.sendMessageTo(userId, stringBuilder.toString());
        vkBotService.sendMessageTo(userId,
                "Если я неправильно отгадал преподавателя, то укажи нового вот так:\n" +
                        "Обнови 3 Иванов Иван Иванович");
    }

    private static void newGroupLoggedErrorMessages (VkBotService vkBotService, Integer userId, UserContext userContext) {
        vkBotService.sendMessageTo(userId,
                "Что-то пошло не так. \n" +
                        "Либо твои логин и пароль неправильные, либо я неправильно их прочитал.\n" +
                        "Если после четырех попыток не получится зайти, то я сам напишу своему создателю о твоей проблеме.");

        userContext.incrementTriesCount();
    }

    private static Info readSubjectsDataFile (String groupName) {
        try {
            return objectMapper.readValue(SUBJECTS_DATA_FILENAME_PREFIX + groupName + ".json", new TypeReference<>() {});
        } catch (Exception e) { //JsonProcessingException
            return null;
        }
    }

    public static Set<SubjectData> getInfoFirstTime (String semester, @NonNull String groupName) throws AuthenticationException {
        final Date checkDate = new Date();

        Set<SubjectData> newSubjectsData = newInfoService.getInfoFirstTime(semester);

        if (!newSubjectsData.isEmpty()) {
            writeSubjectsInfo(newSubjectsData, groupName, checkDate);
        }
        return newSubjectsData;
    }

    private static void writeSubjectsInfo (Set<SubjectData> newSubjectsData, @NonNull String groupName, Date checkDate) {
        try {
            objectMapper.writeValue(
                    new File(SUBJECTS_DATA_FILENAME_PREFIX + groupName + ".json"),
                    new Info(checkDate, newSubjectsData.stream()
                            .sorted(Comparator.comparing(SubjectData::getSubjectName))
                            .collect(Collectors.toList())));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<SubjectData> checkNewInfo (String semester, @NonNull String groupName) throws AuthenticationException {
        final Date checkDate = new Date();

        Info oldInfo = readSubjectsDataFile(groupName);
        Set<SubjectData> newSubjectsData = newInfoService.getNewInfo(semester, oldInfo.getLastCheckDate());

        if (!newSubjectsData.isEmpty()) {
            writeSubjectsInfo(newSubjectsData, groupName, checkDate);
        }

        return NewInfoService.removeOldDocuments(new HashSet<>(oldInfo.getSubjectsData()), newSubjectsData);
    }

    // TODO
    private static void updateAcademicName (String group, Integer academicNumber, String academicName) {

    }

    private static String makeSubjectsInfoReport (List<SubjectData> subjectsData) {
        StringBuilder builder = new StringBuilder();
        StringBuilder partBuilder = new StringBuilder();
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
                StringBuilder messageBuilder = new StringBuilder();
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
        return chunks[0] + " " + chunks[1].charAt(0) + chunks[2].charAt(0);
    }

    // TODO дополнительно просмотр ФИО преподавателей через их личную страницу
    // проверка свежей информации сразу же по просьбе по одному предмету
    //> автопроверка ЛК через разные интервалы: два раза в день - самое редкое, по желанию: каждые 30 минут
    // Добавить защиту паролей и данных
    // Удаление сообщения с данными для входа
    //? Добавить код доступа к данным группы
    // Автоопределение семестра и оповещение об изменении сканируемого семестра
}
