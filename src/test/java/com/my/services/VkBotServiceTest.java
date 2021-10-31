package com.my.services;

import com.my.services.vk.VkBotService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Переменные окружения VK_GROUP_TOKEN
class VkBotServiceTest {

    VkBotService vkBot = VkBotService.getInstance();
    static final int APP_ADMIN_ID = 173315241;
    static final int APP_ADMIN_FAKE = 267306707;

    @Test
    @Disabled ("Пройден")
    void getUserNameIsCorrect () {
        assertEquals("Павел Дуров", vkBot.getUserName(1));
    }

    @Test
    void fileUpload () {
        final String docAttachment = vkBot.sendMessageTo(APP_ADMIN_ID,
                new File("C:\\Users\\Terqaz\\Desktop\\1.txt"),
                "Тестовый файл");
        vkBot.sendMessageTo(APP_ADMIN_ID, docAttachment, "Тот же тестовый файл");
        vkBot.sendMessageTo(APP_ADMIN_FAKE, docAttachment, "Тестовый файл");
    }
}