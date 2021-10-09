package com.my.services;

import com.my.services.vk.VkBotService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Переменные окружения VK_GROUP_TOKEN
class VkBotServiceTest {

    VkBotService vkBot = VkBotService.getInstance();
    static final int APP_ADMIN_ID = 173315241;

    @Test
    void getUserNameIsCorrect () {
        assertEquals("Павел Дуров", vkBot.getUserName(1));
    }

    @Test
    void fileUpload () throws URISyntaxException {
        vkBot.sendMessageTo(APP_ADMIN_ID,
                new File(new URI("https://i.stack.imgur.com/C93kI.png")),
                "Тестовый файл");
    }
}