package com.my.services;

import com.my.ParserUtils;
import com.my.Utils;
import com.my.exceptions.LoginNeedsException;
import com.my.models.*;
import com.my.services.lk.LkParser;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// Переменные среды: LOGIN, PASSWORD
@Log4j2
class LkParserTest {

    static LkParser lkParser = new LkParser();

    static String testSemester = "2021-В";
    static String testSemester2 = "2021-О";
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
    @Disabled ("Пройден")
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
    @Disabled ("Пройден")
    void getSubjectsGeneralLkIds_IsCorrect () {
        final Map<String, String> ids = lkParser.getSubjectsGeneralLkIds(testSemester);
        assertEquals("5:94052862", ids.get(LkParser.SEMESTER_ID));
        assertEquals("5:94564750", ids.get(LkParser.GROUP_ID));
        assertEquals("5:95319097", ids.get(LkParser.CONTINGENT_ID));
    }

    @Test
    @Disabled ("Пройден")
    void parseTimetable_IsCorrect () {
        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(testSemester2);
        testGroup.setLkIds(
                lkIds.get(LkParser.SEMESTER_ID),
                lkIds.get(LkParser.GROUP_ID),
                lkIds.get(LkParser.CONTINGENT_ID)
        );
        // Семестр, в котором не поменяют расписание
        final Timetable timetable = lkParser.parseTimetable(testGroup.getLkSemesterId(), testGroup.getLkId());
        log.info("timetable loaded");

        assertEquals(16, listsSizeCount(timetable.getWhiteWeekDaySubjects()));
        assertEquals(17, listsSizeCount(timetable.getGreenWeekDaySubjects()));

        assertFalse(anyTimetableSubjectMatch(timetable, subject -> subject.getName().equals("")));
        assertFalse(anyTimetableSubjectMatch(timetable, subject -> subject.getInterval().equals("")));
        assertFalse(anyTimetableSubjectMatch(timetable, subject -> subject.getPlace().equals("")));

        assertEquals(timetable.getWhiteWeekDaySubjects().get(0).get(0),
                new TimetableSubject("Операционные системы", "Журавлева Марина Гарриевна",
                "11:20-12:50", "363, лабораторная"));

        assertEquals(timetable.getWhiteWeekDaySubjects().get(1).get(1), new TimetableSubject(
                "Общая физическая подготовка", "",
                "11:20-12:50", "спортзал, практика"));
    }

    private boolean anyTimetableSubjectMatch(Timetable timetable, Predicate<TimetableSubject> cond) {
        return timetable.getWhiteWeekDaySubjects().stream()
                .anyMatch(timetableSubjects ->
                        timetableSubjects.stream()
                                .anyMatch(cond));
    }

    private <T> int listsSizeCount(List<List<T>> lists) {
        return lists.stream().map(List::size).reduce(Integer::sum).orElse(0);
    }

    @Test
    @Disabled ("Пройден")
    void getSubjectsFirstTime_isCorrect () {
        final List<Subject> subjects = lkParser.getSubjectsFirstTime(testSemester);
        log.info("subjects loaded");

        final Subject subject1 = subjects.stream()
                .filter(subjectData -> subjectData.getLkId().equals("1:1751010"))
                .findFirst().orElse(null);
        assertNotNull(subject1);

        assertEquals("Правоведение", subject1.getName());
        assertEquals(Set.of("Рабочая программа", "ИДЗ", "Гражданское право (конспект)",
                "Уголовное право (конспект)", "Информационное право"),
                subject1.getMaterialsDocuments().stream()
                        .map(LkDocument::getName)
                        .collect(Collectors.toSet()));


        final Subject subject2 = subjects.stream()
                .filter(subjectData -> subjectData.getLkId().equals("1:9116307"))
                .findFirst().orElse(null);
        assertNotNull(subject2);

        assertEquals("Правоведение", subject1.getName());
        assertEquals("Черемисин ЕВ", subject2.getMessagesData().get(0).getSender());

        final Subject subject3 = subjects.stream()
                .filter(subjectData -> subjectData.getLkId().equals("1:1750023"))
                .findFirst().get();

        assertNotNull(subject3);
        assertEquals("Экономика", subject3.getName());
        assertNull(subject3.getMessagesData().get(4).getDocument());
        final LkDocument lkDocument1 = new LkDocument(6, "Лекция 01.04 Предприятие, производство, издержки", null, "5:108785375");
        assertEquals(lkDocument1,
                subject3.getMessagesData().get(5).getDocument());

        assertIterableEquals(sortDocumentsByName(Set.of(lkDocument1,
                new LkDocument(7, "Лекция 18.03 Равновесие потребителя", null, "5:108535269"),
                new LkDocument(8,"Тема 3. Рынок и его механизмы", null, "5:108490770"))),
                sortDocumentsByName(subject3.getMessagesDocuments()));
    }

    private List<LkDocument> sortDocumentsByName(Set<LkDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(LkDocument::getName))
                .collect(Collectors.toList());
    }

    @Test
    @Disabled ("Пройден")
    void getSubjectsFirstTime_thenGetNewSubjects_thenCorrect() {
        // Получили первые данные
        final List<Subject> firstSubjects = lkParser.getSubjectsFirstTime(testSemester);
        log.info("First subjects loaded");

        assertTrue(firstSubjects.stream() // Есть сообщения хотя бы где-то
                .anyMatch(subjectData -> !subjectData.getMessagesData().isEmpty()));

        // Получаем новые данные
        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(testSemester);
        testGroup.setLkIds(
                lkIds.get(LkParser.SEMESTER_ID),
                lkIds.get(LkParser.GROUP_ID),
                lkIds.get(LkParser.CONTINGENT_ID)
        );
        testGroup.setLastCheckDate(new Date());

        final List<Subject> newSubjects = lkParser.getNewSubjects(firstSubjects, testGroup);
        log.info("New subjects loaded");
        newSubjects.forEach(Utils::setIdsWhereNull);

        // Проверяем
        assertTrue(newSubjects.stream() // Новых сообщений нет нигде
                .allMatch(subjectData -> subjectData.getMessagesData().isEmpty()));

        final Subject oldSubject1 = assertDoesNotThrow(() ->
                firstSubjects.stream()
                        .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                        .findFirst()
                        .orElseThrow(NullPointerException::new));

        final Subject newSubject1 = assertDoesNotThrow(() ->
                newSubjects.stream()
                        .filter(subjectData -> subjectData.getName().equals("Правоведение"))
                        .findFirst()
                        .orElseThrow(NullPointerException::new));

        // Два раза подряд получили одно и то же
        assertIterableEquals( //TODO
                oldSubject1.getMaterialsDocuments().stream().sorted(Comparator.comparing(LkDocument::getName))
                        .collect(Collectors.toList()),
                newSubject1.getMaterialsDocuments().stream().sorted(Comparator.comparing(LkDocument::getName))
                        .collect(Collectors.toList()));
        assertTrue(Utils.removeOldDocuments(firstSubjects, newSubjects).isEmpty());
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
        String groupName = "ПИ-19-1";
        final String subjectName = "Предмет";

        final List<File> files = List.of(
                lkParser.loadMaterialsFile( // http://lk.stu.lipetsk.ru/file/me_teachingmaterials/5:110038482
                        new LkDocument("тест1", "5:110038482"), groupName, subjectName),

                lkParser.loadMaterialsFile( // http://lk.stu.lipetsk.ru/file/me_teachingmaterials/5:111571856
                        new LkDocument("тест2", "5:111571856"), groupName, subjectName),

                lkParser.loadMessageFile( // http://lk.stu.lipetsk.ru/file/me_msg_lk/5:108785375
                        new LkDocument("тест3", "5:108785375"), groupName, subjectName)
        );
        log.info("files loaded");

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

    // Нужно править assertTrue или assertFalse перед запуском теста
    @Test
    @Disabled ("Пройден")
    void weekType () {
        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(testSemester);
        assertTrue(lkParser.parseWeekType(lkIds.get(LkParser.SEMESTER_ID)));
    }
}