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
            System.out.println("Login succeeded");
        } else {
            throw new AuthenticationException("Failed login in LK");
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
            System.out.println("Logout successful");
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
            return Collections.EMPTY_LIST;
        }

        Map<String, Integer> columnNames = new HashMap<>();
        int columnId = 0;
        for (Element htmlColumnName : htmlSubjectsTableColumnNames) {
            columnNames.put(htmlColumnName.text(), columnId);
            columnId++;
        }
        final Elements htmlSubjects = document.select("tr.eduProc");
        System.out.println(htmlSubjects);

        List<Subject> subjects = new ArrayList<>();

        // TODO если 0 проверка
//        final Subject subject = new Subject();
//        for (Element element : htmlSubjects) {
//            final Elements innerTds = element.select("tr > td");
//            subject.setName(innerTds.get(0).text());
//            Optional.of(Integer.parseInt(innerTds.get(3).text())).ifPresent(subject::setSemesterWorkPoints);
//            Optional.of(Integer.parseInt(innerTds.get(4).text())).ifPresent(subject::setExamPoints);
//            Optional.of(Integer.parseInt(innerTds.get(5).text())).ifPresent(subject::setCreditPoints);
//        }
        return subjects;
    }


    private boolean isLoggedIn () {
        return (sessId != null) && (phpSessId != null);
    }
}
