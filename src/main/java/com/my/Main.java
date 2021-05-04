package com.my;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.auth.AuthenticationException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    static final String SEMESTERS_DATA_FILENAME = "semesters.json";
    static final String DOCUMENT_NAMES_DATA_FILENAME = "documentNames.json";

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

    public static double countRating (SemesterData semesterData) {
        List<Integer> presentPoints = new ArrayList<>();
        double rating = 0;
        for (Subject subject : semesterData.getSubjects()) {
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
        rating /= semesterData.getSubjects().stream().map(Subject::getHours)
                .reduce(0, Integer::sum);
        return rating;
    }

    public static void completeData (SemesterData semesterData) {
        semesterData.setSubjects(
                semesterData.getSubjects().stream().filter(
                        subject -> !(subject.getSemesterWorkPoints() == -1 && subject.getCreditPoints() == -1 &&
                                subject.getExamPoints() == -1 && subject.getCourseWorkPoints() == -1))
                        .collect(Collectors.toList()));
        if (semesterData.getSubjects().isEmpty())
            return;

        System.out.println("Input hours for " + semesterData.getName() + " semester subjects:");
        for (Subject subject : semesterData.getSubjects()) {
            if (subject.getHours() == -1) {
                System.out.print(subject.getName() + ": ");
                subject.setHours(in.nextInt());
            }
        }
        for (Subject subject : semesterData.getSubjects()) {
            if (subject.getSemesterWorkPoints() == 0) {
                System.out.print("Specify work points for " + subject.getName() +
                        "\nin semester " + semesterData.getName() + ":");
                subject.setSemesterWorkPoints(in.nextInt());
            }
            if (subject.getCreditPoints() == 0) {
                System.out.print("Specify credit points for " + subject.getName() +
                        "\nin semester " + semesterData.getName() + ":");
                subject.setCreditPoints(in.nextInt());
            }
            if (subject.getExamPoints() == 0) {
                System.out.print("Specify exam points for " + subject.getName() +
                        "\nin semester " + semesterData.getName() + ":");
                subject.setExamPoints(in.nextInt());
            }
            if (subject.getCourseWorkPoints() == 0) {
                System.out.print("Specify course work points for " + subject.getName() +
                        "\nin semester " + semesterData.getName() + ":");
                subject.setCourseWorkPoints(in.nextInt());
            }
        }
    }

    // TODO filename обобщить
    public static void saveDataToFile(List<SemesterData> semesterData) {
        try {
            objectMapper.writeValue(new File(SEMESTERS_DATA_FILENAME), semesterData);
        } catch (IOException e) {
            System.out.println("Failed to save semester data to file");
        }
    }

    public static List<SemesterData> loadDataFromFile() {
        try {
            return objectMapper.readValue(new File(SEMESTERS_DATA_FILENAME), new TypeReference<>() {});
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Failed to load semester data from file");
        }
        return Collections.emptyList();
    }

    public static void countRating() throws AuthenticationException {
        List<SemesterData> semestersData = loadDataFromFile();
        semestersData.forEach(Main::completeData);

        if (semestersData.isEmpty()) {
            LstuClient lstuClient = new LstuClient();
            lstuClient.login("s11916327", "f7LLDSJibCw8QNGeR6");
            semestersData = lstuClient.getSemestersData();
            lstuClient.logout();
            semestersData.stream()
                    .filter(semesterData -> !semesterData.getSubjects().isEmpty())
                    .forEach(semesterData -> {
                        completeData(semesterData);
                        System.out.println("Rating for the " + semesterData.getName() + " semester is " + countRating(semesterData));
                    });
        }
    }

    public static void checkNewDocuments() throws AuthenticationException {
        LstuClient lstuClient = new LstuClient();
        lstuClient.login("s11916327", "f7LLDSJibCw8QNGeR6");
        Map<String, Set<String>> semestersDocumentNames = lstuClient.getDocumentNames("2021-В");
        semestersDocumentNames.forEach((semesterName, documentNames) -> {
            System.out.println("Documents for "+semesterName+":");
            documentNames.stream().forEach(documentName -> System.out.print("\""+documentName+"\" "));
            System.out.println();
        });
        lstuClient.logout();
    }

    public static void main (String[] args) throws AuthenticationException {
        checkNewDocuments(); // TODO Сохранение и сравнение
    }
}
