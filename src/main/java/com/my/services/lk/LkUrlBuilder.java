package com.my.services.lk;

import com.my.Utils;
import com.my.models.LkDocument;
import org.apache.http.client.utils.URIBuilder;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;

public class LkUrlBuilder {

    public static final String AJAX_HANDLER_PHP = "ajax.handler.php";

    private LkUrlBuilder() {}

    public static final String LSTU_HOST_NAME = "lk.stu.lipetsk.ru";

    public static URIBuilder getLstuOriginUriBuilder () {
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

    private static URL buildUrl(URIBuilder uriBuilder) {
        try {
            return uriBuilder.build().toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
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

    public static String buildSubjectStringUrl(String semesterId, String disciplineId,
                                               String groupId, String contingentId) {
        return build(createSubjectUri(semesterId, disciplineId, groupId, contingentId));
    }

    public static URL buildSubjectUrl(String semesterId, String disciplineId,
                                      String groupId, String contingentId) {
        return buildUrl(createSubjectUri(semesterId, disciplineId, groupId, contingentId));
    }

    private static URIBuilder createSubjectUri(String semesterId, String disciplineId, String groupId, String contingentId) {
        return getLstuOriginUriBuilder()
                .setPathSegments("education", "0", semesterId, disciplineId, groupId, contingentId, "");
    }

    public static String buildNextMessagesUrl (String semesterId, String disciplineId, String groupId, LocalDateTime date) {
        final URIBuilder builder = getLstuOriginUriBuilder()
                .setPath(AJAX_HANDLER_PHP)
                .addParameter("get_msg", "1")
                .addParameter("group", groupId)
                .addParameter("discipline", disciplineId)
                .addParameter("semester", semesterId);
                if (date != null)
                    builder.addParameter("last_msg", Utils.queryFormatMessageDate(date));
        return build(builder);
    }

    public static String buildByLocalUrl (String localRef) {
        return build(getLstuOriginUriBuilder()
                .setPath(localRef));
    }

    public static String buildStudentScheduleUrl (String semesterId, String groupId) {
        return build(getLstuOriginUriBuilder()
                .setPath(AJAX_HANDLER_PHP)
                .addParameter("student_schedule", "1")
                .addParameter("semester", semesterId)
                .addParameter("group", groupId)
        );
    }

    public static String buildKeepAuthUrl() {
        return build(getLstuOriginUriBuilder()
            .addParameter("auth", "1")
        );
    }

    public static URL buildMaterialsDocumentUrl(LkDocument document) {
        return buildUrl(getLstuOriginUriBuilder().setPathSegments("file", "me_teachingmaterials", document.getLkId()));
    }

    public static URL buildMessageDocumentUrl(LkDocument document) {
        return buildUrl(getLstuOriginUriBuilder().setPathSegments("file", "me_msg_lk", document.getLkId()));
    }
}
