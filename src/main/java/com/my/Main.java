package com.my;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.services.LstuAuthService;
import com.my.services.NewInfoService;
import com.my.services.RatingCountService;
import com.my.services.VkBotService;
import com.vk.api.sdk.client.TransportClient;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    static final String SEMESTERS_DATA_FILENAME = "semesters.json";
    static final String SUBJECTS_DATA_FILENAME_PREFIX = "info_";
    static final long HALF_DAY = 12L * 3600 * 1000;

    static final ObjectMapper objectMapper = new ObjectMapper();
    static final Scanner in = new Scanner(System.in);

    static final int ADMIN_VK_ID = 173315241;
    static final Map<Integer, UserContext> userContexts = new HashMap<>();
    static final Map<String, LoggedUser> groupLoggedUser = new HashMap<>();
    static final LstuAuthService lstuAuthService = new LstuAuthService();

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

    static final Map<Integer, List<Double>> coefficients = new HashMap<>();
    static {
        coefficients.put(0b10000, Collections.singletonList(1.0));
        coefficients.put(0b11000, Arrays.asList(0.5, 0.5));
        coefficients.put(0b10100, Arrays.asList(0.5, 0.5));
        coefficients.put(0b00010, Collections.singletonList(1.0));
        coefficients.put(0b11100, Arrays.asList(0.4, 0.2, 0.4));
        coefficients.put(0b10110, Arrays.asList(0.3, 0.4, 0.3));
        coefficients.put(0b11010, Arrays.asList(0.4, 0.2, 0.4));
        coefficients.put(0b11110, Arrays.asList(0.2, 0.2, 0.4, 0.2));
        coefficients.put(0b00001, Collections.singletonList(1.0));
        coefficients.put(0b01001, Arrays.asList(0.2, 0.8));
        coefficients.put(0b01010, Arrays.asList(0.5, 0.5));
    }

    public static int isPointsPresent (int points, List<Integer> presentPoints) {
        if (points != -1) {
            presentPoints.add(points);
            return 1;
        }
        return 0;
    }

    public static double countLinearCombination (List<Integer> intList, List<Double> doubleList) {
        double result = 0;
        for (int i = 0; i < intList.size(); i++) {
            result += intList.get(i) * doubleList.get(i);
        }
        return result;
    }

    public static double countRating (SemesterSubjects semesterSubjects) {
        List<Integer> presentPoints = new ArrayList<>();
        double rating = 0;
        for (Subject subject : semesterSubjects.getSubjects()) {
            int key = 0;
            if (subject.isPractice()) { // TODO
                key = 0x00001;
                isPointsPresent(subject.getCreditPoints(), presentPoints);
            } else {
                key += isPointsPresent(subject.getSemesterWorkPoints(), presentPoints);
                key <<= 1;
                key += isPointsPresent(subject.getCreditPoints(), presentPoints);
                key <<= 1;
                key += isPointsPresent(subject.getExamPoints(), presentPoints);
                key <<= 1;
                key += isPointsPresent(subject.getCourseWorkPoints(), presentPoints);
                key <<= 1;
            }
            rating += subject.getHours() * countLinearCombination(presentPoints, coefficients.get(key));
            presentPoints.clear();
        }
        rating /= semesterSubjects.getSubjects().stream().map(Subject::getHours)
                .reduce(0, Integer::sum);
        return rating;
    }

    public static void completeData (SemesterSubjects semesterSubjects) {
        semesterSubjects.setSubjects(
                semesterSubjects.getSubjects().stream().filter(
                        subject -> !(subject.getSemesterWorkPoints() == -1 && subject.getCreditPoints() == -1 &&
                                subject.getExamPoints() == -1 && subject.getCourseWorkPoints() == -1))
                        .collect(Collectors.toList()));
        if (semesterSubjects.getSubjects().isEmpty())
            return;

        System.out.println("Input hours for " + semesterSubjects.getName() + " semester subjects:");
        for (Subject subject : semesterSubjects.getSubjects()) {
            if (subject.getHours() == -1) {
                System.out.print(subject.getName() + ": ");
                subject.setHours(in.nextInt());
            }
        }
        for (Subject subject : semesterSubjects.getSubjects()) {
            if (subject.getSemesterWorkPoints() == 0) {
                System.out.print("Specify work points for " + subject.getName() +
                        "\nin semester " + semesterSubjects.getName() + ":");
                subject.setSemesterWorkPoints(in.nextInt());
            }
            if (subject.getCreditPoints() == 0) {
                System.out.print("Specify credit points for " + subject.getName() +
                        "\nin semester " + semesterSubjects.getName() + ":");
                subject.setCreditPoints(in.nextInt());
            }
            if (subject.getExamPoints() == 0) {
                System.out.print("Specify exam points for " + subject.getName() +
                        "\nin semester " + semesterSubjects.getName() + ":");
                subject.setExamPoints(in.nextInt());
            }
            if (subject.getCourseWorkPoints() == 0) {
                System.out.print("Specify course work points for " + subject.getName() +
                        "\nin semester " + semesterSubjects.getName() + ":");
                subject.setCourseWorkPoints(in.nextInt());
            }
        }
    }

    public static <T> void writeFile (String filename, T object) throws IOException {
        objectMapper.writeValue(new File(filename), object);
    }

    private static <T> T readFile (String filename, TypeReference<T> typeReference)
            throws IOException {
        return objectMapper.readValue(new File(filename), typeReference);
        // MismatchedInputException?
    }

    //TODO доделать
    public static void countRating() throws AuthenticationException, IOException {
        List<SemesterSubjects> semestersData = null;
        try {
            semestersData = readFile(SEMESTERS_DATA_FILENAME, new TypeReference<>() {});
        } catch (FileNotFoundException ignored) {}
        semestersData.forEach(Main::completeData);

        if (semestersData.isEmpty()) {
            RatingCountService ratingCountService = new RatingCountService();
            final LstuAuthService lstuAuthService = new LstuAuthService();
            lstuAuthService.login("s11916327", "f7LLDSJibCw8QNGeR6");
            semestersData = ratingCountService.getSemestersData();
            lstuAuthService.logout();
            semestersData.stream()
                    .filter(semesterSubjects -> !semesterSubjects.getSubjects().isEmpty())
                    .forEach(semesterSubjects -> {
                        completeData(semesterSubjects);
                        System.out.println("Rating for the " + semesterSubjects.getName() + " semester is " + countRating(semesterSubjects));
                    });
        }
    }

    @Data
    @NoArgsConstructor
    @RequiredArgsConstructor
    private static class Info {
        @NonNull
        Date lastCheckDate;
        @NonNull
        Set<SubjectData> subjectsData;
    }

    public static Set<SubjectData> checkNewInfo(String semester) throws AuthenticationException, IOException {
        final Date checkDate = new Date();

        Info oldInfo;
        try {
            oldInfo = readFile(SUBJECTS_DATA_FILENAME_PREFIX, new TypeReference<>() {});
        } catch (FileNotFoundException e) {
            oldInfo = null;
        }

        final NewInfoService newInfoService = new NewInfoService();
        Set<SubjectData> newSubjectsData = (oldInfo == null) ?
                newInfoService.getInfoFirstTime(semester) :
                newInfoService.getNewInfo(semester, oldInfo.getLastCheckDate());

        if (!newSubjectsData.isEmpty()) {
            writeFile(SUBJECTS_DATA_FILENAME_PREFIX, new Info(checkDate, newSubjectsData));
        }

        return (oldInfo != null) ?
                NewInfoService.removeOldDocuments(oldInfo.getSubjectsData(), newSubjectsData) :
                newSubjectsData;
    }

    public static void checkNewInfoUsage(String semester) throws IOException, AuthenticationException {
        System.out.println("--- Список новых документов по предметам ---");
        checkNewInfo(semester).forEach(subjectData -> {
            System.out.println(subjectData.getSubjectName()+":");
            subjectData.getDocumentNames().forEach(
                    documentName -> System.out.print("\""+documentName+"\" "));
            System.out.println();
        });
    }

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

    private static void updateAcademicName (String group, Integer academicNumber, String academicName) {

    }

    private static Date executeBotDialog (Date lastGlobalCheckDate, VkBotService vkBotService, Message message) {
        String messageText = message.getText();
        Integer userId = message.getFromId();
        try {
            if (messageText.startsWith("Я из ")) {
                String groupName = messageText.substring(5);
                userContexts.put(userId, new UserContext(userId, groupName));
                if (!groupLoggedUser.containsKey(groupName)) {
                    vkBotService.sendMessageTo(userId, keyboard1,
                            "Я еще не могу смотреть данные из твоей группы\n" +
                                    "Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию для всей этой группы\n" +
                                    "А еще несколько минут, чтобы ты мне объяснил, чьи сообщения самые важные\n" +
                                    "Можешь мне довериться ;-)\n" +
                                    "Если тебе стремно и ты не староста, то пни своего старосту добавить свои логин и пароль)0))0\n" +
                                    "Ты всегда можешь изучить мой внутренний мир по этой ссылке: https://github.com/Terqaz/LKBot\n" +
                                    "И обратиться к этому человеку за помощью (ну, почти всегда): https://vk.com/terqaz");
                } else {
                    Info info = readFile(SUBJECTS_DATA_FILENAME_PREFIX + groupName + ".json", new TypeReference<>() {});

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

            } else if (messageText.equals("Покажи последнюю информацию из ЛК")) {

            } else if (messageText.equals("Обнови предметы")) {
                vkBotService.sendMessageTo(userId, keyboard2,
                        "Это самая долгая операция. Я итак выполняю ее регулярно\n" +
                                "Я такой же ленивый как и ты, человек. Может мне не стоит сканировать весь ЛК?");

            } else {
                final UserContext userContext = userContexts.get(userId);
                if (messageText.equals("Сканируй все равно")) {
                    vkBotService.sendMessageTo(userId, keyboard2,
                            "Ладно, уговорил. Можешь пока отдохнуть\n" +
                                    "Я тебе напишу, как проверю");
                    final LoggedUser userCredentials = groupLoggedUser.get(userContext.getGroupName());
                    String login = userCredentials.getLogin();
                    String password = userCredentials.getPassword();
                    if (lstuAuthService.login(login, password)) {
                        checkNewInfo("2021-В");
                        lstuAuthService.logout();
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

                } else if (messageText.equals("Ладно, отдыхай")) {

                } else if (messageText.equals("Я готов на все ради своей группы!")) {
                    vkBotService.sendMessageTo(userId,
                            "Хорошо, смельчак. Пиши свои данные вот так:\n" +
                                    "Хочу войти в ЛК\n" +
                                    "Мой_логин\n" +
                                    "Мой_пароль");

                } else if (messageText.startsWith("Хочу войти в ЛК")) {
                    final String[] chunks = messageText.split("\n");
                    String login = chunks[1];
                    String password = chunks[2];
                    groupLoggedUser.put(
                            userContext.getGroupName(),
                            new LoggedUser(userId, login, password)
                    );

                    vkBotService.sendMessageTo(userId, "Пробую зайти в твой ЛК...");
                    if (lstuAuthService.login(login, password)) {
                        userContext.setLoginTries(0);
                        final LoggedUser loggedUser = groupLoggedUser.get(userId);
                        if (loggedUser.getPasswordNotActual()) {
                            loggedUser.passwordNotActual = false;
                            vkBotService.sendMessageTo(userId, "Спасибо, что обновил данные :-)");
                        } else {
                            vkBotService.sendMessageTo(userId, "Ура. Теперь я похищу все твои данные)");
                            vkBotService.sendMessageTo(userId,
                                    "Ой, извини, случайно вырвалось)\n" +
                                            "Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК\n" +
                                            "Тебе нужно просто позвать их пообщаться со мной\n" +
                                            "Но позволь я сначала проверю твой ЛК и попытаюсь определить преподавателей...");
                            StringBuilder stringBuilder = new StringBuilder("Преподаватели:\n");
                            int i = 1;
                            for (SubjectData subjectData : checkNewInfo("2021-В")) {
                                stringBuilder.append(i).append(" ").append(subjectData.getSubjectName()).append("\n")
                                        .append("- ").append(NewInfoService.findPrimaryAcademic(subjectData.getMessagesData())).append("\n");
                                i++;
                            }
                            vkBotService.sendMessageTo(userId, stringBuilder.toString());
                            vkBotService.sendMessageTo(userId,
                                    "Если я неправильно отгадал преподавателя, то укажи нового вот так:\n" +
                                            "Обнови 3 Иванов Иван Иванович");
                        }
                        lstuAuthService.logout();
                    } else {
                        vkBotService.sendMessageTo(userId,
                                "Что-то пошло не так. \n" +
                                        "Либо твои логин и пароль неправильные, либо я неправильно их прочитал.\n" +
                                        "Если после четырех попыток не получится зайти, то я сам напишу своему создателю о твоей проблеме.");
                        userContext.incrementLoginTries();
                        if (userContext.getLoginTries() == 4) {
                            vkBotService.sendMessageTo(ADMIN_VK_ID, "Проблема со входом у пользователя: vk.com/id" + userId);
                            userContext.setLoginTries(0);
                            vkBotService.sendMessageTo(userId, "Я написал своему создателю о твоей проблеме. Ожидай его сообщения ;-)");
                        }
                    }
                } else if (messageText.startsWith("Обнови")) {
                    final String[] chunks = messageText.split(" ");
                    Integer academicNumber = Integer.parseInt(chunks[1]);
                    String academicName = chunks[2] + chunks[3] + chunks[4];
                    updateAcademicName(userContext.getGroupName(), academicNumber, academicName);
                } else if (!userContexts.containsKey(message.getFromId())) {
                    vkBotService.sendMessageTo(userId, "Напиши из какой ты группы (например: \"Я из ПИ-19\"):");
                } else {
                    vkBotService.sendMessageTo(userId, "Ты говоришь будто на другом языке! Прочитай внимательнее, что я хочу узнать");
                }
            }

            if (new Date().getTime() > lastGlobalCheckDate.getTime() + HALF_DAY) {
                lastGlobalCheckDate = new Date();
                for (String checkingGroup : groupLoggedUser.keySet()) {
                    LoggedUser loggedUser = groupLoggedUser.get(checkingGroup);

                    lstuAuthService.login(loggedUser.getLogin(), loggedUser.getPassword());
                    final Set<SubjectData> subjectData = checkNewInfo("2021-В");
                    lstuAuthService.logout();
                    String report = makeReport(subjectData);
                    userContexts.values().stream()
                            .filter(userContext -> userContext.groupName.equals(checkingGroup))
                            .map(UserContext::getUserId)
                            .forEach(userId1 -> {
                                try {
                                    vkBotService.sendMessageTo(userId1, report);
                                } catch (ClientException | ApiException e) {
                                    e.printStackTrace();
                                }
                            });
                }
            }
        } catch (ApiException | ClientException | AuthenticationException | IOException e) {
            e.printStackTrace();
        }
        return lastGlobalCheckDate;
    }

    private static String makeReport (Set<SubjectData> subjectsData) {
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

    public static void main (String[] args) throws ClientException, ApiException, InterruptedException {
        Date lastGlobalCheckDate = new Date();

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        final GroupActor actor = new GroupActor(205287906, BotSecretInfoContainer.VK_TOKEN.getValue());

        final VkBotService vkBotService = new VkBotService(vk, actor);

        Integer ts = vk.messages().getLongPollServer(actor).execute().getTs();
        while (true) {
            MessagesGetLongPollHistoryQuery historyQuery =  vk.messages().getLongPollHistory(actor).ts(ts);
            List<Message> messages = historyQuery.execute().getMessages().getItems();
            if (!messages.isEmpty()) {
                for (Message message : messages) {
                    System.out.println(message.toString());
                    lastGlobalCheckDate = executeBotDialog(lastGlobalCheckDate, vkBotService, message);
                }
            }
            ts = vk.messages().getLongPollServer(actor).execute().getTs();
            Thread.sleep(500);
        }

//        lstuAuthService.login("s11916327", "f7LLDSJibCw8QNGeR6");
//        checkNewInfoUsage("2021-В");
//        lstuAuthService.logout();
    }

    // TODO дополнительно просмотр ФИО преподавателей через их личную страницу
    // проверка свежей информации сразу же по просьбе по одному предмету
    //> автопроверка ЛК через разные интервалы: два раза в день - самое редкое, по желанию: каждые 30 минут
    // Добавить защиту паролей и данных
    // Удаление сообщения с данными для входа
    //? Добавить код доступа к данным группы
    // Когда человек поменял пароль, бот напишет
    // Автоопределение семестра и оповещение об изменении сканируемого семестра
}
