package com.my.utils;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class LstuConnections {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0";

    private LstuConnections() {}

    private static Connection getOriginConnection (String url) {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive");
    }

    private static Connection getOriginSessionConnection(String url, String phpSessId) {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive")
                .cookie("PHPSESSID", phpSessId);
    }

    private static Response executeRequest(Connection connection) {
        try {
            return connection.execute();
        } catch (IOException e) {
            return null;
        }
    }

    public static Response executeLoginRequest (String authUrl, String phpSessId) {
        return executeRequest(getOriginSessionConnection(authUrl, phpSessId)
                .method(Connection.Method.POST));
    }

    public static void executeLogoutRequest (String logoutUrl, String phpSessId) {
        executeRequest(getOriginSessionConnection(logoutUrl, phpSessId)
                .method(Connection.Method.POST));
    }

    public static Response openLoginPage () {
        return executeRequest(getOriginConnection("http://lk.stu.lipetsk.ru/")
                .method(Connection.Method.GET));
    }

    public static Document openPage (String url, String phpSessId) {
        try {
            return getOriginSessionConnection(url, phpSessId)
                    .get();
        } catch (IOException e) {
            return null;
        }
    }
}
