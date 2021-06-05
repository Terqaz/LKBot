package com.my;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.auth.AuthenticationException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    static final String SEMESTERS_DATA_FILENAME = "semesters.json";
    static final String SUBJECTS_DATA_FILENAME = "subjectsData.json";

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

        try {
            return objectMapper.readValue(new File(filename), typeReference);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    //TODO доделать
    public static void countRating() throws AuthenticationException, IOException {
        List<SemesterSubjects> semestersData
                = readFile(SEMESTERS_DATA_FILENAME, new TypeReference<>() {});
        semestersData.forEach(Main::completeData);

        if (semestersData.isEmpty()) {
            LstuClient lstuClient = new LstuClient();
            lstuClient.login("s11916327", "f7LLDSJibCw8QNGeR6");
            semestersData = lstuClient.getSemestersData();
            lstuClient.logout();
            semestersData.stream()
                    .filter(semesterSubjects -> !semesterSubjects.getSubjects().isEmpty())
                    .forEach(semesterSubjects -> {
                        completeData(semesterSubjects);
                        System.out.println("Rating for the " + semesterSubjects.getName() + " semester is " + countRating(semesterSubjects));
                    });
        }
    }

    public static Set<SubjectData> checkNewDocuments (String semester, String login, String password) throws AuthenticationException, IOException {
        LstuClient lstuClient = new LstuClient();
        lstuClient.login(login, password);
        login = password = null;

        final Set<SubjectData> oldDocuments
                = readFile(SUBJECTS_DATA_FILENAME, new TypeReference<>() {});

        Set<SubjectData> actualDocuments = lstuClient.getDocumentNames(semester);
        lstuClient.logout();

        if (!actualDocuments.isEmpty())
            writeFile(SUBJECTS_DATA_FILENAME, actualDocuments);

        if (oldDocuments != null) {
            Map<String, SubjectData> oldDocumentsMap = new HashMap<>();
            for (SubjectData data : oldDocuments) {
                oldDocumentsMap.put(data.getSubjectName(), data);
            }
            return actualDocuments.stream()
                    .peek(subjectData -> {
                        final String subjectName = subjectData.getSubjectName();
                        final Set<String> documents = subjectData.getDocumentNames();
                        if (oldDocuments.contains(subjectData))
                            documents.removeAll(oldDocumentsMap.get(subjectName).getDocumentNames());
                    })
                    .filter(subjectData -> !subjectData.getDocumentNames().isEmpty())
                    .collect(Collectors.toSet());
        } else {
            return actualDocuments;
        }
    }

    public static void checkNewDocumentsUsage (String semester, String login, String password) throws IOException, AuthenticationException {
        System.out.println("--- Список новых документов по предметам ---");
        checkNewDocuments(semester, login, password).forEach(subjectData -> {
            System.out.println(subjectData.getSubjectName()+":");
            subjectData.getDocumentNames().forEach (
                    documentName -> System.out.print("\""+documentName+"\" "));
            System.out.println();
        });
    }

    public static void main (String[] args) throws AuthenticationException, IOException {
        checkNewDocumentsUsage("2020-В","s11916327", "f7LLDSJibCw8QNGeR6");
    }
}
