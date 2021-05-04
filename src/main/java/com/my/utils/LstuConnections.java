package com.my.utils;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class LstuConnections {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0";

    private String sessId = null;
    private String phpSessId = null;

    public LstuConnections () {}

    public void updateSessionTokens (String sessId, String phpSessId) {
        this.sessId = sessId;
        this.phpSessId = phpSessId;
    }

    public String getSessId () {
        return sessId;
    }

    public String getPhpSessId () {
        return phpSessId;
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
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
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

    public void executeLogoutRequest (String logoutUrl) {
        executeRequest(getOriginSessionConnection(logoutUrl)
                .method(Connection.Method.POST));
    }

    public Response openLoginPage () {
        return executeRequest(getOriginConnection("http://lk.stu.lipetsk.ru/")
                .method(Connection.Method.GET));
    }

    public Document openPage (String url) {
        try {
            return getOriginSessionConnection(url)
                    .get();
        } catch (IOException e) {
            return null;
        }
    }
}
