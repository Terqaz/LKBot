package com.my.services;

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

    // 4 параметр - не понятно, к чему этот id относится в ссылке на предмет
    public static String buildSubjectLocalUrl (String semester, String discipline, String group, String unknownId) {
        return finallyUrlBuild(new URIBuilder()
                .setPathSegments("education", "0", semester, discipline, group, unknownId));
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
        return finallyUrlBuild(builder);
    }

    public static String buildByLocalUrl (String localRef) {
        return finallyUrlBuild(getLstuOriginUriBuilder()
                .setPath(localRef));
    }

    // TODO Сохранять айдишники семестра и группы отдельно
    public static String buildStudentScheduleUrl (String semesterId, String groupId) {
        return finallyUrlBuild(getLstuOriginUriBuilder()
                .setPath("ajax.handler.php")
                .addParameter("student_schedule", "1")
                .addParameter("semester", semesterId)
                .addParameter("group", groupId)
        );
    }
}
