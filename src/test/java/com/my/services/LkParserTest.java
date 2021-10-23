package com.my.services;

import com.my.Utils;
import com.my.exceptions.LoginNeedsException;
import com.my.models.*;
import com.my.services.lk.LkParser;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Переменные среды: LOGIN, PASSWORD
@Log4j2
class LkParserTest {

    static LkParser lkParser = new LkParser();

    static String testSemester = "2021-В";
    static Group testGroup;

    @BeforeAll
    static void init () {
        String login = System.getenv("LOGIN");
        String password = System.getenv("PASSWORD");
        AuthenticationData data = new AuthenticationData(login, password);
        lkParser.login(data);
        loginTest(data);
    }

    static void loginTest(AuthenticationData data) {
        assertDoesNotThrow(() -> lkParser.getGroupName());
        lkParser.logout();
        assertThrows(LoginNeedsException.class, () -> lkParser.getGroupName());
        lkParser.login(data);
    }

    @AfterAll
    static void end () {
        lkParser.logout();
    }

    @BeforeEach
    void eachInit () {
        testGroup = new Group("Тест");
    }

    @Test
    void getSubjectsGeneralLkIds_IsCorrect () {
        final Map<String, String> ids = lkParser.getSubjectsGeneralLkIds(testSemester);
        assertEquals("5:94052862", ids.get(LkParser.SEMESTER_ID));
        assertEquals("5:94564750", ids.get(LkParser.GROUP_ID));
        assertEquals("5:95319097", ids.get(LkParser.CONTINGENT_ID));
    }

    @Test
    void parseTimetable_IsCorrect () {
        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(testSemester);
        testGroup.setLkIds(
                lkIds.get(LkParser.SEMESTER_ID),
                lkIds.get(LkParser.GROUP_ID),
                lkIds.get(LkParser.CONTINGENT_ID)
        );
        // Семестр, в котором не поменяют расписание
        final Timetable timetable =
                lkParser.parseTimetable(testGroup.getLkSemesterId(), testGroup.getLkId());

        assertEquals(15, listsSizeCount(timetable.getWhiteWeekDaySubjects()));
        assertEquals(18, listsSizeCount(timetable.getGreenWeekDaySubjects()));

        assertFalse(timetable.getWhiteWeekDaySubjects().stream()
                .anyMatch(timetableSubjects ->
                        timetableSubjects.stream()
                                .anyMatch(subject -> subject.getName().equals(""))));

        assertEquals(timetable.getWhiteWeekDaySubjects().get(0).get(0),
                new TimetableSubject("Численные методы", "Гаев Леонид Витальевич",
                "08:00-09:30", "255, лекция"));

        assertEquals(timetable.getWhiteWeekDaySubjects().get(1).get(0), new TimetableSubject(
                "Общая физическая подготовка", "",
                "08:00-09:30", "спортзал, практика"));

        assertEquals(timetable.getWhiteWeekDaySubjects().get(5).get(0), new TimetableSubject(
                "Современные платформы разработки программного обеспечения",
                "Овчинников Владимир Владимирович",
                "09:40-11:10", "9-405, лекция"));

        assertEquals(timetable.getGreenWeekDaySubjects().get(1).get(1), new TimetableSubject(
                "Статистические методы в прикладных задачах", "Рыжкова Дарья Васильевна",
                "09:40-11:10", "458, лекция"));
    }

    private <T> int listsSizeCount(List<List<T>> lists) {
        return lists.stream().map(List::size).reduce(Integer::sum).orElse(0);
    }

    @Test
    void getSubjectsFirstTime_isCorrect () {
        final List<Subject> subjectsData = lkParser.getSubjectsFirstTime(testSemester);

        assertEquals(subjectsData.stream()
                .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                .findFirst()
                .map(Subject::getDocumentNames)
                .orElse(null),
            Set.of("Рабочая программа", "ИДЗ", "Гражданское право (конспект)", "Уголовное право (конспект)", "Информационное право")
        );

        assertTrue(subjectsData.stream()
                .filter(subjectData -> subjectData.getLkId().equals("1:9116307"))
                .findFirst()
                .map(subjectData ->
                        subjectData.getName().equals("Основы электроники и схемотехники") &&
                        subjectData.getDocumentNames().contains("Рабочая программа") &&
                        subjectData.getMessages().get(0).getSender().equals("Черемисин ЕВ"))
                .orElse(false));

        assertTrue(subjectsData.stream()
                .filter(subjectData -> subjectData.getLkId().equals("1:1750023"))
                .findFirst()
                .map(subjectData ->
                        subjectData.getName().equals("Экономика") &&
                                subjectData.getDocumentNames().contains("Рабочая программа") &&
                                subjectData.getMessages().get(0).getSender().equals("Круглов ИВ"))
                .orElse(false));
    }

    @Test
    @Disabled
    void getSubjectsFirstTime_thenGetNewSubjects_thenCorrect() {
        // Получили первые данные
        final List<Subject> firstSubjects = lkParser.getSubjectsFirstTime(testSemester);
        log.info("First subjects loaded");

        assertTrue(firstSubjects.stream()
                .anyMatch(subjectData -> !subjectData.getMessages().isEmpty()));

        // Получаем новые данные
        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(testSemester);
        testGroup.setLkIds(
                lkIds.get(LkParser.SEMESTER_ID),
                lkIds.get(LkParser.GROUP_ID),
                lkIds.get(LkParser.CONTINGENT_ID)
        );
        testGroup.setLastCheckDate(new Date());

        final List<Subject> newSubjectsData = lkParser.getNewSubjects(firstSubjects, testGroup);
        log.info("New subjects loaded");

        // Проверяем
        assertTrue(newSubjectsData.stream()
                .allMatch(subjectData -> subjectData.getMessages().isEmpty()));

        final Subject oldSubject1 = assertDoesNotThrow(() ->
                firstSubjects.stream()
                        .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                        .findFirst()
                        .orElseThrow(NullPointerException::new));

        final Subject newSubject1 = assertDoesNotThrow(() ->
                newSubjectsData.stream()
                        .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                        .findFirst()
                        .orElseThrow(NullPointerException::new));

        assertEquals(oldSubject1.getDocumentNames(), newSubject1.getDocumentNames());
        assertTrue(Utils.removeOldDocuments(firstSubjects, newSubjectsData).isEmpty());
    }
}