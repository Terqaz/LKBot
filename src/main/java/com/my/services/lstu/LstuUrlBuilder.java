package com.my.services.lstu;

import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LstuUrlBuilder {

    private LstuUrlBuilder () {}

    public static final String LSTU_HOST_NAME = "lk.stu.lipetsk.ru";

    private static URIBuilder getLstuOriginUriBuilder () {
        return new URIBuilder()
                .setScheme("http")
                .setHost(LSTU_HOST_NAME);
    }

    private static String build(URIBuilder uriBuilder) {
        try {
            return uriBuilder.build().toString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    public static String buildAuthUrl (String login, String password, String sessId) {
        return build(getLstuOriginUriBuilder()
                .setPath("index.php")
                .addParameter("AUTH_FORM", "1")
                .addParameter("sessid", sessId)
                .addParameter("LOGIN", login)
                .addParameter("PASSWORD", password));
    }

    public static String buildLogoutUrl () {
        return build(getLstuOriginUriBuilder()
                .addParameter("logout", "Y"));
    }

    public static String buildSemesterUrl (String semesterId) {
        return build(getLstuOriginUriBuilder()
                .setPathSegments("education", "0", semesterId, ""));
    }

    public static String buildSemestersUrl () {
        return build(getLstuOriginUriBuilder()
                .setPath("education/0/"));
    }

    public static String buildSubjectLocalUrl (String semesterId, String disciplineId,
                                               String groupId, String contingentId) {
        return build(new URIBuilder()
                .setPathSegments("education", "0", semesterId, disciplineId, groupId, contingentId, ""));
    }

    public static String buildNextMessagesUrl (String semesterId, String disciplineId, String groupId, Date date) {
        final URIBuilder builder = getLstuOriginUriBuilder()
                .setPath("ajax.handler.php")
                .addParameter("get_msg", "1")
                .addParameter("group", groupId)
                .addParameter("discipline", disciplineId)
                .addParameter("semester", semesterId);
                if (date != null)
                    builder.addParameter("last_msg",
                            new SimpleDateFormat("dd.MM.yyyy+HH:mm").format(date));
        return build(builder);
    }

    public static String buildByLocalUrl (String localRef) {
        return build(getLstuOriginUriBuilder()
                .setPath(localRef));
    }

    public static String buildStudentScheduleUrl (String semesterId, String groupId) {
        return build(getLstuOriginUriBuilder()
                .setPath("ajax.handler.php")
                .addParameter("student_schedule", "1")
                .addParameter("semester", semesterId)
                .addParameter("group", groupId)
        );
    }
}