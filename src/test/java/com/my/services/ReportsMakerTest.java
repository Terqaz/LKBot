package com.my.services;

import com.my.models.MessageData;
import com.my.models.SubjectData;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportsMakerTest {

    @Test
    @Disabled
    void getSubjectsData_isCorrect () throws ParseException {
        String report = "\uD83D\uDD34 Новые документы:\n" +
                "➡ 1 Матеша: лекция 1, лекция 2\n" +
                "➡ 2 Прога: варианты, рабочая программа\n\n" +
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

        Date date1 = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse("11.09.2021 18:45");
        Date date2 = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse("11.09.2021 18:46");

        List<SubjectData> list = List.of(
                new SubjectData("1", "Матеша",
                        new TreeSet<>(List.of("лекция 1", "лекция 2")),
                        List.of(
                                new MessageData("Выкладывайте лабы)))", "Игорев ИИ", date1),
                                new MessageData("Выложил лр3", "Сергеев СС", date1)))
                .setId(1),
                new SubjectData("2", "Прога",
                        new TreeSet<>(List.of("варианты", "рабочая программа")),
                        List.of(
                                new MessageData("Где лабы?", "Олегов ОО", date1),
                                new MessageData("Прошел тест", "Владимиров ВВ", date1)))
                .setId(2),
                new SubjectData("3", "Пустой предмет", new TreeSet<>(List.of()), List.of())
                        .setId(3)
        );
        assertEquals(report, ReportsMaker.getSubjectsData(list, date2));

        String report2 = "\uD83D\uDD34 Новые документы:\n" +
                "➡ 1 Матеша: Лекция 1\n" +
                "➡ 2 Прога: Задачи к практикам, Рабочая программа\n\n" +
                "Следующее глобальное обновление будет 11.09.2021 18:46";

        List<SubjectData> list2 = List.of(
                new SubjectData("1", "Матеша",
                        new TreeSet<>(List.of("Лекция 1")), List.of())
                        .setId(1),
                new SubjectData("2", "Прога",
                        new TreeSet<>(List.of("Задачи к практикам", "Рабочая программа")), List.of())
                        .setId(2),
                new SubjectData("3", "Пустой предмет", new TreeSet<>(List.of()), List.of())
                        .setId(3)
        );
        assertEquals(report2, ReportsMaker.getSubjectsData(list2, date2));
    }

}