package com.my;

import com.my.models.LkDocument;
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
    @Disabled ("Пройден")
    void removeOldSubjectsDocuments_isCorrect () {
        List<Subject> oldSubjectData = createSubjects1();
        List<Subject> newSubjectData = createSubjects2();

        List<Subject> postSubjectData = Utils.removeOldDocuments(oldSubjectData, newSubjectData);

        assertIterableEquals(createSubjects1(), oldSubjectData); // oldSubjectData не изменилась
        assertIterableEquals(createSubjects2(), newSubjectData); // newSubjectData не изменилась

        assertEquals(2, postSubjectData.size());
        assertEquals(TestUtils.createDocuments("2abcdef"), postSubjectData.get(0).getMaterialsDocuments());
        assertEquals(TestUtils.createDocuments("3abcdef"), postSubjectData.get(1).getMaterialsDocuments());
    }

    @Test
    @Disabled ("Пройден")
    void setDocumentsIdsWhereNull_isCorrect () {

        List<LkDocument> documents =
                TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f");

        Utils.setDocumentsIdsWhereNull(documents, 0);

        documents = new ArrayList<>(
                TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f"));
        documents.get(2).setId(2);
        documents.get(4).setId(1);
        documents.get(5).setId(3);
        assertEquals(8, Utils.setDocumentsIdsWhereNull(new HashSet<>(documents), 4));
        assertIterableEquals(List.of(4, 5, 2, 6, 1, 3, 7), sortByNameAndGetIds(documents));
    }

    @Test
    void setIdsWhereNull_isCorrect() {
        createSubject1().setMaterialsDocuments(TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f"))
    }

    @Test
    @Disabled ("Пройден")
    void copyIdsFromOldMaterialsDocuments_isCorrect() {
        final List<LkDocument> oldDocuments = TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f");

        oldDocuments.get(1).setId(3);
        oldDocuments.get(3).setId(1);
        oldDocuments.get(6).setId(2);

        Utils.setDocumentsIdsWhereNull(oldDocuments, 1);
        final Set<LkDocument> newDocuments = TestUtils.createDocuments("0k", "1a", "2b", "3c", "4d", "5e", "6f");
        Utils.copyIdsFromOldMaterialsDocuments(newDocuments, oldDocuments);
        assertIterableEquals(sortByNameAndGetIds(oldDocuments), sortByNameAndGetIds(newDocuments));
    }

    private List<Integer> sortByNameAndGetIds(Collection<LkDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(LkDocument::getName))
                .map(LkDocument::getId)
                .collect(Collectors.toList());
    }

    private List<Subject> createSubjects1() {
        return List.of(
                createSubject1().setName("a").setMaterialsDocuments(TestUtils.createDocuments("0abc", "0abcd", "0abcde")),
                createSubject1().setName("b").setMaterialsDocuments(TestUtils.createDocuments("1abc", "1abcd", "1abcde")),
                createSubject1().setName("c").setMaterialsDocuments(TestUtils.createDocuments("2abc", "2abcd", "2abcde")),
                createSubject1().setName("d").setMaterialsDocuments(TestUtils.createDocuments("3abc", "3abcd", "3abcde"))
        );
    }

    private List<Subject> createSubjects2() {
        return List.of(
                createSubject1() // Нет изменений
                        .setName("a").setMaterialsDocuments(TestUtils.createDocuments("0abc", "0abcd", "0abcde")),
                createSubject1()  // Удалили элемент
                        .setName("b").setMaterialsDocuments(TestUtils.createDocuments("1abc", "1abcd")),
                createSubject1() // Добавили элемент
                        .setName("c").setMaterialsDocuments(TestUtils.createDocuments("2abc", "2abcd", "2abcde", "2abcdef")),
                createSubject1()  // Удалили и добавили элементы
                        .setName("d").setMaterialsDocuments(TestUtils.createDocuments("3abcd", "3abcde", "3abcdef"))
        );
    }

    private Subject createSubject1() {
        return new Subject().setId(234634).setLkId("346346").setMessagesData(List.of()).setMessagesDocuments(Set.of());
    }

    private LkDocument createDocument1(String name, Integer id) {
        return new LkDocument(name, "lkid").setId(id);
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