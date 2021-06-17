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
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    static final String SEMESTERS_DATA_FILENAME = "semesters.json";
    static final String SUBJECTS_DATA_FILENAME = "info_.json";

    static final ObjectMapper objectMapper = new ObjectMapper();
    static final Scanner in = new Scanner(System.in);

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
            oldInfo = readFile(SUBJECTS_DATA_FILENAME, new TypeReference<>() {});
        } catch (FileNotFoundException e) {
            oldInfo = null;
        }

        final NewInfoService newInfoService = new NewInfoService();
        Set<SubjectData> newSubjectsData = (oldInfo == null) ?
                newInfoService.getInfoFirstTime(semester) :
                newInfoService.getNewInfo(semester, oldInfo.getLastCheckDate());

        if (!newSubjectsData.isEmpty()) {
            writeFile(SUBJECTS_DATA_FILENAME, new Info(checkDate, newSubjectsData));
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

    public static void main (String[] args) throws ClientException, ApiException, InterruptedException {
        Map<Integer, String> userAndGroup = new HashMap<>();
        Map<Integer, Integer> userAndTries = new HashMap<>();
        Map<String, Credentials> groupAndCredentials = new HashMap<>();
        final LstuAuthService lstuAuthService = new LstuAuthService();

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        final GroupActor actor = new GroupActor(205287906, BotSecretInfoContainer.VK_TOKEN.getValue());

        final VkBotService vkBotService = new VkBotService(vk, actor);

        final Keyboard keyboard1 = new Keyboard().setOneTime(true)
                .setButtons(Arrays.asList(
                    Arrays.asList(vkBotService.generateButton("Я готов на все ради своей группы!", KeyboardButtonColor.POSITIVE)),
                    Arrays.asList(vkBotService.generateButton("Лучше доверюсь своему старосте", KeyboardButtonColor.NEGATIVE))
                ));

        final Keyboard keyboard2 = new Keyboard().setOneTime(true)
                .setButtons(Arrays.asList(
                        Arrays.asList(vkBotService.generateButton("Покажи последнюю информацию из ЛК", KeyboardButtonColor.POSITIVE)),
                        Arrays.asList(vkBotService.generateButton("Пришли самые новые данные из ЛК", KeyboardButtonColor.NEGATIVE))
                ));

        Integer ts = vk.messages().getLongPollServer(actor).execute().getTs();
        while (true) {
            MessagesGetLongPollHistoryQuery historyQuery =  vk.messages().getLongPollHistory(actor).ts(ts);
            List<Message> messages = historyQuery.execute().getMessages().getItems();
            if (!messages.isEmpty()){
                messages.forEach(message -> {
                    System.out.println(message.toString());
                    String messageText = message.getText();
                    Integer userId = message.getFromId();
                    try {
                        if (messageText.startsWith("Я из ")) {
                            String groupName = messageText.substring(5);
                            userAndGroup.put(userId, groupName);
                            if (!groupAndCredentials.containsKey(groupName)) {
                                vkBotService.sendMessageTo(userId, keyboard1,
                                        "Я еще не могу смотреть данные из твоей группы. \n " +
                                                "Мне нужны твои логин и пароль от личного кабинета, чтобы проверять новую информацию для всей этой группы,\n" +
                                                "А еще несколько минут, чтобы ты мне объяснил, чьи сообщения самые важные\n" +
                                                "Можешь мне довериться ;-)\n" +
                                                "Если тебе стремно и ты не староста, то пни своего старосту добавить свои логин и пароль)0))0\n" +
                                                "Ты всегда можешь изучить мой внутренний мир по этой ссылке: https://github.com/Terqaz/LKBot \n" +
                                                "И обратиться к этому человеку за помощью (ну, почти всегда): https://vk.com/terqaz");
                            } else {
                                Info info = readFile(SUBJECTS_DATA_FILENAME + "groupName", new TypeReference<>() {});

                                StringBuilder stringBuilder = new StringBuilder("О, я знаю эту группу!\n" +
                                        "Могу вывести тебе последнюю информацию из ЛК по данным предметам\n" +
                                        "(глобальное обновление было в" +
                                        new SimpleDateFormat("dd.MM.yyyy HH:mm").format(info.getLastCheckDate()) + "):\n");
                                int i = 1;
                                for (SubjectData subjectData : info.getSubjectsData()) {
                                    stringBuilder.append(i).append(" ").append(subjectData.getSubjectName()).append("\n");
                                    i++;
                                }
                                stringBuilder.append("Чтобы узнать самую свежую информацию по предмету, введи его номер\n");
                                vkBotService.sendMessageTo(userId, keyboard2, stringBuilder.toString());
                            }

                        } else if (messageText.equals("Покажи последнюю информацию из ЛК")) {

                        } else if (messageText.equals("Пришли самые новые данные из ЛК")) {
                            vkBotService.sendMessageTo(userId, keyboard1,
                                    "Это самая долгая операция. \n" +
                                            "Я такой же ленивый как и ты, человек. Может мне не стоит сканировать весь ЛК");

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
                            groupAndCredentials.put(
                                    userAndGroup.get(userId),
                                    new UsernamePasswordCredentials(login, password));

                            vkBotService.sendMessageTo(userId,"Пробую зайти в твой ЛК...");
                            if (lstuAuthService.login(login, password)) {
                                userAndTries.put(userId, 0);
                                vkBotService.sendMessageTo(userId,"Ура. Теперь я похищу все твои данные)");
                                vkBotService.sendMessageTo(userId,
                                        "Ой, извини, случайно вырвалось\n " +
                                                "Теперь я могу присылать тебе и твоим одногруппникам информацию об обновлениях из ЛК.\n" +
                                                "Тебе нужно просто позвать их пообщаться со мной.\n" +
                                                "Но позволь я сначала проверю твой ЛК и попытаюсь определить преподавателей. \n " +
                                                "Потом ты мне поможешь исправить ФИО, если я не отгадал");
                                StringBuilder stringBuilder = new StringBuilder();
                                int i = 1;
                                for (SubjectData subjectData : checkNewInfo("2021-В")) {
                                    stringBuilder.append(i).append(" ").append(subjectData.getSubjectName()).append("\n")
                                        .append("Преподаватель: ").append(NewInfoService.findPrimaryAcademic(subjectData.getMessagesData())).append("\n");
                                    i++;
                                }
                                vkBotService.sendMessageTo(userId, stringBuilder.toString());

                            } else {
                                vkBotService.sendMessageTo(userId,
                                        "Что-то пошло не так. \n" +
                                                "Либо твои логин и пароль неправильные, либо я неправильно их прочитал.\n" +
                                                "Если после четырех попыток не получится зайти, то я сам напишу своему создателю.");
                                if (userAndTries.putIfAbsent(userId, 1) != null) {
                                    userAndTries.compute(userId, (k, v) -> v+1);
                                }
                                if (userAndTries.get(userId) == 4) {
                                    vkBotService.sendMessageTo(173315241, "Проблема со входом у пользователя: vk.com/id" + userId);
                                    userAndTries.put(userId, 0);
                                    vkBotService.sendMessageTo(userId, "Я написал своему создателю о твоей проблеме. Ожидай его сообщения ;-)");
                                }
                            }
                        } else if (!userAndGroup.containsKey(message.getFromId())) {
                            vkBotService.sendMessageTo(userId, "Напиши из какой ты группы (например: \"Я из ПИ-19\"):");
                        }
                    }
                    catch (ApiException | ClientException | AuthenticationException | IOException e) {e.printStackTrace();}
                });
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
    // автопроверка ЛК через разные интервалы: два раза в день - самое редкое, по желанию: каждые 30 минут
}
