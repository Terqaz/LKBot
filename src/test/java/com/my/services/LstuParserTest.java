package com.my.services;

import com.my.Utils;
import com.my.models.*;
import com.my.services.lstu.LstuAuthClient;
import com.my.services.lstu.LstuParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LstuParserTest {

    static final LstuAuthClient lstuAuthClient = LstuAuthClient.getInstance();
    static LstuParser lstuParser = LstuParser.getInstance();

    static String testSemester = "2021-В";
    static Group testGroup;

    @BeforeAll
    static void init () {
        String login = System.getenv("LOGIN");
        String password = System.getenv("PASSWORD");
        lstuAuthClient.login(new AuthenticationData(login, password));
    }

    @AfterAll
    static void end () {
        lstuAuthClient.logout();
    }

    @BeforeEach
    void eachInit () {
        testGroup = new Group("Тест");
    }

    @Test
    void getSubjectsGeneralLkIds_IsCorrect () {
        final Map<String, String> ids = lstuParser.getSubjectsGeneralLkIds(testSemester);
        assertEquals("5:94052862", ids.get(LstuParser.SEMESTER_ID));
        assertEquals("5:94564750", ids.get(LstuParser.GROUP_ID));
        assertEquals("5:95319097", ids.get(LstuParser.CONTINGENT_ID));
    }

    @Test
    void parseTimetable_IsCorrect () {
        final Map<String, String> lkIds = lstuParser.getSubjectsGeneralLkIds(testSemester);
        testGroup.setLkIds(
                lkIds.get(LstuParser.SEMESTER_ID),
                lkIds.get(LstuParser.GROUP_ID),
                lkIds.get(LstuParser.CONTINGENT_ID)
        );
        // Семестр, в котором не поменяют расписание
        final Timetable timetable =
                lstuParser.parseTimetable(testGroup.getLkSemesterId(), testGroup.getLkId());

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
    void getSubjectsDataFirstTime_isCorrect () {
        final List<SubjectData> subjectsData = lstuParser.getSubjectsDataFirstTime(testSemester);

        assertEquals(subjectsData.stream()
                .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                .findFirst()
                .map(SubjectData::getDocumentNames)
                .orElse(null),
            Set.of("Рабочая программа", "ИДЗ", "Гражданское право (конспект)", "Уголовное право (конспект)", "Информационное право")
        );

        assertTrue(subjectsData.stream()
                .filter(subjectData -> subjectData.getLkId().equals("1:9116307"))
                .findFirst()
                .map(subjectData ->
                        subjectData.getName().equals("Основы электроники и схемотехники") &&
                        subjectData.getDocumentNames().contains("Рабочая программа") &&
                        subjectData.getMessagesData().get(0).getSender().equals("Черемисин ЕВ"))
                .orElse(false));

        assertTrue(subjectsData.stream()
                .filter(subjectData -> subjectData.getLkId().equals("1:1750023"))
                .findFirst()
                .map(subjectData ->
                        subjectData.getName().equals("Экономика") &&
                                subjectData.getDocumentNames().contains("Рабочая программа") &&
                                subjectData.getMessagesData().get(0).getSender().equals("Круглов ИВ"))
                .orElse(false));
    }

    @Test
    void getSubjectsDataFirstTime_thenGetNewSubjectsData_isCorrect () {

        // Получили первые данные
        final List<SubjectData> firstSubjectsData = lstuParser.getSubjectsDataFirstTime(testSemester);

        assertTrue(firstSubjectsData.stream()
                .anyMatch(subjectData -> !subjectData.getMessagesData().isEmpty()));

        // Получаем новые данные
        final Map<String, String> lkIds = lstuParser.getSubjectsGeneralLkIds(testSemester);
        testGroup.setLkIds(
                lkIds.get(LstuParser.SEMESTER_ID),
                lkIds.get(LstuParser.GROUP_ID),
                lkIds.get(LstuParser.CONTINGENT_ID)
        );
        testGroup.setLastCheckDate(new Date());

        final List<SubjectData> newSubjectsData = lstuParser.getNewSubjectsData(firstSubjectsData, testGroup);

        // Проверяем
        assertTrue(newSubjectsData.stream()
                .allMatch(subjectData -> subjectData.getMessagesData().isEmpty()));

        final SubjectData oldSubjectData1 = assertDoesNotThrow(() ->
                firstSubjectsData.stream()
                        .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                        .findFirst()
                        .orElseThrow(NullPointerException::new));

        final SubjectData newSubjectData1 = assertDoesNotThrow(() ->
                newSubjectsData.stream()
                        .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                        .findFirst()
                        .orElseThrow(NullPointerException::new));

        assertEquals(oldSubjectData1.getDocumentNames(), newSubjectData1.getDocumentNames());

        assertTrue(Utils.removeOldDocuments(firstSubjectsData, newSubjectsData).isEmpty());
    }
}