package com.my.services;

import com.my.BotSecretInfoContainer;
import com.vk.api.sdk.client.ApiRequest;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.*;

import java.util.List;
import java.util.Random;

public class VkBotService {

    private static VkBotService instance = null;

    public static VkBotService getInstance () {
        if (instance == null)
            instance = new VkBotService();
        return instance;
    }

    private static final Random random = new Random();

    private static TransportClient transportClient;
    private static VkApiClient vk;
    private static GroupActor groupActor;
    private static Integer ts;

    private VkBotService () {
        transportClient = new HttpTransportClient();
        vk = new VkApiClient(transportClient);
        groupActor = new GroupActor(205287906, BotSecretInfoContainer.VK_TOKEN.getValue());
    }

    public <T> T executeRequest(ApiRequest<T> apiRequest) {
        try {
            return apiRequest.execute();
        } catch (ApiException | ClientException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateTs() {
        ts = executeRequest(vk.messages().getLongPollServer(groupActor)).getTs();
    }

    public List<Message> getNewMessages() {
        return executeRequest(vk.messages().getLongPollHistory(groupActor).ts(ts))
                .getMessages().getItems();
    }

    public void sendMessageTo (Integer userId, String message) {
        try {
            vk.messages().send(groupActor)
                    .message(message)
                    .userId(userId)
                    .randomId(random.nextInt(Integer.MAX_VALUE))
                    .execute();
        } catch (ApiException | ClientException e) {
            e.printStackTrace();
        }
    }

    public void sendMessageTo (Integer userId, Keyboard keyboard, String message) {
        try {
            vk.messages().send(groupActor)
                    .message(message)
                    .userId(userId)
                    .randomId(random.nextInt(Integer.MAX_VALUE))
                    .keyboard(keyboard)
                    .execute();
        } catch (ApiException | ClientException e) {
            e.printStackTrace();
        }
    }

    public static KeyboardButton generateButton (String text, KeyboardButtonColor color) {
        return new KeyboardButton()
                .setAction(new KeyboardButtonAction()
                        .setLabel(text)
                        .setType(TemplateActionTypeNames.TEXT))
                .setColor(color);
    }
}
