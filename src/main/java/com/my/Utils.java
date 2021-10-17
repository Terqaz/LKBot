package com.my;

import com.my.models.Subject;
import org.apache.commons.lang3.SerializationUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class Utils {

    private Utils () {}

    public static String formatDate (Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }

    // oldSubjects и newSubjects должны быть отсортированы в порядке возрастания имени
    public static List<Subject> removeOldDocuments (List<Subject> oldSubjects,
                                                    List<Subject> newSubjects) {
        Map<String, Set<String>> oldDocumentsMap = new HashMap<>();
        for (Subject data : oldSubjects)
            oldDocumentsMap.put(data.getName(), data.getDocumentNames());

        return newSubjects.stream()
                .map(SerializationUtils::clone)
                .map(newSubject -> {
                    final Set<String> newDocuments = newSubject.getDocumentNames();
                    final var oldDocuments = oldDocumentsMap.get(newSubject.getName());
                    newDocuments.removeAll(oldDocuments);
                    return newSubject;
                })
                .filter(Subject::isNotEmpty)
                .collect(Collectors.toList());
    }

    public static String getSemesterName() {
        Calendar now = new GregorianCalendar();
        Calendar autumnSemesterStart = new GregorianCalendar();
        autumnSemesterStart.set(Calendar.MONTH, Calendar.AUGUST);
        autumnSemesterStart.set(Calendar.DAY_OF_MONTH, 18);

        Calendar springSemesterStart = new GregorianCalendar();
        springSemesterStart.set(Calendar.MONTH, Calendar.JANUARY);
        springSemesterStart.set(Calendar.DAY_OF_MONTH, 15);

        final int year = now.get(Calendar.YEAR);
        if (now.after(springSemesterStart) && now.before(autumnSemesterStart))
            return year + "-В";
        else
            return year + "-О";
    }

    public static Integer tryParseInteger (String messageText) {
        try {
            return Integer.parseInt(messageText);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }


    public static boolean isSilentTime (int start, int end, int nowHour) {
        if (start < end)
            return start <= nowHour && nowHour <= end;

        else if (start > end) {
            return start <= nowHour || nowHour <= end;

        } else return true;
    }

    private static final Random random = new Random();

    public static Integer generateVerificationCode () {
        return nextRandomInt(100_000, 1_000_000);
    }

    private static int nextRandomInt(int min, int max) {
        return random.nextInt(max - min) + min;
    }

    // Возвращает время, необходимое, чтобы трэд спал до начала следующего часа
    public static long getSleepTimeToHourStart(int nowMinute, int nowSecond) {
        return ((59 - nowMinute) * 60L + (60 - nowSecond)) * 1000L;
    }

    public static int mapWeekDayFromCalendar() {
        return new GregorianCalendar().get(Calendar.DAY_OF_WEEK) - 1;
    }

    public static int mapWeekDayFromCalendar(GregorianCalendar calendar) {
        return calendar.get(Calendar.DAY_OF_WEEK) - 2;
    }
}
