package com.my.services;

import com.mongodb.lang.Nullable;
import com.my.Utils;
import com.my.models.LkDocument;
import com.my.models.LkMessage;
import com.my.models.Subject;
import com.my.models.TimetableSubject;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Reports {

    private Reports() {}

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
            return emptySubjectsReport(nextCheckDate);

        final var sb = new StringBuilder();

        String reportPart = getNewMaterialsDocuments(subjects);
        if (reportPart.length() > 0)
            sb.append("\uD83D\uDD34 Новые документы из материалов:\n" + reportPart);

        reportPart = subjects.stream()
                .filter(data -> !data.getMessagesData().isEmpty())
                .map(data -> "➡ " + data.getId() + " " + data.getName() + ":\n" +
                        getSubjectMessages(data.getMessagesData())
                ).collect(Collectors.joining("\n\n"));

        if (reportPart.length() > 0)
            sb.append("\n\n\uD83D\uDD34 Новые сообщения:\n").append(reportPart);

        if (nextCheckDate != null)
            sb.append("\n\n").append(getNextUpdateDateText(nextCheckDate));

        return sb.toString();
    }

    private static String getNewMaterialsDocuments(List<Subject> subjects) {
        return subjects.stream()
                .filter(subject -> !subject.getMaterialsDocuments().isEmpty())
                .map(subject -> "➡ " + subject.getId() + " " + subject.getName() + ":\n" +
                        subject.getMaterialsDocuments().stream()
                                .map(lkDocument -> lkDocument.getId() + " " + lkDocument.getName())
                                .sorted()
                                .collect(Collectors.joining("\n"))
                ).collect(Collectors.joining("\n\n"));
    }

    public static String emptySubjectsReport(@Nullable Date nextCheckDate) {
        return "Нет новой информации по предметам\n" + getNextUpdateDateText(nextCheckDate);
    }

    private static String getSubjectMessages(List<LkMessage> messages) {
        return messages.stream()
                .map(lkMessage ->
                        "☑ " + lkMessage.getSender() + " в " +
                                Utils.formatDate(lkMessage.getDate()) + ":\n" +
                                lkMessage.getComment() + "\n" +
                                "ДОКУМЕНТ: " +
                                lkMessage.getDocument().getId() + " " + lkMessage.getDocument().getName()
                ).collect(Collectors.joining("\n\n"));
    }

    public static String getNextUpdateDateText (Date nextCheckDate) {
        return "Следующее обновление в " + Utils.formatDate(nextCheckDate);
    }

    public static String getDaySchedule (List<TimetableSubject> subjects, boolean isWhiteWeek) {
        return subjects.isEmpty() ? "" :
                "\uD83D\uDD36 " +
                (isWhiteWeek ? "Белая неделя" : "Зеленая неделя") +
                " \uD83D\uDD36\n" +
                subjects.stream()
                        .map(subject -> "➡ " + subject.getInterval() + ' ' +
                                subject.getName() + '\n' +
                                subject.getPlace() + '\n' +
                                subject.getAcademicName())
                        .collect(Collectors.joining("\n\n"));

    }

    public static String getSubjectDocuments(Subject subject) {
        String report = "Документы предмета " + subject.getName() + "\n";
        String reportPart = getSubjectDocumentsPart(subject.getMaterialsDocuments());
        if (!reportPart.isEmpty())
            report += "Документы из материалов:\n" + reportPart;

        reportPart = getSubjectDocumentsPart(subject.getMessagesDocuments());
        if (!reportPart.isEmpty())
            report += "\nДокументы из сообщений:\n" + reportPart;

        return report;
    }

    private static String getSubjectDocumentsPart(Set<LkDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(LkDocument::getId))
                .map(document -> document.getId() + " " + document.getName())
                .collect(Collectors.joining("\n"));
    }
}
