package com.my.utils;

import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;

public class LstuUrlBuilder {

    public static final String LSTU_HOST_NAME = "lk.stu.lipetsk.ru";

    private static URIBuilder getLstuOriginUriBuilder () {
        return new URIBuilder()
                .setScheme("http")
                .setHost(LSTU_HOST_NAME);
    }

    private static String buildUrl(URIBuilder uriBuilder) {
        try {
            return uriBuilder.build().toString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    public static String buildAuthUrl (String login, String password, String sessId) {
        return buildUrl(getLstuOriginUriBuilder()
                .setPath("index.php")
                .addParameter("AUTH_FORM", "1")
                .addParameter("sessid", sessId)
                .addParameter("LOGIN", login)
                .addParameter("PASSWORD", password));
    }

    public static String buildLogoutUrl () {
        return buildUrl(getLstuOriginUriBuilder()
                .addParameter("logout", "Y"));
    }

    public static String buildGetSemestersUrl () {
        return buildUrl(getLstuOriginUriBuilder()
                .setPath("education/0/"));
    }

    public static String buildGetByLocalUrl (String localRef) {
        return buildUrl(getLstuOriginUriBuilder()
                .setPath(localRef));
    }
}
