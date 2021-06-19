package com.my.services;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.*;

import java.util.Random;

public class VkBotService {

    private static final Random random = new Random();

    GroupActor groupActor;
    VkApiClient vk;

    public VkBotService (VkApiClient vk, GroupActor groupActor) {
        this.vk = vk;
        this.groupActor = groupActor;
    }

    public void sendMessageTo (Integer userId, String message) throws ClientException, ApiException {
        vk.messages().send(groupActor)
                .message(message)
                .userId(userId)
                .randomId(random.nextInt(10000))
                .execute();
    }
    public void sendMessageTo (Integer userId, Keyboard keyboard, String message) throws ClientException, ApiException {
        vk.messages().send(groupActor)
                .message(message)
                .userId(userId)
                .randomId(random.nextInt(10000))
                .keyboard(keyboard)
                .execute();
    }

    public static KeyboardButton generateButton (String text, KeyboardButtonColor color) {
        return new KeyboardButton()
                .setAction(new KeyboardButtonAction()
                        .setLabel(text)
                        .setType(TemplateActionTypeNames.TEXT))
                .setColor(color);
    }
}
