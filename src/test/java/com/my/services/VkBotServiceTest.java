package com.my.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class VkBotServiceTest {

    VkBotService vkBot = VkBotService.getInstance();

    @Test
    void getUserNameIsCorrect () {
        assertEquals("Павел Дуров", vkBot.getUserName(1));
    }
}