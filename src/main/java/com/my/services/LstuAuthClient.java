package com.my.services;

import com.my.models.AuthenticationData;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class LstuAuthClient {

    private static LstuAuthClient instance = null;

    public static LstuAuthClient getInstance () {
        if (instance == null)
            instance = new LstuAuthClient();
        return instance;
    }

    private LstuAuthClient () {}

    private static final LstuClient lstuClient = LstuClient.getInstance();

    public boolean login (AuthenticationData data) {
        if (lstuClient.isNotLoggedIn()) {
            try {
                return auth(data.getLogin(), data.getPassword());
            } catch (Exception e) {
                return false;
            }
        } else return true;
    }

    private boolean auth (String login, String password) throws IOException {
        final var firstResponse = lstuClient.openLoginPage();

        String phpSessId = firstResponse.header("Set-Cookie")
                .split(";")[0]
                .split("=")[1];

        final var document1 = firstResponse.parse();
        String sessId = document1.select("input[name=\"sessid\"]")
                .get(0)
                .attr("value");
        lstuClient.updateSessionTokens(sessId, phpSessId);

        final Document document = lstuClient.executeLoginRequest(
                LstuUrlBuilder.buildAuthUrl(login, password, sessId));

        final String jsonResponse = document.body().text();
        if (!jsonResponse.startsWith("{\"SUCCESS\":\"1\"")) {
            lstuClient.updateSessionTokens(null, null);
            return false;
        } else return true;
    }

    public void logout () {
        try {
            lstuClient.executeLogoutRequest(
                    LstuUrlBuilder.buildLogoutUrl());

        } catch (Exception ignored) {}
    }
}
