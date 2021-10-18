package com.my;

import com.my.models.Subject;
import com.my.services.text.KeyboardLayoutConverter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith (MockitoExtension.class)
class AnyUtilsTest {

    @Test
    @Disabled
    void translateFromEnglishKeyboardLayout_isCorrect () {
        final List<String> expected =
                List.of("rjvfyls", "rjvFYls", "кjvFYls", "кjvF Yls", "rjvF Yls", "rjvF Yls 2", "кjvF Yls 2")
                .stream().map(KeyboardLayoutConverter::translateFromEnglishLayoutIfNeeds)
                .collect(Collectors.toList());

        final List<String> actual =
                List.of("команды", "комАНды", "кjvFYls", "кjvF Yls", "комА Нды", "комА Нды 2", "кjvF Yls 2");

        assertIterableEquals(expected, actual);
    }

    @Test
    @Disabled
    void ParserUtils_capitalize_IsCorrect () {
        assertEquals("Тест", ParserUtils.capitalize("Тест"));
        assertEquals("Тест", ParserUtils.capitalize("ТЕСТ"));
        assertEquals("Тест", ParserUtils.capitalize("тест"));
        assertThrows(StringIndexOutOfBoundsException.class,
                () -> ParserUtils.capitalize(""));
    }

    @Test
    @Disabled
    void removeOldSubjectsDocuments_isCorrect () {
        List<Subject> oldSubjectData = createSubjects1();
        List<Subject> newSubjectData = createSubjects2();

        List<Subject> postSubjectData = Utils.removeOldDocuments(oldSubjectData, newSubjectData);

        assertIterableEquals(createSubjects1(), oldSubjectData); // oldSubjectData не изменилась
        assertIterableEquals(createSubjects2(), newSubjectData); // newSubjectData не изменилась

        assertEquals(2, postSubjectData.size());
        assertEquals(Set.of("2abcdef"), postSubjectData.get(0).getDocumentNames());
        assertEquals(Set.of("3abcdef"), postSubjectData.get(1).getDocumentNames());
    }

    private List<Subject> createSubjects1() {
        return List.of(
                createSubject1().setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),
                createSubject1().setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd", "1abcde")),
                createSubject1().setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde")),
                createSubject1().setName("d").setDocumentNames(mutableSetOf("3abc", "3abcd", "3abcde"))
        );
    }

    private List<Subject> createSubjects2() {
        return List.of(
                createSubject1() // Нет изменений
                        .setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),
                createSubject1()  // Удалили элемент
                        .setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd")),
                createSubject1() // Добавили элемент
                        .setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde", "2abcdef")),
                createSubject1()  // Удалили и добавили элементы
                        .setName("d").setDocumentNames(mutableSetOf("3abcd", "3abcde", "3abcdef"))
        );
    }

    private Subject createSubject1() {
        return new Subject().setId(234634).setLkId("346346").setMessagesData(List.of());
    }

    @SafeVarargs
    private <T> Set<T> mutableSetOf(T... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

    @Test
    @Disabled
    void isSilentTime_isCorrect () {
        assertTrue(Utils.isSilentTime(2, 6, 2));
        assertTrue(Utils.isSilentTime(2, 6, 6));
        assertTrue(Utils.isSilentTime(2, 6, 4));
        assertFalse(Utils.isSilentTime(2, 6, 1));

        assertTrue(Utils.isSilentTime(22, 4, 22));
        assertTrue(Utils.isSilentTime(22, 4, 23));
        assertTrue(Utils.isSilentTime(22, 4, 1));
        assertTrue(Utils.isSilentTime(22, 4, 4));
        assertFalse(Utils.isSilentTime(22, 4, 12));
    }

    @Test
    void getSleepTimeToHourStart_isCorrect () {
        assertEquals( 3600L * 1000 , Utils.getSleepTimeToHourStart(0, 0));
        assertEquals( 1800L * 1000 , Utils.getSleepTimeToHourStart(30, 0));
        assertEquals( 1L * 1000 , Utils.getSleepTimeToHourStart(59, 59));
    }

    @Test
    void getThisWeekDayIndex_isCorrect() {
        assertEquals(6, (Calendar.SUNDAY - 2 + 7) % 7);
        assertEquals(0, (Calendar.MONDAY - 2 + 7) % 7);
    }
}