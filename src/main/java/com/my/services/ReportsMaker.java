package com.my.services;

import com.mongodb.lang.Nullable;
import com.my.Utils;
import com.my.models.MessageData;
import com.my.models.Subject;
import com.my.models.TimetableSubject;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ReportsMaker {

    private ReportsMaker () {}

    public static String getSubjectsNames (List<Subject> subjects) {
        final var sb = new StringBuilder();
        for (Subject data : subjects) {
            sb.append("➡ ").append(data.getId()).append(" ")
                    .append(data.getName()).append("\n");
        }
        return sb.toString();
    }

    public static String getSubjects(List<Subject> subjects, @Nullable Date nextCheckDate) {
        if (subjects.isEmpty())
            return "Нет новой информации по предметам\n" + getNextUpdateDateText(nextCheckDate);

        final var sb = new StringBuilder();

        String partBuilder = subjects.stream()
                .filter(subject -> !subject.getDocumentNames().isEmpty())
                .map(data -> "➡ " + data.getId() + " " + data.getName() + ":\n" +
                        data.getDocumentNames().stream()
                                .sorted()
                                .collect(Collectors.joining("\n"))
                ).collect(Collectors.joining("\n\n"));

        if (partBuilder.length() > 0)
            sb.append("\uD83D\uDD34 Новые документы:\n" + partBuilder);

        partBuilder = subjects.stream()
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
        return subjects.isEmpty() ? "" :
                "\uD83D\uDD36 Сегодня " +
                (isWhiteWeek ? "белая неделя" : "зеленая неделя") +
                " \uD83D\uDD36\n" +
                subjects.stream()
                        .map(subject -> "➡ " + subject.getInterval() + ' ' +
                                subject.getName() + '\n' +
                                subject.getPlace() + '\n' +
                                subject.getAcademicName())
                        .collect(Collectors.joining("\n\n"));

    }

}
