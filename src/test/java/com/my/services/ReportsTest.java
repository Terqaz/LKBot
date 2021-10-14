package com.my.services;

import com.my.TestUtils;
import com.my.models.Subject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportsTest {

    @Test
    @Disabled
    void getSubjects_isCorrect () throws ParseException {
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
                "Прошел тест\n\n" +
                "Следующее обновление в 11.09.2021 18:46";

        Date date2 = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse("11.09.2021 18:46");

        List<Subject> list = TestUtils.createSubjects1();
        assertEquals(report, Reports.getSubjects(list, date2));

        String report2 = "\uD83D\uDD34 Новые документы из материалов:\n" +
                "➡ 1 Матеша:\n1 Лекция 1\n\n" +
                "➡ 2 Прога:\n1 Задачи к практикам\n2 Рабочая программа\n\n" +
                "Следующее обновление в 11.09.2021 18:46";

        List<Subject> list2 = TestUtils.createSubjects2();
        assertEquals(report2, Reports.getSubjects(list2, date2));
    }

}