package com.my.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.LstuClient;
import com.my.LstuUrlBuilder;
import com.my.exceptions.ConnectionAttemptsException;
import com.my.models.SemesterSubjects;
import com.my.models.Subject;
import org.apache.http.auth.AuthenticationException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

//TODO Добавить возможность сичтать рейтинг
public class RatingCountService {

    private static final LstuClient lstuClient = LstuClient.getInstance();
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    static final String SEMESTERS_DATA_FILENAME = "semesters.json";
    private static final int ATTEMPTS_COUNT = 8;

    static final ObjectMapper objectMapper = new ObjectMapper();

    public List<SemesterSubjects> getSemestersData () throws AuthenticationException {
        if (lstuClient.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        Document document = lstuClient.get(
                LstuUrlBuilder.buildSemestersUrl());

        final Elements htmlSemestersData = document.select("ul.ul-main > li");
        List<SemesterSubjects> semestersData = new ArrayList<>();

        int semesterNumber = htmlSemestersData.size();
        for (Element htmlSemesterData : htmlSemestersData) {
            SemesterSubjects semesterSubjects = getSemesterData(htmlSemesterData);
            semesterSubjects.setNumber(semesterNumber);
            semesterNumber--;
            semestersData.add(semesterSubjects);
        }
        return semestersData;
    }

    private SemesterSubjects getSemesterData (Element element) {
        final SemesterSubjects semesterSubjects = new SemesterSubjects();
        semesterSubjects.setName(element.text());
        List<Subject> subjects = getSubjects(element.select("a").attr("href"));
        semesterSubjects.setSubjects(subjects);
        return semesterSubjects;
    }

    private List<Subject> getSubjects (String localRef) {
        Document subjectsPage = null;
        Elements htmlSubjectsTableColumnNames = null;
        for (int attemptsLeft = 0; attemptsLeft < ATTEMPTS_COUNT; attemptsLeft++) {
            subjectsPage = lstuClient.get(
                    LstuUrlBuilder.buildByLocalUrl(localRef));
            htmlSubjectsTableColumnNames = subjectsPage.select("div.table-responsive").select("th");
            if (!htmlSubjectsTableColumnNames.isEmpty())
                break;
        }

        if (htmlSubjectsTableColumnNames.isEmpty()) {
            throw new ConnectionAttemptsException("Too many attempts to get data. Try later");
        } else if (htmlSubjectsTableColumnNames.size() <= 3) { // Subjects without data
            return Collections.emptyList();
        }

        Map<String, Integer> columnNames = new HashMap<>();
        int columnId = 0;
        for (Element htmlColumnName : htmlSubjectsTableColumnNames) {
            columnNames.put(htmlColumnName.text(), columnId);
            columnId++;
        }
        final Elements htmlSubjects = subjectsPage.select("tr.eduProc");

        final List<Subject> subjects = new ArrayList<>();
        for (Element element : htmlSubjects) {
            final Subject subject = new Subject();
            final Elements htmlTableRow = element.select("tr > td");
            for (Map.Entry<String, Integer> entry : columnNames.entrySet()) {
                String columnName = entry.getKey();
                columnId = entry.getValue();
                switch (columnName) {
                    case "Дисциплина":
                        subject.setName(htmlTableRow.get(columnId).text());
                        break;
                    case "Семестр":
                        subject.setSemesterWorkPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Зачет":
                        subject.setCreditPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Экзамен":
                        subject.setExamPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Курсовая работа":
                        subject.setCourseWorkPoints(parseInt(htmlTableRow, columnId));
                        break;
                    default:
                        break;
                }
            }
            subjects.add(subject);
        }
        return subjects;
    }

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

    static final Scanner in = new Scanner(System.in);

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

    public static void countRating() throws AuthenticationException, IOException {
        List<SemesterSubjects> semestersData = null;
        semestersData = readFile(SEMESTERS_DATA_FILENAME, new TypeReference<>() {});
        semestersData.forEach(RatingCountService::completeData);

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

    private static <T> T readFile (String filename, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(filename, typeReference);
        } catch (Exception e) { //JsonProcessingException
            return null;
        }
    }

    private int parseInt (Elements htmlTableRow, int columnId) {
        final int value;
        try {
            value = Integer.parseInt(htmlTableRow.get(columnId).text());
        } catch (NumberFormatException e) {
            return -1;
        }
        return value;
    }

}
