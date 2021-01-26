package com.my.utils;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class LstuConnections {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0";

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

    public static Response openLoginPage () throws IOException {
        return getOriginConnection("http://lk.stu.lipetsk.ru/")
                .method(Connection.Method.GET)
                .execute();
    }

    public static Response executeLoginRequest (String authUrl, String phpSessId) throws IOException {
        return getOriginSessionConnection(authUrl, phpSessId)
                .method(Connection.Method.POST)
                .execute();
    }

    public static void executeLogoutRequest (String logoutUrl, String phpSessId) throws IOException {
        getOriginSessionConnection(logoutUrl, phpSessId)
                .method(Connection.Method.POST)
                .execute();
    }

    public static Document openPage (String url, String phpSessId) throws IOException {
        return getOriginSessionConnection(url, phpSessId)
                .get();
    }
}
