package com.my.services;

import com.my.TestUtils;
import com.my.Utils;
import com.my.models.LkDocument;
import com.my.models.Subject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnswerTest {

    @Test
    @Disabled
    void getSubjects_isCorrect () {
        String report = "\uD83D\uDD34 Новые документы из материалов:\n" +
                "➡ 1 Матеша:\n2 лекция 1\n3 лекция 2\n\n" +
                "➡ 2 Прога:\n1 варианты\n2 рабочая программа\n\n" +
                "\uD83D\uDD34 Новые сообщения:\n" +
                "➡ 1 Матеша:\n" +
                "☑ Игорев ИИ сегодня в 18:45:\n" +
                "Выкладывайте лабы)))\n\n" +
                "☑ Сергеев СС сегодня в 18:45:\n" +
                "Выложил лр3\n" +
                "ДОКУМЕНТ: 1 ЛР3\n\n" +
                "➡ 2 Прога:\n" +
                "☑ Олегов ОО в 11.09.2021 18:45:\n" +
                "Где лабы?\n\n" +
                "☑ Владимиров ВВ в 11.09.2021 18:45:\n" +
                "Прошел тест";

        List<Subject> list = TestUtils.createSubjects1();
        assertEquals(report, Answer.getSubjects(list));

        String report2 = "\uD83D\uDD34 Новые документы из материалов:\n" +
                "➡ 1 Матеша:\n1 Лекция 1\n\n" +
                "➡ 2 Прога:\n1 Задачи к практикам\n2 Рабочая программа";

        List<Subject> list2 = TestUtils.createSubjects2();
        assertEquals(report2, Answer.getSubjects(list2));
    }

    @Test
    void getSubjectDocuments_isCorrect() {
        String report1 = "\uD83D\uDD34 Документы предмета Предмет\n" +
                "➡ Документы из материалов:\n" +
                "1 Документ 1\n" +
                "2 Документ 2\n" +
                "3 Документ 3";

        String report2 = "\uD83D\uDD34 Документы предмета Предмет\n" +
                "➡ Документы из материалов:\n" +
                "1 Документ 1\n" +
                "2 Документ 2\n" +
                "3 Документ 3\n" +
                "➡ Документы из сообщений:\n" +
                "Сергеев AС: 4 МДокумент 1\n" +
                "Сергеев БС: 5 МДокумент 2\n" +
                "Сергеев ВС: 6 МДокумент 3\n" +
                "Сергеев ГС: 7 МДокумент 4";

        String report3 = "\uD83D\uDD34 Документы предмета Предмет\n" +
                "➡ Документы из сообщений:\n" +
                "Сергеев AС: 4 МДокумент 1\n" +
                "Сергеев БС: 5 МДокумент 2\n" +
                "Сергеев ВС: 6 МДокумент 3\n" +
                "Сергеев ГС: 7 МДокумент 4";



        final Set<LkDocument> materialsDocuments = TestUtils.createDocuments("Документ 1", "Документ 2", "Документ 3");
        Subject subject = new Subject().setName("Предмет")
                .setMaterialsDocuments(materialsDocuments)
                .setMessagesDocuments(Set.of());

        Utils.setIdsWhereNull(subject);

        assertEquals(report1, Answer.getSubjectDocuments(subject));

        subject.setMessagesDocuments(Set.of(
                new LkDocument("МДокумент 1", "lkid").setSender("Сергеев AС"),
                new LkDocument("МДокумент 2", "lkid").setSender("Сергеев БС"),
                new LkDocument("МДокумент 3", "lkid").setSender("Сергеев ВС"),
                new LkDocument("МДокумент 4", "lkid").setSender("Сергеев ГС")
        ));

        Utils.setIdsWhereNull(subject);
        assertEquals(report2, Answer.getSubjectDocuments(subject));

        subject.setMaterialsDocuments(Set.of());
        assertEquals(report3, Answer.getSubjectDocuments(subject));

    }

}