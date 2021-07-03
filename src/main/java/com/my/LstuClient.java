package com.my;

import com.my.exceptions.AuthenticationException;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class LstuClient {

    private static LstuClient instance = null;

    public static LstuClient getInstance () {
        if (instance == null)
            instance = new LstuClient();
        return instance;
    }

    private LstuClient () {}

    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0";
    private static String sessId = null;
    private static String phpSessId = null;

    public void updateSessionTokens (String sessId, String phpSessId) {
        this.sessId = sessId;
        this.phpSessId = phpSessId;
    }

    public boolean isNotLoggedIn() {
        return (sessId == null || phpSessId == null);
    }

    private Connection getOriginConnection (String url) {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive");
    }

    private Connection getOriginSessionConnection(String url) {
        if (isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .cookie("PHPSESSID", phpSessId);
    }

    private Response executeRequest(Connection connection) {
        try {
            return connection.execute();
        } catch (IOException e) {
            return null;
        }
    }

    public Response executeLoginRequest (String authUrl) {
        return executeRequest(getOriginSessionConnection(authUrl)
                .method(Connection.Method.POST));
    }

    public void executeLogoutRequest (String logoutUrl){
        executeRequest(getOriginSessionConnection(logoutUrl)
                .method(Connection.Method.POST));
    }

    public Response openLoginPage () {
        return executeRequest(getOriginConnection("http://lk.stu.lipetsk.ru/")
                .method(Connection.Method.GET));
    }

    public Document get (String url) {
        try {
            return getOriginSessionConnection(url)
                    .get();
        } catch (IOException e) {
            return null;
        }
    }

    public Document post (String url) {
        try {
            return getOriginSessionConnection(url)
                    .post();
        } catch (IOException e) {
            return null;
        }
    }
}
