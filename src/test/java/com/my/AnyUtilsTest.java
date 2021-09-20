package com.my;

import com.my.models.Subject;
import com.my.services.text.KeyboardLayoutConverter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith (MockitoExtension.class)
class AnyUtilsTest {

    @Test
    @Disabled
    void translateFromEnglishKeyboardLayout_isCorrect () {
        final List<String> expected =
                Arrays.asList("rjvfyls", "rjvFYls", "кjvFYls", "кjvF Yls", "rjvF Yls", "rjvF Yls 2", "кjvF Yls 2")
                .stream().map(KeyboardLayoutConverter::translateFromEnglishLayoutIfNeeds)
                .collect(Collectors.toList());

        final List<String> actual =
                Arrays.asList("команды", "комАНды", "кjvFYls", "кjvF Yls", "комА Нды", "комА Нды 2", "кjvF Yls 2");

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
        List<Subject> oldSubjectData = Arrays.asList(
                new Subject().setMessagesData(Arrays.asList())
                        .setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),
                new Subject().setMessagesData(Arrays.asList())
                        .setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd", "1abcde")),
                new Subject().setMessagesData(Arrays.asList())
                        .setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde")),
                new Subject().setMessagesData(Arrays.asList())
                        .setName("d").setDocumentNames(mutableSetOf("3abc", "3abcd", "3abcde"))
        );

        List<Subject> newSubjectData = Arrays.asList(
                new Subject().setMessagesData(Arrays.asList()) // Нет изменений
                        .setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),

                new Subject().setMessagesData(Arrays.asList())  // Удалили элемент
                        .setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd")),

                new Subject().setMessagesData(Arrays.asList()) // Добавили элемент
                        .setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde", "2abcdef")),

                new Subject().setMessagesData(Arrays.asList())  // Удалили и добавили элементы
                        .setName("d").setDocumentNames(mutableSetOf("3abcd", "3abcde", "3abcdef"))
        );

        List<Subject> postSubjectData = Utils.removeOldDocuments(oldSubjectData, newSubjectData);

        assertEquals(Arrays.asList(
                new Subject().setMessagesData(Arrays.asList())
                        .setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),
                new Subject().setMessagesData(Arrays.asList())
                        .setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd", "1abcde")),
                new Subject().setMessagesData(Arrays.asList())
                        .setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde")),
                new Subject().setMessagesData(Arrays.asList())
                        .setName("d").setDocumentNames(mutableSetOf("3abc", "3abcd", "3abcde"))
                ),
                newSubjectData); // newSubjectData не изменилась

        assertEquals(2, postSubjectData.size());
        assertEquals(Set.of("2abcdef"), postSubjectData.get(0).getDocumentNames());
        assertEquals(Set.of("3abcdef"), postSubjectData.get(1).getDocumentNames());
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
}