package com.my.services;

import com.my.LstuClient;
import com.my.LstuUrlBuilder;
import org.apache.http.auth.AuthenticationException;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class LstuAuthService {

    public static final String FAILED_LK_LOGIN = "Failed LK login";
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";

    private static final LstuClient lstuClient = LstuClient.getInstance();

    public boolean login (String login, String password) throws AuthenticationException {
        try {
            return auth(login, password);
        } catch (Exception e) {
            throw new AuthenticationException(FAILED_LK_LOGIN);
        }
    }

    private boolean auth (String login, String password) throws IOException {

        final Response firstResponse = lstuClient.openLoginPage();

        String phpSessId = firstResponse.header("Set-Cookie")
                .split(";")[0]
                .split("=")[1];

        Document document1 = firstResponse.parse();
        String sessId = document1.select("input[name=\"sessid\"]")
                .get(0)
                .attr("value");
        lstuClient.updateSessionTokens(sessId, phpSessId);

        final Response response = lstuClient.executeLoginRequest(
                LstuUrlBuilder.buildAuthUrl(login, password, sessId));

        final String jsonResponse = response.parse().body().text();
        return jsonResponse.startsWith("{\"SUCCESS\":\"1\"");
    }

    public void logout () {
        try {
            lstuClient.executeLogoutRequest(
                    LstuUrlBuilder.buildLogoutUrl());
        } catch (Exception e) {
            System.out.println("Logout failed");
        }
        System.out.println("Logout complete");
    }
}
