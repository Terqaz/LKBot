package com.my.services;

import com.my.TestUtils;
import com.my.models.Subject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnswerTest {

    @Test
    @Disabled
    void getSubjects_isCorrect () throws ParseException {
        String report = "\uD83D\uDD34 Новые документы:\n" +
                "➡ 1 Матеша:\nлекция 1\nлекция 2\n\n" +
                "➡ 2 Прога:\nварианты\nрабочая программа\n\n" +
                "\uD83D\uDD34 Новые сообщения:\n" +
                "➡ 1 Матеша:\n" +
                "☑ Игорев ИИ в 11.09.2021 18:45:\n" +
                "Выкладывайте лабы)))\n\n" +
                "☑ Сергеев СС в 11.09.2021 18:45:\n" +
                "Выложил лр3\n\n" +
                "➡ 2 Прога:\n" +
                "☑ Олегов ОО в 11.09.2021 18:45:\n" +
                "Где лабы?\n\n" +
                "☑ Владимиров ВВ в 11.09.2021 18:45:\n" +
                "Прошел тест\n\n" +
                "Следующее глобальное обновление будет 11.09.2021 18:46";

        Date date2 = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse("11.09.2021 18:46");

        List<Subject> list = TestUtils.createSubjects();
        assertEquals(report, Answer.getSubjects(list, date2));

        String report2 = "\uD83D\uDD34 Новые документы:\n" +
                "➡ 1 Матеша:\nЛекция 1\n\n" +
                "➡ 2 Прога:\nЗадачи к практикам\nРабочая программа\n\n" +
                "Следующее глобальное обновление будет 11.09.2021 18:46";

        List<Subject> list2 = List.of(
                new Subject("1", "Матеша",
                        Set.of("Лекция 1"), List.of())
                        .setId(1),
                new Subject("2", "Прога",
                        Set.of("Задачи к практикам", "Рабочая программа"), List.of())
                        .setId(2),
                new Subject("3", "Пустой предмет", new TreeSet<>(List.of()), List.of())
                        .setId(3)
        );
        assertEquals(report2, Answer.getSubjects(list2, date2));
    }

}