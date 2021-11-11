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
    @Disabled ("Пройден")
    void translateFromEnglishKeyboardLayout_isCorrect () {
        final List<String> expected =
                List.of("rjvfyls", "rjvFYls", "кjvFYls", "кjvF Yls", "rjvF Yls", "rjvF Yls 2", "кjvF Yls 2")
                .stream().map(KeyboardLayoutConverter::convertFromEngIfNeeds)
                .collect(Collectors.toList());

        final List<String> actual =
                List.of("команды", "комАНды", "кjvFYls", "кjvF Yls", "комА Нды", "комА Нды 2", "tjvF Ylш 2");

        assertIterableEquals(expected, actual);
    }

    @Test
    @Disabled ("Пройден")
    void ParserUtils_capitalize_IsCorrect () {
        assertEquals("Тест", TextUtils.capitalize("Тест"));
        assertEquals("Тест", TextUtils.capitalize("ТЕСТ"));
        assertEquals("Тест", TextUtils.capitalize("тест"));
        assertEquals("", TextUtils.capitalize(""));
        assertEquals("  ", TextUtils.capitalize("  "));
    }

    @Test
    @Disabled ("Пройден")
    void removeOldSubjectsDocuments_isCorrect () {
        List<Subject> oldSubjectData = createSubjects1();
        List<Subject> newSubjectData = createSubjects2();

        assertTrue(Utils.removeOldDocuments(oldSubjectData, Collections.emptyList()).isEmpty());

        TestUtils.assertDeepObjectEquals(
                createSubjects2(), Utils.removeOldDocuments(Collections.emptyList(), newSubjectData));

        List<Subject> postSubjectData = Utils.removeOldDocuments(oldSubjectData, newSubjectData);

        assertIterableEquals(createSubjects1(), oldSubjectData); // oldSubjectData не изменилась
        assertIterableEquals(createSubjects2(), newSubjectData); // newSubjectData не изменилась

        assertEquals(TestUtils.createDocuments("2abcdef"), postSubjectData.get(0).getMaterialsDocuments());
        assertEquals(TestUtils.createDocuments("3abcdef"), postSubjectData.get(1).getMaterialsDocuments());
        assertEquals(Set.of(
                new LkDocument().setName("4abc").setLkId("6"),
                new LkDocument().setName("4abcdef").setLkId("8")
        ), postSubjectData.get(2).getMaterialsDocuments());
    }

    @Test
    @Disabled ("Пройден")
    void setDocumentsIdsWhereNull_isCorrect () {

        assertDoesNotThrow(() -> Utils.setDocumentsIdsWhereNull(Collections.emptyList(), 1));

        List<LkDocument> documents =
                TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f");

        assertEquals(8, Utils.setDocumentsIdsWhereNull(documents, 1));
        assertIterableEquals(List.of(1,2,3,4,5,6,7), sortByNameAndGetIds(documents));

        documents = new ArrayList<>(
                TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f"));
        documents.get(2).setId(2);
        documents.get(4).setId(1);
        documents.get(5).setId(3);
        assertEquals(8, Utils.setDocumentsIdsWhereNull(new HashSet<>(documents), 4));
        assertIterableEquals(List.of(4, 5, 2, 6, 1, 3, 7), sortByNameAndGetIds(documents));
    }

    @Test
    @Disabled ("Пройден")
    void setIdsWhereNull_isCorrect() {
        assertDoesNotThrow(() -> Utils.setIdsWhereNull(new Subject("1", "2", Set.of(), Set.of(), List.of())));

        final var subject = createSubject1()
                .setMaterialsDocuments(TestUtils.createDocuments("0k", "1a", "2b", "3c", "4d", "5e", "6f"))
                .setMessagesDocuments(TestUtils.createDocuments("m0k", "m1a", "m2b", "m3c", "m4d", "m5e", "m6f"));
        Utils.setIdsWhereNull(subject);
        assertIterableEquals(List.of(1,2,3,4,5,6,7), sortByNameAndGetIds(subject.getMaterialsDocuments()));
        assertIterableEquals(List.of(8,9,10,11,12,13,14), sortByNameAndGetIds(subject.getMessagesDocuments()));

        final List<LkDocument> documents1 = TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f");
        final List<LkDocument> documents2 = TestUtils.createDocumentsList("m0k", "m1a", "m2b", "m3c", "m4d", "m5e", "m6f");

        documents1.get(2).setId(2);
        documents1.get(4).setId(1);
        documents1.get(5).setId(3);

        documents2.get(2).setId(5);
        documents2.get(4).setId(4);
        documents2.get(5).setId(6);

        subject.setMaterialsDocuments(new HashSet<>(documents1))
                .setMessagesDocuments(new HashSet<>(documents2));

        Utils.setIdsWhereNull(subject);

        assertIterableEquals(List.of(7, 8, 2, 9, 1, 3, 10), sortByNameAndGetIds(subject.getMaterialsDocuments()));
        assertIterableEquals(List.of(11, 12, 5, 13, 4, 6, 14), sortByNameAndGetIds(subject.getMessagesDocuments()));
    }

    @Test
    @Disabled ("Пройден")
    void copyIdsFromOldMaterialsDocuments_isCorrect() {
        assertDoesNotThrow(() -> Utils.copyIdsFrom(List.of(), List.of()));

        final List<LkDocument> oldDocuments = TestUtils.createDocumentsList("0k", "1a", "2b", "3c", "4d", "5e", "6f");

        assertDoesNotThrow(() -> Utils.copyIdsFrom(List.of(), oldDocuments));
        assertDoesNotThrow(() -> Utils.copyIdsFrom(oldDocuments, List.of()));

        oldDocuments.get(1).setId(3);
        oldDocuments.get(3).setId(1);
        oldDocuments.get(6).setId(2);

        Utils.setDocumentsIdsWhereNull(oldDocuments, 1);
        final Set<LkDocument> newDocuments = TestUtils.createDocuments("0k", "1a", "2b", "3c", "4d", "5e", "6f");
        Utils.copyIdsFrom(newDocuments, oldDocuments);
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
                createSubject1().setName("d").setMaterialsDocuments(TestUtils.createDocuments("3abc", "3abcd", "3abcde")),
                createSubject1().setName("e").setMaterialsDocuments(Set.of(
                        new LkDocument().setName("4ab").setLkId("1"),
                        new LkDocument().setName("4abc").setLkId("2"),
                        new LkDocument().setName("4abc").setLkId("3"),
                        new LkDocument().setName("4abc").setLkId("4"),
                        new LkDocument().setName("4abcd").setLkId("5"),
                        new LkDocument().setName("4abcde").setLkId("51")
                ))
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
                        .setName("d").setMaterialsDocuments(TestUtils.createDocuments("3abcd", "3abcde", "3abcdef")),
                createSubject1() // Удалили и добавили везде подряд
                        .setName("e").setMaterialsDocuments(Set.of(
                                new LkDocument().setName("4ab").setLkId("1"),
                                new LkDocument().setName("4abc").setLkId("6"),
                                new LkDocument().setName("4abc").setLkId("4"),
                                new LkDocument().setName("4abcdef").setLkId("8")
                        ))
        );
    }

    private Subject createSubject1() {
        return new Subject().setId(234634).setLkId("346346").setMessagesData(List.of()).setMessagesDocuments(Set.of());
    }

    private LkDocument createDocument1(String name, Integer id) {
        return new LkDocument(name, "lkid").setId(id);
    }

    @Test
    @Disabled ("Пройден")
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
    @Disabled ("Пройден")
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

//    @Test
//    void toUnixCompatibleName_isCorrect() {
//        final List<String> strings1 = List.of(
//                "Вопросы к защите по 1-й П.Р. (БЖД).exe",
//                "Вопросы к защите по 2-й П.Р. (БЖД).exe",
//                "Вопросы к защите по 3-й и 4-й П.Р. (БЖД).7z",
//                "Вопросы к защите по 5-й П.Р. (БЖД).1",
//                "Заготовка по 2-й практической работе (Вариант№1) (БЖД)",
//                "Заготовка по 3-й лабораторной работе (БЖД)",
//                "Лекция №3,4",
//                "МУ2820 ШУМ",
//                "Письмо для студентов как выполнять РГЗ и сроки сдачи на оценку",
//                "РГЗ по БЖД (задание)",
//                "Рабочая программа");
//        strings1.stream()
//                .map(TextUtils::toUnixCompatibleName)
//                .forEach(System.out::println);
//    }

//    @Test
//    void s () {
//        final Path path = Paths.get("11", "22");
//        System.out.println(path);
//        final Path path1 = Paths.get(path.toAbsolutePath().toString(), "44.txt");
//        System.out.println(path1);
//    }
}