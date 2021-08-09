package com.my.services;

import com.my.LstuClient;
import com.my.LstuUrlBuilder;
import com.my.models.MessageData;
import com.my.models.SubjectData;
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

    private static final LstuClient lstuClient = LstuClient.getInstance();
    private static final Pattern groupNamePattern =
            Pattern.compile("^((т9?|ОЗ|ОЗМ|М)-)?([A-Я]{1,5}-)(п-)?\\d{2}(-\\d)?$");

    // Загружает все документы и все сообщения
    public List<SubjectData> getSubjectsDataFirstTime (String semesterName) {
        final List<SubjectData> subjectsData = getHtmlSubjectsLinks(semesterName)
                .map(htmlSubjectLink -> getSubjectDataByHtmlLink(htmlSubjectLink, new Date(1)))
                .sorted(Comparator.comparing(SubjectData::getName))
                .collect(Collectors.toList());
        return addIds(subjectsData);
    }

    private Stream<Element> getHtmlSubjectsLinks(String semesterName) {
        return Stream.of(getSemesterLink(semesterName))
                .map(semesterLink -> lstuClient.get(LstuUrlBuilder.buildByLocalUrl(semesterLink)))
                .flatMap(semesterDataPage -> semesterDataPage.select("li.submenu.level3 > a").stream());
    }

    private String getSemesterLink(String semesterName) {
        Document semestersListPage = lstuClient.get(LstuUrlBuilder.buildSemestersUrl());
        final Elements htmlSemestersLinks = semestersListPage.select(".ul-main > li > a");
        for (Element link : htmlSemestersLinks) {
            if (link.text().equals(semesterName)) {
                return link.attr("href");
            }
        }
        return null;
    }

    // Загружает все документы и только новые сообщения
    public List<SubjectData> getNewSubjectsData (List<SubjectData> oldSubjectsData, Date lastCheckDate) {
        final List<SubjectData> subjectsData = oldSubjectsData.stream()
                .map(subjectData ->
                        getNewSubjectData(subjectData.getName(), subjectData.getLocalUrl(), lastCheckDate))
                .sorted(Comparator.comparing(SubjectData::getName))
                .collect(Collectors.toList());
        return addIds(subjectsData);
    }

    private List<SubjectData> addIds (List<SubjectData> subjectsData) {
        var id = 1;
        for (SubjectData subjectData : subjectsData) {
            subjectData.setId(id++);
        }
        return subjectsData;
    }

    private SubjectData getSubjectDataByHtmlLink (Element htmlSubjectLink, Date lastCheckDate) {
        return getNewSubjectData(htmlSubjectLink.text(), htmlSubjectLink.attr("href"), lastCheckDate);
    }

    // Загружает все документы и только новые сообщения
    public SubjectData getNewSubjectData(String subjectName, String localUrl, Date lastCheckDate) {
        final Document subjectDataPage = lstuClient.get(
                LstuUrlBuilder.buildByLocalUrl(localUrl));

        final List<MessageData> messages = loadMessagesAfterDate(localUrl, lastCheckDate);

        return new SubjectData(
                subjectName,
                localUrl,
                new HashSet<>(subjectDataPage.select("ul.list-inline > li").eachText()),
                messages
        );
    }

    private List<MessageData> loadMessagesAfterDate (String subjectLink, Date lastCheckDate) {
        final String[] pathSegments = subjectLink.split("/");
        final List<MessageData> messageDataList = new ArrayList<>();

        Document pageWithMessages;
        List<MessageData> messagesDataChunk;
        Date lastMessageDate = null;
        do {
            pageWithMessages = lstuClient.post(
                    LstuUrlBuilder.buildNextMessagesUrl(
                            pathSegments[5], pathSegments[4], pathSegments[3],
                            lastMessageDate));

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
                messageDataList.add(new MessageData(comments.next(), makeShortSenderName(senders.next()), date));
            } catch (NoSuchElementException ignored) {
                break;
            }
        }
        return messageDataList;
    }

    private static String makeShortSenderName (String name) {
        final String[] chunks = name.split(" ");
        return chunks[0] + " " + chunks[1].charAt(0) + chunks[2].charAt(0);
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
}
