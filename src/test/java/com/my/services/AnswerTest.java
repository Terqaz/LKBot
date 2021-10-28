package com.my.services;

import com.my.TestUtils;
import com.my.models.Subject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                "☑ Игорев ИИ в 11.09.2021 18:45:\n" +
                "Выкладывайте лабы)))\n\n" +
                "☑ Сергеев СС в 11.09.2021 18:45:\n" +
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

}