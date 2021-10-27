package com.my.services;

import com.my.services.vk.VkBotService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Переменные окружения VK_GROUP_TOKEN
class VkBotServiceTest {

    VkBotService vkBot = VkBotService.getInstance();
    static final int APP_ADMIN_ID = 173315241;

    @Test
    @Disabled ("Пройден")
    void getUserNameIsCorrect () {
        assertEquals("Павел Дуров", vkBot.getUserName(1));
    }

    @Test
    void fileUpload () throws URISyntaxException {
        vkBot.sendMessageTo(APP_ADMIN_ID,
                new File("D:\\Mega\\Learning\\HW\\BD\\ê°€.docx"),
                "Тестовый файл");
    }
}