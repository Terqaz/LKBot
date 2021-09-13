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
        if (subjectsData.isEmpty())
            return "Нет новой информации по предметам\n" + getNextUpdateDateText(nextCheckDate);

        final var sb = new StringBuilder();

        String partBuilder = subjectsData.stream()
                .filter(data -> !data.getDocumentNames().isEmpty())
                .map(data -> "➡ " + data.getId() + " " + data.getName() + ": " +
                        String.join(", ", data.getDocumentNames())
                ).collect(Collectors.joining("\n"));

        if (partBuilder.length() > 0)
            sb.append("\uD83D\uDD34 Новые документы:\n" + partBuilder);

        partBuilder = subjectsData.stream()
                .filter(data -> !data.getMessagesData().isEmpty())
                .map(data -> "➡ " + data.getId() + " " + data.getName() + ":\n" +
                        getSubjectMessages(data.getMessagesData())
                ).collect(Collectors.joining("\n\n"));

        if (partBuilder.length() > 0)
            sb.append("\n\n\uD83D\uDD34 Новые сообщения:\n").append(partBuilder);

        if (nextCheckDate != null)
            sb.append("\n\n").append(getNextUpdateDateText(nextCheckDate));

        return sb.toString();
    }

    private static String getSubjectMessages(List<MessageData> messages) {
        return messages.stream()
                .map(messageData ->
                        "☑ " + messageData.getSender() + " в " +
                                Utils.formatDate(messageData.getDate()) + ":\n" +
                                messageData.getComment()
                ).collect(Collectors.joining("\n\n"));
    }

    public static String getNextUpdateDateText (Date nextCheckDate) {
        return "Следующее глобальное обновление будет " + Utils.formatDate(nextCheckDate);
    }

    public static String getDaySchedule (List<TimetableSubject> subjects, boolean isWhiteWeek) {
        return "Сегодня " +
                (isWhiteWeek ? "белая неделя" : "зеленая неделя") +
                '\n' +
                subjects.stream()
                        .map(subject -> "➡ " + subject.getInterval() + ' ' +
                                subject.getName() + '\n' +
                                subject.getPlace() + '\n' +
                                subject.getAcademicName())
                        .collect(Collectors.joining("\n\n"));

    }

}
