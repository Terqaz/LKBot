package com.my.services;

import com.my.LstuClient;
import com.my.LstuUrlBuilder;
import com.my.models.MessageData;
import com.my.models.SubjectData;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NewInfoService {

    private static final LstuClient lstuClient = LstuClient.getInstance();

    public List<SubjectData> getSubjectsDataFirstTime (String semesterName) {
        final List<SubjectData> subjectsData = getHtmlSubjectsLinks(semesterName)
                .map(htmlSubjectLink -> getSubjectDataByHtmlLink(htmlSubjectLink, new Date(1)))
                .filter(SubjectData::isNotEmpty)
                .sorted(Comparator.comparing(SubjectData::getSubjectName))
                .collect(Collectors.toList());
        return addIds(subjectsData);
    }

    private Stream<Element> getHtmlSubjectsLinks(String semesterName) {
        return Stream.of(getSemesterLink(semesterName))
                .map(semesterLink -> lstuClient.get(LstuUrlBuilder.buildByLocalUrl(semesterLink)))
                .flatMap(semesterDataPage -> semesterDataPage.select("li.submenu.level3 > a").stream());
    }

    public List<SubjectData> getNewSubjectsData (List<SubjectData> oldSubjectsData, Date lastCheckDate) {
        final List<SubjectData> subjectsData = oldSubjectsData.stream()
                .map(subjectData ->
                        getNewSubjectData(subjectData.getSubjectName(), subjectData.getLocalUrl(), lastCheckDate))
                .sorted(Comparator.comparing(SubjectData::getSubjectName))
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
                    } catch (ParseException e) {
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
                messageDataList.add(new MessageData(comments.next(), senders.next(), date));
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

    public static List<SubjectData> removeOldSubjectsDocuments (
            List<SubjectData> oldSubjectsData, List<SubjectData> newSubjectsData) {

        Map<String, SubjectData> oldDocumentsMap = new HashMap<>();
        for (SubjectData data : oldSubjectsData) {
            oldDocumentsMap.put(data.getSubjectName(), data);
        }

        Set<SubjectData> oldSubjectsDataSet = new HashSet<>(oldSubjectsData);
        return newSubjectsData.stream()
                .peek(subjectData -> {
                    final String subjectName = subjectData.getSubjectName();
                    final Set<String> documents = subjectData.getDocumentNames();
                    if (oldSubjectsDataSet.contains(subjectData))
                        documents.removeAll(oldDocumentsMap.get(subjectName).getDocumentNames());
                })
                .filter(SubjectData::isNotEmpty)
                .collect(Collectors.toList());
    }
}
