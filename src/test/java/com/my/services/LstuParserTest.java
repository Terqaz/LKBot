package com.my.services;

import com.my.models.AuthenticationData;
import com.my.models.Group;
import com.my.models.TimetableSubject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LstuParserTest {

    static final LstuAuthClient lstuAuthClient = LstuAuthClient.getInstance();
    static LstuParser lstuParser = LstuParser.getInstance();

    static String testSemester = "2021-В";
    static Group testGroup;

    @BeforeEach
    void eachInit () {
        testGroup = new Group("Тест");
    }

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
        final Set<TimetableSubject> timetableSubjects =
                lstuParser.parseTimetable(testGroup.getLkSemesterId(), testGroup.getLkId());

        assertEquals(33, timetableSubjects.size());
        assertFalse(timetableSubjects.stream()
                .anyMatch(timetableSubject -> timetableSubject.getName().equals("")));

        assertTrue(timetableSubjects.contains(new TimetableSubject(
                "Численные методы", "Гаев Леонид Витальевич",
                "08:00-09:30", "255, лекция")));

        assertTrue(timetableSubjects.contains(new TimetableSubject(
                "Правоведение", "Мыздрикова Екатерина Александровна",
                "09:40-11:10", "9-325, практика")));

        assertTrue(timetableSubjects.contains(new TimetableSubject(
                "Экономика", "Митрофанова Ксения Николаевна",
                "09:40-11:10", "9-327, практика")));

        assertTrue(timetableSubjects.contains(new TimetableSubject(
                "Экономика", "Круглов Игорь Валерьевич",
                "11:20-12:50", "411, лекция")));

        assertTrue(timetableSubjects.contains(new TimetableSubject(
                "Современные платформы разработки программного обеспечения",
                "Овчинников Владимир Владимирович",
                "09:40-11:10", "9-405, лекция")));

        assertTrue(timetableSubjects.contains(new TimetableSubject(
                "Общая физическая подготовка","",
                "08:00-09:30", "спортзал, практика")));

        assertTrue(timetableSubjects.contains(new TimetableSubject(
                "Компьютерное моделирование","Гаев Леонид Витальевич",
                "13:20-14:50", "360, лабораторная")));
    }
}