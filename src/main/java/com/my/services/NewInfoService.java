package com.my.services;

import com.my.LstuClient;
import com.my.LstuUrlBuilder;
import com.my.MessageData;
import com.my.SubjectData;
import org.apache.http.auth.AuthenticationException;
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

    private static final String UNKNOWN_ACADEMIC_NAME = "*УТОЧНИТЕ ИМЯ*";
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";


    public Set<SubjectData> getInfoFirstTime (String semesterName) throws AuthenticationException {
        return getInfo(semesterName, new Date(1));
    }

    // Получить все выложенные преподавателями документы и последние сообщения
    public Set<SubjectData> getNewInfo (String semesterName, Date lastCheckDate) throws AuthenticationException {
        return getInfo(semesterName, lastCheckDate);
    }

    public static Set<SubjectData> removeOldDocuments (Set<SubjectData> oldSubjectsData, Set<SubjectData> newSubjectsData) {
        Map<String, SubjectData> oldDocumentsMap = new HashMap<>();
        for (SubjectData data : oldSubjectsData) {
            oldDocumentsMap.put(data.getSubjectName(), data);
        }
        return newSubjectsData.stream()
                .peek(subjectData -> {
                    final String subjectName = subjectData.getSubjectName();
                    final Set<String> documents = subjectData.getDocumentNames();
                    if (oldSubjectsData.contains(subjectData))
                        documents.removeAll(oldDocumentsMap.get(subjectName).getDocumentNames());
                })
                .filter(subjectData -> !subjectData.getDocumentNames().isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<SubjectData> getInfo (String semesterName, Date lastCheckDate) throws AuthenticationException {
        if (lstuClient.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        return Stream.of(getSemesterLink(semesterName))
                .map(semesterLink -> lstuClient.get(LstuUrlBuilder.buildByLocalUrl(semesterLink)))
                .flatMap(semesterDataPage -> semesterDataPage.select("li.submenu.level3 > a").stream()
                        .map(htmlSubjectLink -> {
                            String subjectLink = htmlSubjectLink.attr("href");
                            final Document subjectDataPage = lstuClient.get(
                                    LstuUrlBuilder.buildByLocalUrl(subjectLink));

                            final List<MessageData> messages =
                                    loadMessagesAfterDate(subjectLink, lastCheckDate);

                            return new SubjectData(
                                    htmlSubjectLink.text(),
                                    new HashSet<>(subjectDataPage.select("ul.list-inline > li").eachText()),
                                    messages
                            );
                        }))
                .filter(subjectData -> !subjectData.getDocumentNames().isEmpty())
                .collect(Collectors.toSet());
    }

    private String getSemesterLink(String semesterName) {
        Document semestersListPage = lstuClient.get(
                LstuUrlBuilder.buildSemestersUrl());
        final Elements htmlSemestersLinks = semestersListPage.select(".ul-main > li > a");
        for (Element link : htmlSemestersLinks) {
            if (link.text().equals(semesterName)) {
                return link.attr("href");
            }
        }
        return null;
    }

    // TODO в последующие разы должен загружать пока не наткнется на прошлое последнее сообщение
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

    // TODO Добавить функционал с сообщениями
    //
    //+   Приложение определяет основного преподавателя как человека, отправившего наибольшее количество сообщений.
    //    Если неверно определило, то
    //      предложить трех первых людей по частоте сообщений после преподавателя
    //      или самостоятельный добавление
    //    Добавить дополнительных преподавателей
    public static String findPrimaryAcademic(List<MessageData> messages) {
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
}
