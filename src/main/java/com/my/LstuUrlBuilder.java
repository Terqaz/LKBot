package com.my;

import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LstuUrlBuilder {

    public static final String LSTU_HOST_NAME = "lk.stu.lipetsk.ru";

    private static URIBuilder getLstuOriginUriBuilder () {
        return new URIBuilder()
                .setScheme("http")
                .setHost(LSTU_HOST_NAME);
    }

    private static String finallyUrlBuild (URIBuilder uriBuilder) {
        try {
            return uriBuilder.build().toString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    public static String buildAuthUrl (String login, String password, String sessId) {
        return finallyUrlBuild(getLstuOriginUriBuilder()
                .setPath("index.php")
                .addParameter("AUTH_FORM", "1")
                .addParameter("sessid", sessId)
                .addParameter("LOGIN", login)
                .addParameter("PASSWORD", password));
    }

    public static String buildLogoutUrl () {
        return finallyUrlBuild(getLstuOriginUriBuilder()
                .addParameter("logout", "Y"));
    }

    public static String buildSemestersUrl () {
        return finallyUrlBuild(getLstuOriginUriBuilder()
                .setPath("education/0/"));
    }

    public static String buildNextMessagesUrl (String group, String discipline, String semester, Date date) {
        final URIBuilder builder = getLstuOriginUriBuilder()
                .setPath("ajax.handler.php")
                .addParameter("get_msg", "1")
                .addParameter("group", group)
                .addParameter("discipline", discipline)
                .addParameter("semester", semester);
                if (date != null)
                    builder.addParameter("last_msg",
                            new SimpleDateFormat("dd.MM.yyyy+HH:mm").format(date));
        return finallyUrlBuild(builder);
    }

    public static String buildByLocalUrl (String localRef) {
        return finallyUrlBuild(getLstuOriginUriBuilder()
                .setPath(localRef));
    }
}
