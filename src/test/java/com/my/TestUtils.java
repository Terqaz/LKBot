package com.my;

import com.my.models.MessageData;
import com.my.models.Subject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class TestUtils {

    public static Date date1;

    static {
        try {
            date1 = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse("11.09.2021 18:45");
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static List<Subject> createSubjects() {
        return List.of(
                new Subject("1", "Матеша",
                        Set.of("лекция 1", "лекция 2"),
                        List.of(
                                new MessageData("Выкладывайте лабы)))", "Игорев ИИ", date1),
                                new MessageData("Выложил лр3", "Сергеев СС", date1)))
                        .setId(1),
                new Subject("2", "Прога",
                        Set.of("варианты", "рабочая программа"),
                        List.of(
                                new MessageData("Где лабы?", "Олегов ОО", date1),
                                new MessageData("Прошел тест", "Владимиров ВВ", date1)))
                        .setId(2),
                new Subject("3", "Пустой предмет", new TreeSet<>(List.of()), List.of())
                        .setId(3)
        );
    }
}
