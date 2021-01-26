package com.my;

import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class LstuParser {

    public static final String FAILED_LK_LOGIN = "Failed LK login";

    public static final String LSTU_HOST_NAME = "lk.stu.lipetsk.ru";
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    private final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0";
    private String sessId = null;
    private String phpSessId = null;

    private URIBuilder getLstuOriginUriBuilder() {
        return new URIBuilder()
                .setScheme("http")
                .setHost(LSTU_HOST_NAME);
    }

    private Connection getLstuOriginConnection(String url) {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive");
    }

    public void login (String login, String password) throws AuthenticationException {
        try {
            auth(login, password);
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException(FAILED_LK_LOGIN);
        }
    }

    private void auth (String login, String password) throws IOException, AuthenticationException {
        final Response firstResponse = getLstuOriginConnection("http://lk.stu.lipetsk.ru/")
                .method(Connection.Method.POST)
                .execute();

        phpSessId = firstResponse.header("Set-Cookie")
                .split(";")[0]
                .split("=")[1];

        Document document1 = firstResponse.parse();
        sessId = document1.select("input[name=\"sessid\"]")
                .get(0)
                .attr("value");

        final String lstuAuthUri;
        try {
            lstuAuthUri = getLstuOriginUriBuilder()
                    .setPath("index.php")
                    .addParameter("AUTH_FORM", "1")
                    .addParameter("sessid", sessId)
                    .addParameter("LOGIN", login)
                    .addParameter("PASSWORD", password)
                    .build().toString();
        } catch (URISyntaxException e) {
            throw new AuthenticationException(FAILED_LK_LOGIN);
        }
        final Response response = getLstuOriginConnection(lstuAuthUri)
                .cookie("PHPSESSID", phpSessId)
                .method(Connection.Method.POST)
                .execute();

        final String jsonResponseString = response.parse().body().text();
        if (jsonResponseString.startsWith("{\"SUCCESS\":\"1\"")) {
            System.out.println("Login complete");
        } else {
            throw new AuthenticationException(FAILED_LK_LOGIN);
        }
    }

    public void logout() throws AuthenticationException {
        if (!isLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        final String lstuLogoutUri;

        try {
            lstuLogoutUri = getLstuOriginUriBuilder()
                    .addParameter("logout", "Y")
                    .build().toString();
        } catch (URISyntaxException e) {
            return;
        }

        try {
            getLstuOriginConnection(lstuLogoutUri)
                    .cookie("PHPSESSID", phpSessId)
                    .method(Connection.Method.POST)
                    .execute();
            System.out.println("Logout complete");
        } catch (IOException e) {
            System.out.println("Logout failed");
        }
    }

    public List<SemesterData> getSemestersData() throws AuthenticationException, IOException {
        if (!isLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        final String semestersUri;
        try {
            semestersUri = getLstuOriginUriBuilder()
                    .setPath("education/0/")
                    .build().toString();
        } catch (URISyntaxException ignore) {
            return Collections.emptyList();
        }

        Document document = getLstuOriginConnection(semestersUri)
                .cookie("PHPSESSID", phpSessId)
                .get();

        final Elements htmlSemestersData = document.select("ul.ul-main > li");
        System.out.println(htmlSemestersData);
        List<SemesterData> semestersData = new ArrayList<>();

        int semesterNumber = htmlSemestersData.size();
        for (Element htmlSemesterData : htmlSemestersData) {
            SemesterData semesterData = getSemesterData(htmlSemesterData);
            semesterData.setNumber(semesterNumber);
            semesterNumber--;
            semestersData.add(semesterData);
        }
        logout();
        return semestersData;
    }

    private SemesterData getSemesterData(Element element) throws IOException {
        final SemesterData semesterData = new SemesterData();
        semesterData.setName(element.text());
        semesterData.setSubjects(getSubjects(element.select("a").attr("href")));
        return semesterData;
    }

    private List<Subject> getSubjects (String localRef) throws IOException {
        final String semesterSubjectsUri;
        try {
            semesterSubjectsUri = getLstuOriginUriBuilder()
                    .setPath(localRef)
                    .build().toString();
        } catch (URISyntaxException ignore) {
            return Collections.emptyList();
        }
        Document document = getLstuOriginConnection(semesterSubjectsUri)
                .cookie("PHPSESSID", phpSessId)
                .get();
        final Elements htmlSubjectsTableColumnNames = document.select("div.table-responsive").select("th");
        if (htmlSubjectsTableColumnNames.size() <= 3) {
            return Collections.emptyList();
        }

        Map<String, Integer> columnNames = new HashMap<>();
        int columnId = 0;
        for (Element htmlColumnName : htmlSubjectsTableColumnNames) {
            columnNames.put(htmlColumnName.text(), columnId);
            columnId++;
        }
        final Elements htmlSubjects = document.select("tr.eduProc");
        System.out.println(htmlSubjects);

        final List<Subject> subjects = new ArrayList<>();
        for (Element element : htmlSubjects) {
            final Subject subject = new Subject();
            final Elements htmlTableRow = element.select("tr > td");
            for (Map.Entry entry : columnNames.entrySet()) {
                String columnName = (String) entry.getKey();
                columnId = (Integer) entry.getValue();
                switch (columnName) {
                    case "Дисциплина":
                        subject.setName(htmlTableRow.get(columnId).text());
                        break;
                    case "Семестр":
                        subject.setSemesterWorkPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Зачет":
                        subject.setCreditPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Экзамен":
                        subject.setExamPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Курсовая работа":
                        subject.setCourseWorkPoints(parseInt(htmlTableRow, columnId));
                        break;
                    default: break;
                }
            }
            subjects.add(subject);
        }
        return subjects;
    }

    private int parseInt (Elements htmlTableRow, int columnId) {
        final int value;
        try {
            value = Integer.parseInt(htmlTableRow.get(columnId).text());
        } catch (NumberFormatException e) {
            return -1;
        }
        return value;
    }


    private boolean isLoggedIn () {
        return (sessId != null) && (phpSessId != null);
    }
}
