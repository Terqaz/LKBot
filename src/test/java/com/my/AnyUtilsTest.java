package com.my;

import com.my.models.SubjectData;
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
                .stream().map(Utils::translateFromEnglishKeyboardLayoutIfNeeds)
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
        final List<SubjectData> oldSubjectData = List.of(
                new SubjectData().setMessagesData(List.of())
                        .setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),
                new SubjectData().setMessagesData(List.of())
                        .setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd", "1abcde")),
                new SubjectData().setMessagesData(List.of())
                        .setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde")),
                new SubjectData().setMessagesData(List.of())
                        .setName("d").setDocumentNames(mutableSetOf("3abc", "3abcd", "3abcde"))
        );

        final List<SubjectData> newSubjectData = List.of(
                new SubjectData().setMessagesData(List.of()) // Нет изменений
                        .setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),

                new SubjectData().setMessagesData(List.of())  // Удалили элемент
                        .setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd")),

                new SubjectData().setMessagesData(List.of()) // Добавили элемент
                        .setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde", "2abcdef")),

                new SubjectData().setMessagesData(List.of())  // Удалили и добавили элементы
                        .setName("d").setDocumentNames(mutableSetOf("3abcd", "3abcde", "3abcdef"))
        );

        final List<SubjectData> postSubjectData = Utils.removeOldDocuments(oldSubjectData, newSubjectData);

        assertEquals(List.of(
                new SubjectData().setMessagesData(List.of())
                        .setName("a").setDocumentNames(mutableSetOf("0abc", "0abcd", "0abcde")),
                new SubjectData().setMessagesData(List.of())
                        .setName("b").setDocumentNames(mutableSetOf("1abc", "1abcd", "1abcde")),
                new SubjectData().setMessagesData(List.of())
                        .setName("c").setDocumentNames(mutableSetOf("2abc", "2abcd", "2abcde")),
                new SubjectData().setMessagesData(List.of())
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
}