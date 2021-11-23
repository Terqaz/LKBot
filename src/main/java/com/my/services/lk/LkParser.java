package com.my.services.lk;

import com.my.TextUtils;
import com.my.Utils;
import com.my.exceptions.LkNotRespondingException;
import com.my.models.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LkParser {

    private final LkClient lkClient;

    public LkParser() {
        lkClient = new LkClient();
    }

    private static final Pattern groupNamePattern =
            Pattern.compile("^((т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,5}-)(п-)?\\d{2}(-\\d)?$");

    public void login (AuthenticationData data) {
        lkClient.login(data);
    }

    public void logout () {
        lkClient.logout();
    }

    // Загружает все документы и все сообщения
    public List<Subject> getSubjectsFirstTime (String semesterName) {
        lkClient.keepAuth();
        final List<Subject> subjects = getHtmlSubjectsUrls(semesterName)
                .map(this::getSubjectByHtmlUrl)
                .map(Utils::setIdsWhereNull)
                .sorted(Comparator.comparing(Subject::getName))
                .collect(Collectors.toList());
        return addIds(subjects);
    }

    private Stream<Element> getHtmlSubjectsUrls(String semesterName) {
        return Stream.of(lkClient.loggedGet(
                LkUrlBuilder.buildByLocalUrl(
                        getSemesterUrl(semesterName))))
                .flatMap(semesterDataPage -> semesterDataPage.select("li.submenu.level3 > a").stream());
    }

    private String getSemesterUrl(String semesterName) {
        Document semestersListPage = lkClient.loggedGet(LkUrlBuilder.buildSemestersUrl());
        final Elements htmlSemestersUrls = semestersListPage.select(".ul-main > li > a");
        for (Element htmlUrl : htmlSemestersUrls) {
            if (htmlUrl.text().equals(semesterName)) {
                return htmlUrl.attr("href");
            }
        }
        return null;
    }

    // Загружает все документы и только новые сообщения
    public List<Subject> getNewSubjects (List<Subject> oldSubjects, Group group) {
        return addIds(oldSubjects.stream()
                .map(subjects1 ->
                        getNewSubject(subjects1, group))
                .sorted(Comparator.comparing(Subject::getName))
                .collect(Collectors.toList()));
    }

    private Subject getSubjectByHtmlUrl (Element htmlSubjectUrl) {
        final String localUrl = htmlSubjectUrl.attr("href");
        return getNewSubject(TextUtils.capitalize(htmlSubjectUrl.text().strip()), localUrl);
    }

    private Subject getNewSubject(String subjectName, String subjectLocalUrl) {

        final Document subjectPage = lkClient.loggedGet(LkUrlBuilder.buildByLocalUrl(subjectLocalUrl));

        final String[] pathSegments = subjectLocalUrl.split("/");
        final String semesterId = pathSegments[3];
        final String subjectId = pathSegments[4];
        final String groupId = pathSegments[5];

        LocalDateTime earliestDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(1), ZoneId.systemDefault());
        final List<LkMessage> newMessages =
                loadMessagesAfterDate(semesterId, subjectId, groupId, earliestDate, null);

        return createNewSubject(subjectId, subjectName, subjectPage, newMessages);
    }

    private Set<LkDocument> getMessagesDocuments(List<LkMessage> messagesAfterDate) {
        return messagesAfterDate.stream()
                .map(LkMessage::getDocument)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<Subject> addIds (List<Subject> subjects) {
        var id = 1;
        for (Subject subject : subjects)
            subject.setId(id++);
        return subjects;
    }

    // Загружает все документы из секции материалов и только новые сообщения
    public Subject getNewSubject(Subject subject, Group group) {
        String subjectUrl = LkUrlBuilder.buildSubjectStringUrl(
                group.getLkSemesterId(), subject.getLkId(), group.getLkId(), group.getLkContingentId());

        final Document subjectPage = lkClient.loggedGet(subjectUrl);
        lkClient.keepAuth();

        final List<LkMessage> newMessages =
                loadMessagesAfterDate(group.getLkSemesterId(), subject.getLkId(), group.getLkId(),
                                      subject.getLastMessageDate(), subject.getLastMessageHash());

        return createNewSubject(subject.getLkId(), subject.getName(), subjectPage, newMessages);
    }

    private Subject createNewSubject(String subjectId, String subjectName, Document subjectPage, List<LkMessage> newMessages) {
        LocalDateTime newFromDate = LocalDateTime.now();
        String lastMessageHash = null;
        if (!newMessages.isEmpty()) {
            final LkMessage lastLkMessage = newMessages.get(0);
            newFromDate = lastLkMessage.getDate();
            lastMessageHash = Utils.hashLkMessage(lastLkMessage);
        }

        return new Subject(subjectId, subjectName,
                parseLkDocumentsFromMaterials(subjectPage),
                getMessagesDocuments(newMessages),
                newMessages, newFromDate, lastMessageHash);
    }

    private Set<LkDocument> parseLkDocumentsFromMaterials(Document subjectPage) {
        return subjectPage.select("ul.list-inline > li > a").stream()
                .map(this::parseDocumentATag)
                .collect(Collectors.toSet());
    }

    private List<LkMessage> loadMessagesAfterDate (String semesterId, String subjectId, String groupId,
                                                   LocalDateTime fromDate, String lastMessageHash) {
        final List<LkMessage> lkMessageList = new ArrayList<>();

        Document pageWithMessages;
        List<LkMessage> messagesChunk;
        LocalDateTime lastMessageDate = null;
        do {
            pageWithMessages = lkClient.loggedPost(
                    LkUrlBuilder.buildNextMessagesUrl(semesterId, subjectId, groupId, lastMessageDate));

            messagesChunk = parseMessages(pageWithMessages, fromDate, lastMessageHash);
            if (messagesChunk.isEmpty())
                break;
            lkMessageList.addAll(messagesChunk);

            lastMessageDate = getLastMessageDate(messagesChunk);

        } while (pageWithMessages.select(".stop-scroll").first() == null);
        return lkMessageList;
    }

    private List<LkMessage> parseMessages(Document pageWithMessages, LocalDateTime fromDate, String lastMessageHash) {
        final Elements htmlMessages = pageWithMessages.getElementsByClass("comment__block");
        final List<LkMessage> lkMessageList = new ArrayList<>();
        String comment;
        String sender;
        LkDocument lkDocument;

        for (var htmlMessage : htmlMessages) {
            final LocalDateTime messageDate = parseMessageDate(htmlMessage);
            if (messageDate.isBefore(fromDate)) // если дата сообщения строго раньше последней
                break;

            comment = parseMessageComment(htmlMessage);
            lkDocument = parseMessageDocument(htmlMessage);
            if (comment == null && lkDocument == null)
                continue;

            sender = TextUtils.makeShortSenderName(parseMessageSender(htmlMessage));
            if (lkDocument != null)
                lkDocument.setSender(sender);

            final LkMessage message = new LkMessage(comment, sender, messageDate, lkDocument);
            if (Utils.hashLkMessage(message).equals(lastMessageHash))
                break;
            lkMessageList.add(message);
        }
        return lkMessageList;
    }

    private String parseMessageComment(Element htmlMessage) {
        return htmlMessage.select("div.comment__body > .row > div")
                .first().wholeText().strip();
    }

    private String parseMessageSender(Element htmlMessage) {
        return htmlMessage.select("p > strong")
                .first().text();
    }

    private LocalDateTime parseMessageDate(Element htmlMessage) {
        final String htmlDate = htmlMessage.attr("data-msg");
        return Utils.responseParseMessageDate(htmlDate);
    }

    private LkDocument parseMessageDocument(Element htmlMessage) {
        final var a = htmlMessage.select(".col-xs-3 > a").first();
        if (a == null) return null;
        return parseDocumentATag(a);
    }

    private LkDocument parseDocumentATag(Element a) {
        final String name = a.text();
        final String url = a.attr("href");

        final String[] strings = url.split("/");
        if (documentIsFromLk(strings)) {
            final String documentLkId = strings[strings.length - 1];
            return new LkDocument(name, documentLkId);

        } else
            return new LkDocument(name, DigestUtils.md5Hex(name+url).substring(0, 8), url);
    }

    public static final String LSTU_HOST = "lk.stu.lipetsk.ru";

    private boolean documentIsFromLk(String[] strings) {
        return strings[0].isBlank() ||
                strings.length >= 2 && strings[1].equals(LSTU_HOST) ||
                strings.length >= 3 && strings[2].equals(LSTU_HOST);
    }

    private LocalDateTime getLastMessageDate (List<LkMessage> messagesDataChunk) {
        if (!messagesDataChunk.isEmpty()) {
            return messagesDataChunk.get(messagesDataChunk.size() - 1).getDate();
        } else {
            return LocalDateTime.now();
        }
    }

    public Optional<String> getGroupName () {
        lkClient.keepAuth();
        return lkClient.loggedGet(
                LkUrlBuilder.buildByLocalUrl("/personal")
        ).select(".col-xs-12 > p").textNodes().stream()
                .map(TextNode::text)
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .filter(s -> groupNamePattern.matcher(s).find())
                .findFirst();
    }

    public Timetable parseTimetable (String semesterId, String groupId) {
        lkClient.keepAuth();
        return Stream.of(lkClient.loggedGet(
                LkUrlBuilder.buildStudentScheduleUrl(semesterId, groupId)))
                .map(schedulePage -> {
                    final Elements rows = schedulePage.select("#schedule_lections tbody tr");

                    final Timetable timetable = new Timetable();

                    Integer thisWeekDay = 0;
                    for (Element row : rows) {
                        Elements cells = row.select("td");

                        final String newWeekDay = cells.get(0).text().strip();
                        if (!newWeekDay.isBlank())
                            thisWeekDay = mapDayOfWeek(newWeekDay);

                        String interval = cells.get(1).text().strip();
                        interval = interval.substring(0, 5) + "-" + interval.substring(8, 13);

                        if (!cells.get(2).text().isBlank())
                            timetable.addWhiteSubject(
                                    thisWeekDay, buildTimetableSubject(cells.get(2), cells.get(3), interval));

                        if (!cells.get(4).text().isBlank())
                            timetable.addGreenSubject(
                                    thisWeekDay, buildTimetableSubject(cells.get(4), cells.get(5), interval));
                    }
                    return timetable;
                }).findFirst().orElse(null);
    }

    private TimetableSubject buildTimetableSubject (Element firstCell, Element secondCell, String interval) {
        String subjectName = TextUtils.capitalize(firstCell.textNodes().get(0).text().strip());

        String academicName = firstCell.select("a").text().strip();

        final List<TextNode> placeCellTextNodes = secondCell.textNodes();
        String place = placeCellTextNodes.get(0).text().strip() + ", " +
                mapCoupleType(placeCellTextNodes.get(1).text().strip());

        return new TimetableSubject(subjectName, academicName, interval, place);
    }

    private int mapDayOfWeek (String weekDay) {
        switch (weekDay) {
            case "ПН": return 0;
            case "ВТ": return 1;
            case "СР": return 2;
            case "ЧТ": return 3;
            case "ПТ": return 4;
            case "СБ": return 5;
            default: throw new IllegalArgumentException(
                    "Unknown day of week: " + weekDay);
        }
    }

    private String mapCoupleType (String couple) {
        switch (couple) {
            case "лек.": return "лекция";
            case "пр.":  return "практика";
            case "лаб.": return "лабораторная";
            default: throw new IllegalArgumentException(
                    "Unknown couple type: " + couple);
        }
    }

    // Changes group
    public boolean setSubjectsGeneralLkIds(Group group, String semesterName) {
        lkClient.keepAuth();

        final Optional<String> localUrl = getHtmlSubjectsUrls(semesterName).findFirst()
                .map(htmlSubjectUrl -> htmlSubjectUrl.attr("href"));

        if (localUrl.isPresent()) {
            String[] segments = localUrl.get().split("/");

            String segment = segments[3];
            if (segment != null) group.setLkSemesterId(segment);

            segment = segments[5];
            if (segment != null) group.setLkId(segment);

            segment = segments[6];
            if (segment != null) group.setLkContingentId(segment);
        }
        return localUrl.isPresent();
    }

    // Белая ли неделя
    public boolean parseWeekType(String semesterId) {
        lkClient.keepAuth();
        return lkClient.loggedGet(LkUrlBuilder.buildSemesterUrl(semesterId))
                .select(".wl_content .mtop-15").text()
                .toLowerCase(Locale.ROOT).contains("белая");
    }

    public Path loadMaterialsFile(LkDocument document) throws IOException {
        try {
            return lkClient.loadFileTo(createTempFileDir(),
                    LkUrlBuilder.buildMaterialsDocumentUrl(document));

        } catch (ConnectException | SocketTimeoutException e) {
            throw new LkNotRespondingException();
        }
    }


    public Path loadMessageFile(LkDocument document) throws IOException {
        try {
            return lkClient.loadFileTo(createTempFileDir(),
                    LkUrlBuilder.buildMessageDocumentUrl(document));

        } catch (ConnectException | SocketTimeoutException e) {
            throw new LkNotRespondingException();
        }
    }

    private static final Random random = new Random();

    private Path createTempFileDir() {
        return Paths.get("temp", String.valueOf(random.nextLong()));
    }
}
