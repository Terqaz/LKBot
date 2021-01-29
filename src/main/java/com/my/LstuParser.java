package com.my;

import com.my.exceptions.ConnectionAttemptsException;
import com.my.utils.LstuConnections;
import com.my.utils.LstuUrlBuilder;
import org.apache.http.auth.AuthenticationException;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;

public class LstuParser {

    public static final String FAILED_LK_LOGIN = "Failed LK login";
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    private static final int ATTEMPTS_COUNT = 8;
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
        final Response firstResponse = LstuConnections.openLoginPage();

        phpSessId = firstResponse.header("Set-Cookie")
                .split(";")[0]
                .split("=")[1];

        Document document1 = firstResponse.parse();
        sessId = document1.select("input[name=\"sessid\"]")
                .get(0)
                .attr("value");

        final Response response = LstuConnections.executeLoginRequest(
                LstuUrlBuilder.buildAuthUrl(login, password, sessId),
                phpSessId);

        final String jsonResponse = response.parse().body().text();
        if (jsonResponse.startsWith("{\"SUCCESS\":\"1\"")) {
            System.out.println("Login complete");
        } else {
            throw new AuthenticationException(FAILED_LK_LOGIN);
        }
    }

    public void logout () throws AuthenticationException {
        if (isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        try {
            LstuConnections.executeLogoutRequest(
                    LstuUrlBuilder.buildLogoutUrl(),
                    phpSessId);
        } catch (Exception e) {
            System.out.println("Logout failed");
        }
        System.out.println("Logout complete");
    }

    public List<SemesterData> getSemestersData () throws AuthenticationException {
        if (isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        Document document = LstuConnections.openPage(
                LstuUrlBuilder.buildGetSemestersUrl(),
                phpSessId);

        final Elements htmlSemestersData = document.select("ul.ul-main > li");
        List<SemesterData> semestersData = new ArrayList<>();

        int semesterNumber = htmlSemestersData.size();
        for (Element htmlSemesterData : htmlSemestersData) {
            SemesterData semesterData = getSemesterData(htmlSemesterData);
            semesterData.setNumber(semesterNumber);
            semesterNumber--;
            semestersData.add(semesterData);
        }
        return semestersData;
    }

    private SemesterData getSemesterData (Element element) {
        final SemesterData semesterData = new SemesterData();
        semesterData.setName(element.text());
        List<Subject> subjects = getSubjects(element.select("a").attr("href"));
        semesterData.setSubjects(subjects);
        return semesterData;
    }

    private List<Subject> getSubjects (String localRef) {
        int attemptsLeft = ATTEMPTS_COUNT;
        Document subjectsPage;
        Elements htmlSubjectsTableColumnNames;
        do {
            subjectsPage = LstuConnections.openPage(
                    LstuUrlBuilder.buildGetSubjectsUrl(localRef),
                    phpSessId);
            htmlSubjectsTableColumnNames = subjectsPage.select("div.table-responsive").select("th");
            attemptsLeft--;
            if (!htmlSubjectsTableColumnNames.isEmpty())
                break;
        } while (attemptsLeft > 0);

        if (htmlSubjectsTableColumnNames.isEmpty()) {
            throw new ConnectionAttemptsException("Too many attempts to get data. Try later");
        } else if (htmlSubjectsTableColumnNames.size() <= 3) { // Subjects without data
            return Collections.emptyList();
        }

        Map<String, Integer> columnNames = new HashMap<>();
        int columnId = 0;
        for (Element htmlColumnName : htmlSubjectsTableColumnNames) {
            columnNames.put(htmlColumnName.text(), columnId);
            columnId++;
        }
        final Elements htmlSubjects = subjectsPage.select("tr.eduProc");

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
                    default:
                        break;
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


    private boolean isNotLoggedIn () {
        return (sessId == null) || (phpSessId == null);
    }
}
