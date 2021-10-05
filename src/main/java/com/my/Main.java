package com.my;

import com.my.exceptions.ApplicationStopNeedsException;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class Main {

    public static void main (String[] args)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {

        Bot bot = new Bot();
        try {
            bot.startProcessing();
        } catch (ApplicationStopNeedsException e) {
            e.printStackTrace();
            bot.endProcessing();
        }
    }

    // TODO Новый функционал и оптимизация
    //  ответ на нецензурные и похвальные слова
    //  Получить 1к бесплатных часов на heroku (пока что не вышло)
    //  Удаление сообщения с данными входа (пока что не получилось, хотя согласно докам можно)

    // TODO Для массового распространения бота:
    //  оптимизация запросов к лк через очередь
}
