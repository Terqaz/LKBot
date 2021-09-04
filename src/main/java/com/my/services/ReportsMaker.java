package com.my.services;

import com.mongodb.lang.Nullable;
import com.my.Utils;
import com.my.models.MessageData;
import com.my.models.SubjectData;
import com.my.models.TimetableSubject;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ReportsMaker {

    private ReportsMaker () {}

    public static String getSubjectsNames (List<SubjectData> subjectsData) {
        final var sb = new StringBuilder();
        for (SubjectData data : subjectsData) {
            sb.append("➡ ").append(data.getId()).append(" ")
                    .append(data.getName()).append("\n");
        }
        return sb.toString();
    }

    public static String getSubjectsData (List<SubjectData> subjectsData, @Nullable Date nextCheckDate) {
        if (subjectsData.isEmpty()) {
            return "Нет новой информации по предметам";
        }
        final var sb = new StringBuilder();
        var partBuilder = new StringBuilder();
        for (SubjectData data : subjectsData) {
            if (!data.getDocumentNames().isEmpty()) {
                partBuilder.append("➡ ").append(data.getId()).append(" ").append(data.getName()).append(": ")
                        .append(String.join(", ", data.getDocumentNames()))
                        .append("\n\n");
            }
        }
        if (partBuilder.length() > 0) {
            sb.append("\uD83D\uDD34 Новые документы:\n").append(partBuilder);
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
            sb.append("\uD83D\uDD34 Новые сообщения:\n").append(partBuilder);

        if (nextCheckDate != null)
            sb.append(getNextUpdateDateText(nextCheckDate));

        return sb.toString();
    }


    public static String getNextUpdateDateText (Date nextCheckDate) {
        return "Следующее глобальное обновление будет " + Utils.formatDate(nextCheckDate);
    }

    public static String getDaySchedule (List<TimetableSubject> subjects, String weekTypeInfo) {
        final var sb = new StringBuilder();

        sb.append("Сегодня ").append(weekTypeInfo).append('\n'); // Пример: Сегодня белая неделя

        return sb.append(subjects.stream()
                .map(subject -> "➡ " + subject.getInterval() + ' ' +
                        subject.getName() + '\n' +
                        subject.getPlace() + '\n' +
                        subject.getAcademicName())
                .collect(Collectors.joining("\n\n")))
                .toString();

    }

}
