package com.my;

import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    public static final String LSTU_HOST_NAME = "lk.stu.lipetsk.ru";
    private final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0";
    private String sessId = null;
    private String phpSessId = null;

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
        final Response firstResponse = Jsoup.connect("http://lk.stu.lipetsk.ru/")
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive")
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
            lstuAuthUri = new URIBuilder()
                    .setScheme("http")
                    .setHost(LSTU_HOST_NAME)
                    .setPath("index.php")
                    .addParameter("AUTH_FORM", "1")
                    .addParameter("sessid", sessId)
                    .addParameter("LOGIN", login)
                    .addParameter("PASSWORD", password)
                    .build().toString();
        } catch (URISyntaxException e) {
            throw new AuthenticationException("Failed login in LK");
        }
        final Response response = Jsoup.connect(lstuAuthUri)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive")
                .cookie("PHPSESSID", phpSessId)
                .method(Connection.Method.POST)
                .execute();

        final String jsonResponseString = response.parse().body().text();
        if (jsonResponseString.startsWith("{\"SUCCESS\":\"1\"")) {
            System.out.println("Login complete");
        } else {
            throw new AuthenticationException("Login failed");
        }
    }

    public void logout() {
        final String lstuLogoutUri;

        try {
            lstuLogoutUri = new URIBuilder()
                    .setScheme("http")
                    .setHost(LSTU_HOST_NAME)
                    .addParameter("logout", "Y")
                    .build().toString();
        } catch (URISyntaxException e) {
            return;
        }

        try {
            Jsoup.connect(lstuLogoutUri)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html")
                    .header("Connection", "keep-alive")
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
            throw new AuthenticationException("You must be logged in before");
        }
        final String semestersUri;
        try {
            semestersUri = new URIBuilder()
                    .setScheme("http")
                    .setHost(LSTU_HOST_NAME)
                    .setPath("education/0/")
                    .build().toString();
        } catch (URISyntaxException ignore) {
            return Collections.emptyList();
        }

        Document document = Jsoup.connect(semestersUri)
                .userAgent(USER_AGENT)
                .header("Accept","text/html")
                .header("Connection", "keep-alive")
                .cookie("PHPSESSID", phpSessId)
                .get();

        final Elements htmlSemestersData = document.select("ul.ul-main > li");
        System.out.println(htmlSemestersData);
        List<SemesterData> semestersData = new ArrayList<>();

        int i = htmlSemestersData.size();
        for (Element htmlSemesterData : htmlSemestersData) {
            SemesterData semesterData = getSemesterData(htmlSemesterData);
            semesterData.setNumber(i);
            i--;
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
            semesterSubjectsUri = new URIBuilder()
                    .setScheme("http")
                    .setHost(LSTU_HOST_NAME)
                    .setPath(localRef)
                    .build().toString();
        } catch (URISyntaxException ignore) {
            return Collections.emptyList();
        }
        Document document = Jsoup.connect(semesterSubjectsUri)
                .userAgent(USER_AGENT)
                .header("Accept","text/html")
                .header("Connection", "keep-alive")
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
