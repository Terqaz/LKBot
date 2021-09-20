package com.my.services.lstu;

import com.my.ParserUtils;
import com.my.models.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LstuParser {

    private static LstuParser instance = null;

    public static LstuParser getInstance () {
        if (instance == null)
            instance = new LstuParser();
        return instance;
    }

    private LstuParser () {}

    private static final LstuClient lstuClient = LstuClient.getInstance();
    private static final Pattern groupNamePattern =
            Pattern.compile("^((т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,5}-)(п-)?\\d{2}(-\\d)?$");

    // Загружает все документы и все сообщения
    public List<Subject> getSubjectsFirstTime (String semesterName) {
        final List<Subject> subjects = getHtmlSubjectsUrls(semesterName)
                .map(htmlSubjectUrl -> getSubjectByHtmlUrl(htmlSubjectUrl, new Date(1)))
                .sorted(Comparator.comparing(Subject::getName))
                .collect(Collectors.toList());
        return addIds(subjects);
    }

    private Stream<Element> getHtmlSubjectsUrls(String semesterName) {
        return Stream.of(lstuClient.get(
                LstuUrlBuilder.buildByLocalUrl(
                        getSemesterUrl(semesterName))))
                .flatMap(semesterDataPage -> semesterDataPage.select("li.submenu.level3 > a").stream());
    }

    private String getSemesterUrl(String semesterName) {
        Document semestersListPage = lstuClient.get(LstuUrlBuilder.buildSemestersUrl());
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
        return getNewSubject(ParserUtils.capitalize(htmlSubjectUrl.text()), localUrl, lastCheckDate);
    }

    private Subject getNewSubject(String subjectName, String subjectLocalUrl, Date lastCheckDate) {

        final Document subjectPage = lstuClient.get(LstuUrlBuilder.buildByLocalUrl(subjectLocalUrl));

        final String[] pathSegments = subjectLocalUrl.split("/");
        final String semesterId = pathSegments[3];
        final String subjectId = pathSegments[4];
        final String groupId = pathSegments[5];

        return new Subject(
                subjectId,
                subjectName,
                getDocumentNamesFromSubjectPage(subjectPage),
                loadMessagesAfterDate(semesterId, subjectId, groupId, lastCheckDate)
        );
    }

    private List<Subject> addIds (List<Subject> subjects) {
        var id = 1;
        for (Subject subject : subjects)
            subject.setId(id++);
        return subjects;
    }

    // Загружает все документы и только новые сообщения
    public Subject getNewSubject(Subject subject, Group group) {
        final Document subjectPage = getSubjectPage(subject.getLkId(), group);

        return new Subject(
                subject.getLkId(),
                subject.getName(),
                getDocumentNamesFromSubjectPage(subjectPage),
                loadMessagesAfterDate(
                        group.getLkSemesterId(), subject.getLkId(), group.getLkId(), group.getLastCheckDate())
        );
    }

    private Set<String> getDocumentNamesFromSubjectPage(Document subjectPage) {
        return new HashSet<>(subjectPage.select("ul.list-inline > li").eachText());
    }

    public Set<String> getSubjectDocumentNames(String subjectLkId, Group group) {
        return getDocumentNamesFromSubjectPage(getSubjectPage(subjectLkId, group));
    }

    private static Document getSubjectPage(String subjectLkId, Group group) {
        String subjectLocalUrl = LstuUrlBuilder.buildSubjectLocalUrl(
                group.getLkSemesterId(), subjectLkId, group.getLkId(), group.getLkContingentId());

        return lstuClient.get(LstuUrlBuilder.buildByLocalUrl(subjectLocalUrl));
    }

    private List<MessageData> loadMessagesAfterDate (String semesterId, String subjectId,
                                                     String groupId, Date lastCheckDate) {
        final List<MessageData> messageDataList = new ArrayList<>();

        Document pageWithMessages;
        List<MessageData> messagesDataChunk;
        Date lastMessageDate = null;
        do {
            pageWithMessages = lstuClient.post(
                    LstuUrlBuilder.buildNextMessagesUrl(semesterId, subjectId, groupId, lastMessageDate));

            messagesDataChunk = parseMessagesDataChunk(pageWithMessages, lastCheckDate);
            if (messagesDataChunk.isEmpty())
                break;
            messageDataList.addAll(messagesDataChunk);

            lastMessageDate = getLastMessageDate(messagesDataChunk);

        } while (pageWithMessages.select(".stop-scroll").first() == null);
        return messageDataList;
    }

    private List<MessageData> parseMessagesDataChunk(Document pageWithMessages, Date lastCheckDate) {
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
                    } catch (java.text.ParseException e) {
                        return new Date(System.currentTimeMillis());
                    }
                }).collect(Collectors.toList())
                .iterator();

        List<MessageData> messageDataList = new ArrayList<>();
        while (comments.hasNext()) {
            final Date date = dates.next();
            if (!date.after(lastCheckDate))
                break;
            try {
                messageDataList.add(new MessageData(
                        comments.next(),
                        ParserUtils.makeShortSenderName(senders.next()),
                        date));
            } catch (NoSuchElementException ignored) {
                break;
            }
        }
        return messageDataList;
    }

    private Date getLastMessageDate (List<MessageData> messagesDataChunk) {
        if (!messagesDataChunk.isEmpty()) {
            return messagesDataChunk.get(messagesDataChunk.size() - 1).getDate();
        } else {
            return new Date();
        }
    }

    public Optional<String> getGroupName () {
        return lstuClient.get(
                LstuUrlBuilder.buildByLocalUrl("/personal")
        ).select(".col-xs-12 > p").textNodes().stream()
                .map(TextNode::text)
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .filter(s -> groupNamePattern.matcher(s).find())
                .findFirst();
    }

    public Timetable parseTimetable (String semesterId, String groupId) {
        return Stream.of(lstuClient.get(
                LstuUrlBuilder.buildStudentScheduleUrl(semesterId, groupId)))
                .map(schedulePage -> {
                    final Elements rows = schedulePage.select("#schedule_lections tbody tr");

                    final Timetable timetable = new Timetable();

                    Integer thisWeekDay = 0;
                    for (Element row : rows) {
                        Elements cells = row.select("td");

                        final String newWeekDay = cells.get(0).text();
                        if (!newWeekDay.isBlank())
                            thisWeekDay = mapDayOfWeek(newWeekDay);

                        String interval = cells.get(1).text().strip();
                        interval = interval.substring(0, 5) + "-" + interval.substring(8, 13);

                        if (!cells.get(2).text().isBlank())
                            timetable.addWhiteWeekSubject(
                                    thisWeekDay, buildTimetableSubject(cells.get(2), cells.get(3), interval));

                        if (!cells.get(4).text().isBlank())
                            timetable.addGreenWeekDaySubject(
                                    thisWeekDay, buildTimetableSubject(cells.get(4), cells.get(5), interval));
                    }
                    return timetable;
                }).findFirst().orElse(null);
    }

    private TimetableSubject buildTimetableSubject (Element firstCell, Element secondCell, String interval) {
        String subjectName = ParserUtils.capitalize(firstCell.textNodes().get(0).text().strip());

        String academicName = firstCell.select("a").text();

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

    public static boolean parseWeekType(String semesterId) {
        return lstuClient.get(LstuUrlBuilder.buildSemesterUrl(semesterId))
                .select(".wl_content .mtop-15").text()
                .toLowerCase(Locale.ROOT).contains("белая");
    }
}