package com.my;

import com.my.models.LkDocument;
import com.my.models.LkMessage;
import com.my.models.Subject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

public class TestUtils {

    public static Date date1;

    static {
        try {
            date1 = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse("11.09.2021 18:45");
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static List<Subject> createSubjects1() {
        final LkDocument document = new LkDocument("ЛР3", "lkid").setId(1);
        return List.of(
                new Subject("1", "Матеша",
                        createDocuments(2,"лекция 1", "лекция 2"),
                        Set.of(document),
                        List.of(
                                new LkMessage("Выкладывайте лабы)))", "Игорев ИИ", date1),
                                new LkMessage("Выложил лр3", "Сергеев СС", date1, document)))
                        .setId(1),
                new Subject("2", "Прога",
                        createDocuments(1, "варианты", "рабочая программа"),
                        emptySet(),
                        List.of(
                                new LkMessage("Где лабы?", "Олегов ОО", date1),
                                new LkMessage("Прошел тест", "Владимиров ВВ", date1)))
                        .setId(2),
                new Subject("3", "Пустой предмет", emptySet(), emptySet(),
                        Collections.emptyList())
                        .setId(3)
        );
    }

    public static Set<LkDocument> createDocuments(String... names) {
        return Stream.of(names).map(name -> new LkDocument(name, "lkId")).collect(Collectors.toSet());
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
                        createDocuments(1,"Лекция 1"), emptySet(), List.of())
                        .setId(1),
                new Subject("2", "Прога",
                        createDocuments(1, "Задачи к практикам", "Рабочая программа"), emptySet(), List.of())
                        .setId(2),
                new Subject("3", "Пустой предмет", emptySet(), emptySet(), emptyList())
                        .setId(3)
        );
    }
}
