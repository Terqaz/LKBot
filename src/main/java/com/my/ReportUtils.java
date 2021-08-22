package com.my;

import com.mongodb.lang.Nullable;
import com.my.models.MessageData;
import com.my.models.SubjectData;

import java.util.Date;
import java.util.List;

public class ReportUtils {

    private ReportUtils () {}

    public static String getSubjectsNames (List<SubjectData> subjectsData) {
        final var stringBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            stringBuilder.append("➡ ").append(data.getId()).append(" ")
                    .append(data.getName()).append("\n");
        }
        return stringBuilder.toString();
    }

    public static String getSubjectsData (List<SubjectData> subjectsData, @Nullable Date nextCheckDate) {
        if (subjectsData.isEmpty()) {
            return "Нет новой информации по предметам";
        }
        final var builder = new StringBuilder();
        var partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            if (!data.getDocumentNames().isEmpty()) {
                partBuilder.append("➡ ").append(data.getId()).append(" ").append(data.getName()).append(": ")
                        .append(String.join(", ", data.getDocumentNames()))
                        .append("\n\n");
            }
        }
        if (partBuilder.length() > 0) {
            builder.append("\uD83D\uDD34 Новые документы:\n").append(partBuilder);
        }
        partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            final List<MessageData> messagesData = data.getMessagesData();
            if (!messagesData.isEmpty()) {
                var messagesBuilder = new StringBuilder();
                for (MessageData messageData : messagesData) {
                    final String shortName = messageData.getSender();
                    messagesBuilder.append("☑ ").append(shortName)
                            .append(" в ")
                            .append(Utils.formatDate(messageData.getDate()))
                            .append(":\n")
                            .append(messageData.getComment())
                            .append("\n\n");
                }
                partBuilder.append("➡ ").append(data.getId()).append(" ").append(data.getName()).append(":\n")
                        .append(messagesBuilder);
            }
        }
        if (partBuilder.length() > 0)
            builder.append("\uD83D\uDD34 Новые сообщения:\n").append(partBuilder);

        if (nextCheckDate != null)
            builder.append(getNextUpdateDateText(nextCheckDate));

        return builder.toString();
    }


    public static String getNextUpdateDateText (Date nextCheckDate) {
        return "Следующее глобальное обновление будет " + Utils.formatDate(nextCheckDate);
    }
}
