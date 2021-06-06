package com.my;

import com.my.exceptions.ConnectionAttemptsException;
import org.apache.http.auth.AuthenticationException;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LstuClient {

    public static final String FAILED_LK_LOGIN = "Failed LK login";
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    private static final int ATTEMPTS_COUNT = 8;
    private static final String UNKNOWN_ACADEMIC_NAME = "*УТОЧНИТЕ ИМЯ*";

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

    public List<SemesterSubjects> getSemestersData () throws AuthenticationException {
        if (lstuRequests.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        Document document = lstuRequests.get(
                LstuUrlBuilder.buildGetSemestersUrl());

        final Elements htmlSemestersData = document.select("ul.ul-main > li");
        List<SemesterSubjects> semestersData = new ArrayList<>();

        int semesterNumber = htmlSemestersData.size();
        for (Element htmlSemesterData : htmlSemestersData) {
            SemesterSubjects semesterSubjects = getSemesterData(htmlSemesterData);
            semesterSubjects.setNumber(semesterNumber);
            semesterNumber--;
            semestersData.add(semesterSubjects);
        }
        return semestersData;
    }

    private SemesterSubjects getSemesterData (Element element) {
        final SemesterSubjects semesterSubjects = new SemesterSubjects();
        semesterSubjects.setName(element.text());
        List<Subject> subjects = getSubjects(element.select("a").attr("href"));
        semesterSubjects.setSubjects(subjects);
        return semesterSubjects;
    }

    private List<Subject> getSubjects (String localRef) {
        Document subjectsPage = null;
        Elements htmlSubjectsTableColumnNames = null;
        for (int attemptsLeft = 0; attemptsLeft < ATTEMPTS_COUNT; attemptsLeft++) {
            subjectsPage = lstuRequests.get(
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
    public Set<SubjectData> getDocumentNames (String semesterName) throws AuthenticationException {
        if (lstuRequests.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        return Stream.of(getSemesterLink(semesterName))
                .map(semesterLink -> lstuRequests.get(LstuUrlBuilder.buildGetByLocalUrl(semesterLink)))
                .flatMap(semesterDataPage -> semesterDataPage.select("li.submenu.level3 > a").stream()
                        .map(htmlLink -> {
                            String subjectLink = htmlLink.attr("href");
                            final Document subjectDataPage = lstuRequests.get(
                                    LstuUrlBuilder.buildGetByLocalUrl(subjectLink));

                            final List<MessageData> messages = loadAllMessages(subjectLink);
                            return new SubjectData(
                                    htmlLink.text(),
                                    new HashSet<>(subjectDataPage.select("ul.list-inline > li").eachText()),
                                    getLastMessageDate(messages),
                                    findPrimaryAcademic(messages)
                            );
                        }))
                .filter(subjectData -> !subjectData.getDocumentNames().isEmpty())
                .collect(Collectors.toSet());
    }

    // TODO Добавить функционал с сообщениями
    //
    //+    Приложение определяет основного преподавателя как человека, отправившего наибольшее количество сообщений.
    //    Если неверно определило, то
    //      предложить двух первых людей по частоте сообщений после преподавателя
    //      или самостоятельный добавление
    //    Если преподавателей несколько, то
    //      предложить четырех первых людей по частоте сообщений после преподавателя с выбором нескольких
    //      или самостоятельное добавление

    // TODO в последующие разы должен загружать пока не наткнется на прошлое последнее сообщение
    private List<MessageData> loadAllMessages (String subjectLink) {
        final String[] pathSegments = subjectLink.split("/");
        final List<MessageData> messageDataList = new ArrayList<>();
        Document pageWithMessages;
        Date lastMessageDate = null;
        do {
            pageWithMessages = lstuRequests.post(
                    LstuUrlBuilder.buildGetNextMessages(
                            pathSegments[5], pathSegments[4], pathSegments[3],
                            lastMessageDate));
            List<MessageData> messagesDataChunk = parseMessagesDataChunk(pageWithMessages);
            if (messagesDataChunk.isEmpty())
                break;
            messageDataList.addAll(messagesDataChunk);
            lastMessageDate = getLastMessageDate(messagesDataChunk);

        } while (pageWithMessages.select(".stop-scroll").first() == null);
        return messageDataList;
    }

    private Date getLastMessageDate (List<MessageData> messagesDataChunk) {
        if (!messagesDataChunk.isEmpty()) {
            return messagesDataChunk.get(messagesDataChunk.size() - 1).getDate();
        } else {
            return new Date();
        }
    }

    // TODO test
    private String findPrimaryAcademic(List<MessageData> messages) {
        if (messages.isEmpty())
            return UNKNOWN_ACADEMIC_NAME;
        else
            return messages.stream()
                    .collect(Collectors.groupingBy(
                            MessageData::getSender,
                            Collectors.counting())
                    )
                    .entrySet().stream().max(Map.Entry.comparingByValue())
                    .get().getKey();
    }

    // TODO
    private Set<String> findSecondaryAcademics(Elements messages) {
        return null;
    }

    private List<MessageData> parseMessagesDataChunk(Document pageWithMessages) {
        final Iterator<String> comments = pageWithMessages
                .select("div.comment__body > .row")
                .eachText().iterator();

        final Iterator<String> senders = pageWithMessages
                .select("div.comment__body > p > strong")
                .eachText().iterator();

        final Iterator<Date> dates = pageWithMessages
                .select("div.comment__block").stream()
                .map(htmlCommentBlock -> htmlCommentBlock.attr("data-msg"))
                .map(htmlDate -> {
                    try {
                        return new SimpleDateFormat("dd.MM.yyyy HH:mm")
                                .parse(htmlDate);
                    } catch (ParseException e) {
                        return new Date(System.currentTimeMillis());
                    }
                }).collect(Collectors.toList())
                .iterator();

        List<MessageData> messageDataList = new ArrayList<>();
        while (comments.hasNext()) {
            try {
                messageDataList.add(new MessageData(comments.next(), senders.next(), dates.next()));
            } catch (NoSuchElementException ignored) {
                break;
            }
        }
        return messageDataList;
    }

    private String getSemesterLink(String semesterName) {
        Document semestersListPage = lstuRequests.get(
                LstuUrlBuilder.buildGetSemestersUrl());
        final Elements htmlSemestersLinks = semestersListPage.select(".ul-main > li > a");
        for (Element link : htmlSemestersLinks) {
            if (link.text().equals(semesterName)) {
                return link.attr("href");
            }
        }
        return null;
    }

    private Set<String> getSemesterDocumentNamesAndLastMessage (String semesterLink) {
        final Document subjectDataPage = lstuRequests.get(LstuUrlBuilder.buildGetByLocalUrl(semesterLink));
        return new HashSet<>(subjectDataPage.select("ul.list-inline > li").eachText());
    }
}
