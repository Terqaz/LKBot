package com.my;

import com.my.exceptions.ConnectionAttemptsException;
import com.my.utils.LstuRequests;
import com.my.utils.LstuUrlBuilder;
import org.apache.http.auth.AuthenticationException;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;

public class LstuClient {

    public static final String FAILED_LK_LOGIN = "Failed LK login";
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    private static final int ATTEMPTS_COUNT = 8;

    private final LstuRequests lstuRequests = new LstuRequests();

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

        final Response firstResponse = lstuRequests.openLoginPage();

        String phpSessId = firstResponse.header("Set-Cookie")
                .split(";")[0]
                .split("=")[1];

        Document document1 = firstResponse.parse();
        String sessId = document1.select("input[name=\"sessid\"]")
                .get(0)
                .attr("value");
        lstuRequests.updateSessionTokens(sessId, phpSessId);

        final Response response = lstuRequests.executeLoginRequest(
                LstuUrlBuilder.buildAuthUrl(login, password, sessId));

        final String jsonResponse = response.parse().body().text();
        if (jsonResponse.startsWith("{\"SUCCESS\":\"1\"")) {
            System.out.println("Login complete");
        } else {
            throw new AuthenticationException(FAILED_LK_LOGIN);
        }
    }

    public void logout () throws AuthenticationException {
        if (lstuRequests.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        try {
            lstuRequests.executeLogoutRequest(
                    LstuUrlBuilder.buildLogoutUrl());
        } catch (Exception e) {
            System.out.println("Logout failed");
        }
        System.out.println("Logout complete");
    }

    public List<SemesterData> getSemestersData () throws AuthenticationException {
        if (lstuRequests.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        Document document = lstuRequests.openPage(
                LstuUrlBuilder.buildGetSemestersUrl());

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
        Document subjectsPage = null;
        Elements htmlSubjectsTableColumnNames = null;
        for (int attemptsLeft = 0; attemptsLeft < ATTEMPTS_COUNT; attemptsLeft++) {
            subjectsPage = lstuRequests.openPage(
                    LstuUrlBuilder.buildGetByLocalUrl(localRef));
            htmlSubjectsTableColumnNames = subjectsPage.select("div.table-responsive").select("th");
            if (!htmlSubjectsTableColumnNames.isEmpty())
                break;
        }

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
            for (Map.Entry<String, Integer> entry : columnNames.entrySet()) {
                String columnName = entry.getKey();
                columnId = entry.getValue();
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

    // Map of document names for all subjects
    public Map<String, Set<String>> getDocumentNames (String semesterName) throws AuthenticationException {
        if (lstuRequests.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        String semesterLink = getSemesterLink(semesterName);
        final Document semesterDataPage = lstuRequests.openPage(LstuUrlBuilder.buildGetByLocalUrl(semesterLink));
        final Elements htmlSubjectsLinks = semesterDataPage.select("li.submenu.level3 > a");

        Map<String, Set<String>> documentNames = new HashMap<>();
        htmlSubjectsLinks.forEach(subjectLink -> {
            String link = subjectLink.attr("href");
            String name = subjectLink.text();
            final Set<String> semesterDocumentNames = getSemesterDocumentNames(link);
            if (!semesterDocumentNames.isEmpty())
                documentNames.put(name, semesterDocumentNames);
        });
        return documentNames;
    }

    private String getSemesterLink(String semesterName) {
        Document semestersListPage = lstuRequests.openPage(
                LstuUrlBuilder.buildGetSemestersUrl());
        final Elements htmlSemestersLinks = semestersListPage.select(".ul-main > li > a");
        for (Element link : htmlSemestersLinks) {
            if (link.text().equals(semesterName)) {
                return link.attr("href");
            }
        }
        return null;
    }

    private Set<String> getSemesterDocumentNames(String semesterLink) {
        final Document subjectDataPage = lstuRequests.openPage(LstuUrlBuilder.buildGetByLocalUrl(semesterLink));
        return new HashSet<>(subjectDataPage.select("ul.list-inline > li").eachText());
    }
}