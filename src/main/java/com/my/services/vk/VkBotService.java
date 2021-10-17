package com.my.services.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonSyntaxException;
import com.vk.api.sdk.client.ApiRequest;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.responses.GetLongPollServerResponse;
import com.vk.api.sdk.objects.users.GetNameCase;
import com.vk.api.sdk.objects.users.responses.GetResponse;
import com.vk.api.sdk.queries.users.UsersGetQuery;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class VkBotService {

    private static VkBotService instance = null;

    public static VkBotService getInstance () {
        if (instance == null)
            instance = new VkBotService();
        return instance;
    }

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    final Keyboard emptyKeyboard = new Keyboard().setOneTime(true).setButtons(Collections.emptyList());

    private static final Random random = new Random();

    private static TransportClient transportClient;
    private static VkApiClient vk;
    private static GroupActor groupActor;

    private static String server;
    private static String key;
    @Getter @Setter
    private static Integer ts;
    private static Integer longPollVersion = 3;

    private boolean unsetKeyboard = false;

    private static final Integer groupId = 205287906;

    private VkBotService () {
        transportClient = new HttpTransportClient();
        vk = new VkApiClient(transportClient);
        groupActor = new GroupActor(groupId, System.getenv("VK_GROUP_TOKEN"));

        final var response = getLongPollServer();
        server = response.getServer();
        key = response.getKey();
        ts = response.getTs();
    }

    private GetLongPollServerResponse getLongPollServer () {
        return executeRequest(vk.messages()
                .getLongPollServer(groupActor)
                .lpVersion(longPollVersion));
    }

    public <T> T executeRequest(ApiRequest<T> apiRequest) {
        try {
            return apiRequest.execute();
        } catch (JsonSyntaxException ignore) {
        } catch (ApiException | ClientException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Stream<Message> getNewMessages () {
        try {
            List<Message> messages;
            do {
                messages = fetchNewMessages();
            } while (messages.isEmpty());
            return messages.stream()
                    // На всякий случай, чтобы бот не ответил на свое сообщение
                    .filter(message -> message.getFromId() > 0);

        } catch (IOException e) {
            e.printStackTrace();
            return Stream.empty();
        }
    }

    private List<Message> fetchNewMessages () throws IOException {
        final var response = transportClient.get(
                "https://"+server+"?act=a_check&key="+key+"&ts="+ts+"&wait=25&version="+longPollVersion);
        final var jsonResponse = jsonMapper.readTree(response.getContent());

        if (!jsonResponse.hasNonNull("failed")) {
            final var updates = jsonResponse.get("updates").elements();
            while (updates.hasNext()) {
                JsonNode array = updates.next();
                if (array.elements().next().asInt() == 4) {
                    final var messages = executeRequest(
                            vk.messages().getLongPollHistory(groupActor).ts(ts)
                    ).getMessages().getItems();
                    ts = jsonResponse.get("ts").asInt();
                    return messages;
                }
            }
        } else {
            updateLongPollParams(jsonResponse);
        }
        return Collections.emptyList();
    }

    private void updateLongPollParams (JsonNode jsonResponse) {
        final var errorCode = jsonResponse.get("failed").asInt();
        final GetLongPollServerResponse response;
        switch (errorCode) {
            case 1:
                ts = jsonResponse.get("ts").asInt();
                break;
            case 2:
                response = getLongPollServer();
                server = response.getServer();
                key = response.getKey();
                break;
            case 3:
                response = getLongPollServer();
                server = response.getServer();
                key = response.getKey();
                ts = response.getTs();
                break;
            case 4:
                longPollVersion = jsonResponse.get("max_version").asInt();
                break;
        }
    }

    public void sendMessageTo (@NotNull Integer userId, Keyboard keyboard, String message) {
        final var query = vk.messages().send(groupActor)
                .message(message)
                .userId(userId)
                .randomId(random.nextInt(Integer.MAX_VALUE))
                .dontParseLinks(true)
                .keyboard(keyboard);
        executeRequest(query);
    }

    public void sendMessageTo (@NotNull Integer userId, String message) {
        final var query = vk.messages().send(groupActor)
                .message(message)
                .userId(userId)
                .randomId(random.nextInt(Integer.MAX_VALUE))
                .dontParseLinks(true);
        if (unsetKeyboard) {
            query.keyboard(emptyKeyboard);
            unsetKeyboard = false;
        }
        executeRequest(query);
    }

    public void sendMessageTo (Collection<Integer> userIds, String message) {
        if (userIds.isEmpty()) return;

        final var query = vk.messages().send(groupActor)
                .message(message)
                .peerIds(new ArrayList<>(userIds))
                .randomId(random.nextInt(Integer.MAX_VALUE))
                .dontParseLinks(true);
        if (unsetKeyboard) {
            query.keyboard(emptyKeyboard);
            unsetKeyboard = false;
        }
        executeRequest(query);
    }

    // Дублировал, чтобы каждый раз не вылетала ошибка из вк API о неизвестном респонсе
    public void sendLongMessageTo (@NotNull Integer userId, String message) {
        var i = 0;
        if (message.length() > 4000)
            for (; i < message.length()-4000; i+=4000)
                sendMessageTo(userId, message.substring(i, i+4000));

        sendMessageTo(userId, message.substring(i));
    }

    public void sendLongMessageTo (@NotEmpty Collection<Integer> userIds, String message) {
        if (userIds.isEmpty()) return;

        var i = 0;
        if (message.length() > 4000)
            for (; i < message.length()-4000; i+=4000)
                sendMessageTo(userIds, message.substring(i, i+4000));

        sendMessageTo(userIds, message.substring(i));
    }

    public void deleteLastMessage (Message message) {
        var query = vk.messages().delete(groupActor)
                .deleteForAll(true)
                .messageIds(message.getId())
                //.messageIds(message.getId())
                .unsafeParam("peer_id", message.getPeerId());
//                .unsafeParam("conversation_message_ids", message.getConversationMessageId());
        executeRequest(query);

//        query = vk.messages().delete(groupActor)
//                .deleteForAll(true)
//                //.messageIds(message.getId())
//                .unsafeParam("peer_id", 2000000000+message.getPeerId())
//                .unsafeParam("conversation_message_ids", message.getConversationMessageId());
//        executeRequest(query);

        final var response = executeRequest(vk.messages().getConversations(groupActor));
    }

    public String getUserName(Integer userId) {
        final UsersGetQuery query = vk.users().get(groupActor)
                .userIds(userId.toString())
                .nameCase(GetNameCase.NOMINATIVE);
        final GetResponse response = executeRequest(query).get(0);
        return response.getFirstName() + " " + response.getLastName();
    }

    public void unsetKeyboard() {
        unsetKeyboard = true;
    }

    public void setOnline (boolean isOnline) {
        if (isOnline) vk.groups().enableOnline(groupActor, groupId);
        else          vk.groups().disableOnline(groupActor, groupId);
    }
}
