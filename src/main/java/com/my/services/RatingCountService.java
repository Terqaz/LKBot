package com.my.services;

import com.my.LstuClient;
import com.my.LstuUrlBuilder;
import com.my.SemesterSubjects;
import com.my.Subject;
import com.my.exceptions.ConnectionAttemptsException;
import org.apache.http.auth.AuthenticationException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

public class RatingCountService {

    private static final LstuClient lstuClient = LstuClient.getInstance();
    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    private static final int ATTEMPTS_COUNT = 8;

    public List<SemesterSubjects> getSemestersData () throws AuthenticationException {
        if (lstuClient.isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
        Document document = lstuClient.get(
                LstuUrlBuilder.buildSemestersUrl());

        final Elements htmlSemestersData = document.select("ul.ul-main > li");
        List<SemesterSubjects> semestersData = new ArrayList<>();

        int semesterNumber = htmlSemestersData.size();
        for (Element htmlSemesterData : htmlSemestersData) {
            SemesterSubjects semesterSubjects = getSemesterData(htmlSemesterData);
            semesterSubjects.setNumber(semesterNumber);
            semesterNumber--;
            semestersData.add(semesterSubjects);
        }
        return semestersData;
    }

    private SemesterSubjects getSemesterData (Element element) {
        final SemesterSubjects semesterSubjects = new SemesterSubjects();
        semesterSubjects.setName(element.text());
        List<Subject> subjects = getSubjects(element.select("a").attr("href"));
        semesterSubjects.setSubjects(subjects);
        return semesterSubjects;
    }

    private List<Subject> getSubjects (String localRef) {
        Document subjectsPage = null;
        Elements htmlSubjectsTableColumnNames = null;
        for (int attemptsLeft = 0; attemptsLeft < ATTEMPTS_COUNT; attemptsLeft++) {
            subjectsPage = lstuClient.get(
                    LstuUrlBuilder.buildByLocalUrl(localRef));
            htmlSubjectsTableColumnNames = subjectsPage.select("div.table-responsive").select("th");
            if (!htmlSubjectsTableColumnNames.isEmpty())
                break;
        }

        if (htmlSubjectsTableColumnNames.isEmpty()) {
            throw new ConnectionAttemptsException("Too many attempts to get data. Try later");
        } else if (htmlSubjectsTableColumnNames.size() <= 3) { // Subjects without data
            return Collections.emptyList();
        }

        Map<String, Integer> columnNames = new HashMap<>();
        int columnId = 0;
        for (Element htmlColumnName : htmlSubjectsTableColumnNames) {
            columnNames.put(htmlColumnName.text(), columnId);
            columnId++;
        }
        final Elements htmlSubjects = subjectsPage.select("tr.eduProc");

        final List<Subject> subjects = new ArrayList<>();
        for (Element element : htmlSubjects) {
            final Subject subject = new Subject();
            final Elements htmlTableRow = element.select("tr > td");
            for (Map.Entry<String, Integer> entry : columnNames.entrySet()) {
                String columnName = entry.getKey();
                columnId = entry.getValue();
                switch (columnName) {
                    case "Дисциплина":
                        subject.setName(htmlTableRow.get(columnId).text());
                        break;
                    case "Семестр":
                        subject.setSemesterWorkPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Зачет":
                        subject.setCreditPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Экзамен":
                        subject.setExamPoints(parseInt(htmlTableRow, columnId));
                        break;
                    case "Курсовая работа":
                        subject.setCourseWorkPoints(parseInt(htmlTableRow, columnId));
                        break;
                    default:
                        break;
                }
            }
            subjects.add(subject);
        }
        return subjects;
    }

    private int parseInt (Elements htmlTableRow, int columnId) {
        final int value;
        try {
            value = Integer.parseInt(htmlTableRow.get(columnId).text());
        } catch (NumberFormatException e) {
            return -1;
        }
        return value;
    }

}
