package com.my.services;

import com.my.ParserUtils;
import com.my.models.Group;
import com.my.models.MessageData;
import com.my.models.SubjectData;
import com.my.models.TimetableSubject;
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
    public List<SubjectData> getSubjectsDataFirstTime (String semesterName) {
        final List<SubjectData> subjectsData = getHtmlSubjectsUrls(semesterName)
                .map(htmlSubjectUrl -> getSubjectDataByHtmlUrl(htmlSubjectUrl, new Date(1)))
                .sorted(Comparator.comparing(SubjectData::getName))
                .collect(Collectors.toList());
        return addIds(subjectsData);
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
    public List<SubjectData> getNewSubjectsData (List<SubjectData> oldSubjectsData, Date lastCheckDate) {
        final List<SubjectData> subjectsData = oldSubjectsData.stream()
                .map(subjectData ->
                        getNewSubjectData(subjectData.getName(), subjectData.getLkId(), lastCheckDate))
                .sorted(Comparator.comparing(SubjectData::getName))
                .collect(Collectors.toList());
        return addIds(subjectsData);
    }

    private SubjectData getSubjectDataByHtmlUrl (Element htmlSubjectUrl, Date lastCheckDate) {
        final String localUrl = htmlSubjectUrl.attr("href");
        return getNewSubjectData(ParserUtils.capitalize(htmlSubjectUrl.text()), localUrl, lastCheckDate);
    }

    private SubjectData getNewSubjectData(String subjectName, String subjectLocalUrl, Date lastCheckDate) {

        final Document subjectDataPage = lstuClient.get(LstuUrlBuilder.buildByLocalUrl(subjectLocalUrl));

        final String[] pathSegments = subjectLocalUrl.split("/");
        final String semesterId = pathSegments[3];
        final String subjectId = pathSegments[4];
        final String groupId = pathSegments[5];

        return new SubjectData(
                subjectId,
                subjectName,
                new HashSet<>(subjectDataPage.select("ul.list-inline > li").eachText()),
                loadMessagesAfterDate(semesterId, subjectId, groupId, lastCheckDate)
        );
    }

    private List<SubjectData> addIds (List<SubjectData> subjectsData) {
        var id = 1;
        for (SubjectData subjectData : subjectsData) {
            subjectData.setId(id++);
        }
        return subjectsData;
    }

    // Загружает все документы и только новые сообщения
    public SubjectData getNewSubjectData(SubjectData oldSubjectData, Group group) {

        String subjectLocalUrl = LstuUrlBuilder.buildSubjectLocalUrl(
                group.getLkSemesterId(), oldSubjectData.getLkId(), group.getLkId(), group.getLkUnknownId());

        final Document subjectDataPage = lstuClient.get(LstuUrlBuilder.buildByLocalUrl(subjectLocalUrl));

        return new SubjectData(
                oldSubjectData.getLkId(),
                oldSubjectData.getName(),
                new HashSet<>(subjectDataPage.select("ul.list-inline > li").eachText()),
                loadMessagesAfterDate(
                        group.getLkSemesterId(), oldSubjectData.getLkId(), group.getLkId(), group.getLastCheckDate())
        );
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

    // Белая ли неделя; номер дня недели
    public Map<Boolean, Map<Integer, List<TimetableSubject>>> parseTimetable (String semesterId, String groupId) {
        return Stream.of(lstuClient.get(
                LstuUrlBuilder.buildStudentScheduleUrl(semesterId, groupId)))
                .map(schedulePage -> {
                    final Elements rows = schedulePage.select("#schedule_lections tbody tr");
                    int dayOfWeek;

                    Map<Integer, List<TimetableSubject>> subjectsByDayOfWeek = new HashMap<>();

                    List<TimetableSubject> whiteSubjects;
                    List<TimetableSubject> greenSubjects;
                    Elements cells;

                    for (Element row : rows) {
                        whiteSubjects = new ArrayList<>();
                        greenSubjects = new ArrayList<>();
                        cells = row.select("td");

                        final String newWeekDay = cells.get(0).text();
                        if (!newWeekDay.isBlank())
                            dayOfWeek = mapDayOfWeek(newWeekDay);

                        String interval = cells.get(1).text().strip();
                        interval = interval.substring(0, 5) + "-" + interval.substring(8, 13);

                        if (!cells.get(2).text().isBlank())
                            whiteSubjects.add(initTimetableSubject(cells.get(2), cells.get(3))
                                        .setInterval(interval));
                        if (!cells.get(4).text().isBlank())
                            greenSubjects.add(initTimetableSubject(cells.get(4), cells.get(5))
                                    .setInterval(interval));

                    }
                })
    }

    private TimetableSubject initTimetableSubject (Element firstCell, Element secondCell) {
        String academicName = firstCell.select("a").text();

        String subjectName = ParserUtils.capitalize(firstCell.textNodes().get(0).text().strip());

        final List<TextNode> placeCellTextNodes = secondCell.textNodes();

        String place = placeCellTextNodes.get(0).text().strip() + ", " +
                mapСoupleType(placeCellTextNodes.get(1).text().strip());

        return new TimetableSubject(subjectName, academicName, place);
    }

    private Integer mapDayOfWeek (String weekDay) {
        switch (weekDay) {
            case "ПН": return 1;
            case "ВТ": return 2;
            case "СР": return 3;
            case "ЧТ": return 4;
            case "ПТ": return 5;
            case "СБ": return 6;
        }
    }

    private String mapСoupleType (String strip) {

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
}
