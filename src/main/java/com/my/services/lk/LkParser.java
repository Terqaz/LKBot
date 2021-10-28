package com.my.services.lk;

import com.my.TextUtils;
import com.my.Utils;
import com.my.models.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
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

    public boolean isNotLoggedIn () {
        return lkClient.isSessionDiscarded();
    }

    // Загружает все документы и все сообщения
    public List<Subject> getSubjectsFirstTime (String semesterName) {
        lkClient.keepAuth();
        final List<Subject> subjects = getHtmlSubjectsUrls(semesterName)
                .map(htmlSubjectUrl -> getSubjectByHtmlUrl(htmlSubjectUrl, new Date(1)))
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

    private Subject getSubjectByHtmlUrl (Element htmlSubjectUrl, Date lastCheckDate) {
        final String localUrl = htmlSubjectUrl.attr("href");
        return getNewSubject(TextUtils.capitalize(htmlSubjectUrl.text()), localUrl, lastCheckDate);
    }

    private Subject getNewSubject(String subjectName, String subjectLocalUrl, Date lastCheckDate) {

        final Document subjectPage = lkClient.loggedGet(LkUrlBuilder.buildByLocalUrl(subjectLocalUrl));

        final String[] pathSegments = subjectLocalUrl.split("/");
        final String semesterId = pathSegments[3];
        final String subjectId = pathSegments[4];
        final String groupId = pathSegments[5];

        final List<LkMessage> messagesAfterDate = loadMessagesAfterDate(semesterId, subjectId, groupId, lastCheckDate);

        return new Subject(subjectId, subjectName,
                parseLkDocumentsFromMaterials(subjectPage),
                getMessagesDocuments(messagesAfterDate),
                messagesAfterDate);
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

        final var messagesAfterDate = loadMessagesAfterDate(
                group.getLkSemesterId(), subject.getLkId(), group.getLkId(), group.getLastCheckDate());

        return new Subject(subject.getLkId(), subject.getName(),
                parseLkDocumentsFromMaterials(subjectPage),
                getMessagesDocuments(messagesAfterDate),
                messagesAfterDate);
    }

    private Set<LkDocument> parseLkDocumentsFromMaterials(Document subjectPage) {
        return subjectPage.select("ul.list-inline > li > a").stream()
                .map(this::parseDocumentATag)
                .collect(Collectors.toSet());
    }

    private List<LkMessage> loadMessagesAfterDate (String semesterId, String subjectId,
                                                   String groupId, Date lastCheckDate) {
        final List<LkMessage> lkMessageList = new ArrayList<>();

        Document pageWithMessages;
        List<LkMessage> messagesChunk;
        Date lastMessageDate = null;
        do {
            pageWithMessages = lkClient.loggedPost(
                    LkUrlBuilder.buildNextMessagesUrl(semesterId, subjectId, groupId, lastMessageDate));

            messagesChunk = parseMessages(pageWithMessages, lastCheckDate);
            if (messagesChunk.isEmpty())
                break;
            lkMessageList.addAll(messagesChunk);

            lastMessageDate = getLastMessageDate(messagesChunk);

        } while (pageWithMessages.select(".stop-scroll").first() == null);
        return lkMessageList;
    }

    private List<LkMessage> parseMessages(Document pageWithMessages, Date lastCheckDate) {
        final Elements htmlMessages = pageWithMessages.getElementsByClass("comment__block");
        final List<LkMessage> lkMessageList = new ArrayList<>();
        String comment;
        String sender;
        LkDocument lkDocument;

        for (var htmlMessage : htmlMessages) {
            final var date = parseMessageDate(htmlMessage);
            if (!date.after(lastCheckDate))
                break;
            comment = parseMessageComment(htmlMessage);
            sender = TextUtils.makeShortSenderName(parseMessageSender(htmlMessage));
            lkDocument = parseMessageDocument(htmlMessage);
            lkMessageList.add(new LkMessage(comment, sender, date, lkDocument));
        }
        return lkMessageList;
    }

    private String parseMessageComment(Element htmlMessage) {
        return htmlMessage.select("div.comment__body > .row")
                .first().text();
    }

    private String parseMessageSender(Element htmlMessage) {
        return htmlMessage.select("p > strong")
                .first().text();
    }

    private Date parseMessageDate(Element htmlMessage) {
        final var htmlDate = htmlMessage.attr("data-msg");
        try {
            return new SimpleDateFormat("dd.MM.yyyy HH:mm")
                    .parse(htmlDate);
        } catch (java.text.ParseException e) {
            return new Date(System.currentTimeMillis());
        }
    }

    private LkDocument parseMessageDocument(Element htmlMessage) {
        final var a = htmlMessage.select(".col-xs-3 > a").first();
        if (a == null) return null;
        return parseDocumentATag(a);
    }

    private LkDocument parseDocumentATag(Element a) {
        final String[] strings = a.attr("href").split("/");
        final var documentLkId = strings[strings.length-1];
        final var name = a.text();
        return new LkDocument(name, documentLkId);
    }

    private Date getLastMessageDate (List<LkMessage> messagesDataChunk) {
        if (!messagesDataChunk.isEmpty()) {
            return messagesDataChunk.get(messagesDataChunk.size() - 1).getDate();
        } else {
            return new Date();
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

    public static final String SEMESTER_ID = "semesterId";
    public static final String GROUP_ID = "groupId";
    public static final String CONTINGENT_ID = "contingentId";

    public Map<String, String> getSubjectsGeneralLkIds (String semesterName) {
        lkClient.keepAuth();
        return getHtmlSubjectsUrls(semesterName).findFirst()
                .map(htmlSubjectUrl -> htmlSubjectUrl.attr("href"))
                .map(localUrl -> {
                    String[] segments = localUrl.split("/");
                    final HashMap<String, String> namedSegments = new HashMap<>();
                    namedSegments.put(SEMESTER_ID, segments[3]);
                    namedSegments.put(GROUP_ID, segments[5]);
                    namedSegments.put(CONTINGENT_ID, segments[6]);
                    return namedSegments;
                }).orElse(new HashMap<>());
    }

    // Белая ли неделя
    public boolean parseWeekType(String semesterId) {
        lkClient.keepAuth();
        return lkClient.loggedGet(LkUrlBuilder.buildSemesterUrl(semesterId))
                .select(".wl_content .mtop-15").text()
                .toLowerCase(Locale.ROOT).contains("белая");
    }

    public Path loadMaterialsFile(LkDocument document, String groupName, String subjectName) {
        final Path path = tryLoadFromLocal(document, groupName, subjectName);
        if (path != null)
            return path;
        else
            return lkClient.loadFileTo(makeFileDir(groupName, subjectName),
                LkUrlBuilder.buildMaterialsDocumentUrl(document));
    }

    public Path loadMessageFile(LkDocument document, String groupName, String subjectName) {
        final Path path = tryLoadFromLocal(document, groupName, subjectName);
        if (path != null)
            return path;
        else
            return lkClient.loadFileTo(makeFileDir(groupName, subjectName),
                LkUrlBuilder.buildMessageDocumentUrl(document));
    }

    private Path tryLoadFromLocal(LkDocument document, String groupName, String subjectName) {
        if (document.getFileName() == null)
            return null;

        var path = Paths.get(groupName, subjectName, document.getFileName());
        if (path.toFile().exists())
            return path;
        else return null;
    }

    private String makeFileDir(String groupName, String subjectName) {
        return ".\\" + groupName + "\\" + subjectName;
    }
}
