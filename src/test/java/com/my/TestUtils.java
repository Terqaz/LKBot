package com.my;

import com.google.gson.Gson;
import com.my.models.LkDocument;
import com.my.models.LkMessage;
import com.my.models.Subject;
import org.junit.jupiter.api.Assertions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

public class TestUtils {

    public static List<Subject> createSubjects1() {
        LocalDateTime date1 = Utils.responseParseMessageDate("11.09.2021 18:45");

        final LocalDateTime date2 = Utils.responseParseMessageDate(
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " 18:45");

        final LkDocument msgDocument = new LkDocument("ЛР3", "lkid").setId(1).setSender("Сергеев СС");
        return List.of(
                new Subject(1,"lk1", "Матеша",
                        createDocuments(2,"лекция 1", "лекция 2"),
                        Set.of(msgDocument),
                        List.of(
                                new LkMessage("Выкладывайте лабы)))", "Игорев ИИ", date2),
                                new LkMessage("Выложил лр3", "Сергеев СС", date2, msgDocument)), LocalDateTime.now(), null),

                new Subject(2, "lk2", "Прога",
                        createDocuments(1, "варианты", "рабочая программа"),
                        emptySet(),
                        List.of(
                                new LkMessage("Где лабы?", "Олегов ОО", date1),
                                new LkMessage("Прошел тест", "Владимиров ВВ", date1)), LocalDateTime.now(), null),

                new Subject(3,"lk3", "Пустой предмет", emptySet(), emptySet(),
                        Collections.emptyList(), LocalDateTime.now(), null)
        );
    }

    public static Set<LkDocument> createDocuments(String... names) {
        return Stream.of(names).map(name -> new LkDocument(name, name)).collect(Collectors.toSet());
    }

    public static List<LkDocument> createDocumentsList(String... names) {
        return Stream.of(names).map(name -> new LkDocument(name, name)).collect(Collectors.toList());
    }

    public static Set<LkDocument> createDocuments(Integer nextId, String... names) {
        final Set<LkDocument> documents = Stream.of(names)
                .map(name -> new LkDocument(name, "lkId"))
                .collect(Collectors.toSet());
        Utils.setDocumentsIdsWhereNull(documents, nextId);
        return documents;
    }

    public static List<Subject> createSubjects2() {
        return List.of(
                new Subject("1", "Матеша",
                        createDocuments(1,"Лекция 1"), emptySet(), List.of(), LocalDateTime.now())
                        .setId(1),
                new Subject("2", "Прога",
                        createDocuments(1, "Задачи к практикам", "Рабочая программа"), emptySet(), List.of(), LocalDateTime.now())
                        .setId(2),
                new Subject("3", "Пустой предмет", emptySet(), emptySet(), emptyList(), LocalDateTime.now())
                        .setId(3)
        );
    }

    public static <T> int listsSizeCount(List<List<T>> lists) {
        return lists.stream().map(List::size).reduce(Integer::sum).orElse(0);
    }

    public static final  Gson gson = new Gson();

    public static void assertDeepObjectEquals(Object o1, Object o2) {
        Assertions.assertEquals(gson.toJson(o1), gson.toJson(o2));
    }
}
