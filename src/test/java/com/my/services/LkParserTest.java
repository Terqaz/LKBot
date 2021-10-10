package com.my.services;

import com.my.ParserUtils;
import com.my.Utils;
import com.my.exceptions.LoginNeedsException;
import com.my.models.*;
import com.my.models.enums.LkDocumentDestination;
import com.my.services.lk.LkParser;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    void loginTest() {
        assertDoesNotThrow(() -> lkParser.getGroupName());
        lkParser.logout();
        assertThrows(LoginNeedsException.class, () -> lkParser.getGroupName());

        String login = System.getenv("LOGIN");
        String password = System.getenv("PASSWORD");
        lkParser.login(new AuthenticationData(login, password));
        assertDoesNotThrow(() -> lkParser.getGroupName());
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
    @Disabled ("Пройден")
    void getSubjectsFirstTime_thenGetNewSubjects_thenCorrect() {
        // Получили первые данные
        final List<Subject> firstSubjects = lkParser.getSubjectsFirstTime(testSemester);
        log.info("First subjects loaded");

        assertTrue(firstSubjects.stream()
                .anyMatch(subjectData -> !subjectData.getMessagesData().isEmpty()));

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
                .allMatch(subjectData -> subjectData.getMessagesData().isEmpty()));

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

    @Test
    @Disabled ("Пройден")
    void changeEncodingIso_8859_1_Utf_8_isCorrect() {
        final var strings1 = List.of("Èíôîðìàöèîííîå ïðàâî.docx", "Çàçåìëåíèå_2590.doc",
                "fffffËåêöèÿ 01.04 Ïðåäïðèÿòèå,ff ïðîèçâîäñòâî, èçäåðæêè.docx").stream()
                .map(ParserUtils::changeEncodingIso_8859_1_Windows_1251)
                .collect(Collectors.toList());

        final var strings2 = List.of("Информационное право.docx", "Заземление_2590.doc",
                "fffffЛекция 01.04 Предприятие,ff производство, издержки.docx");

        assertIterableEquals(strings2, strings1);
    }

    @Test
    @Disabled ("Пройден")
    void loadFile_isCorrect () throws IOException {

        final var lkDocuments = List.of(
                // http://lk.stu.lipetsk.ru/file/me_teachingmaterials/5:110038482
                new LkDocument("тест1", "5:110038482", LkDocumentDestination.MATERIALS),

                // http://lk.stu.lipetsk.ru/file/me_teachingmaterials/5:111571856
                new LkDocument("тест2", "5:111571856", LkDocumentDestination.MATERIALS),

                // http://lk.stu.lipetsk.ru/file/me_msg_lk/5:108785375
                new LkDocument("тест3", "5:108785375", LkDocumentDestination.MESSAGE));

        String groupName = "ПИ-19-1";

        final var files = lkDocuments.stream()
                .map(lkDocument -> lkParser.loadFile(lkDocument, groupName, "Предмет"))
                .collect(Collectors.toList());

        assertTrue(files.stream().allMatch(File::exists));

        assertEquals(18127, files.get(0).length());
        assertEquals("Информационное право.docx", files.get(0).getName());
        assertEquals(4327936, files.get(1).length());
        assertEquals("Заземление_2590.doc", files.get(1).getName());
        assertEquals(56239, files.get(2).length());
        assertEquals("Лекция 01.04 Предприятие, производство, издержки.docx", files.get(2).getName());

        FileUtils.deleteDirectory(Path.of(groupName).toFile());
        assertFalse(Path.of(groupName).toFile().exists());
    }
}