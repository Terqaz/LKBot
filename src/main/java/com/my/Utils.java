package com.my;

import com.my.models.SubjectData;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class Utils {

    private Utils () {}

    public static String formatDate (Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }

    // oldSubjectsData и newSubjectsData должны быть отсортированы в порядке возрастания имени
    public static List<SubjectData> removeOldDocuments (List<SubjectData> oldSubjectsData,
                                                        List<SubjectData> newSubjectsData) {
        Map<String, Set<String>> oldDocumentsMap = new HashMap<>();
        for (SubjectData data : oldSubjectsData)
            oldDocumentsMap.put(data.getName(), data.getDocumentNames());

        return newSubjectsData.stream()
                .map(newData -> {
                        final Set<String> newDocuments = newData.getDocumentNames();
                        final var oldDocuments = oldDocumentsMap.get(newData.getName());
                        newDocuments.removeAll(oldDocuments);
                        return newData;
                })
                .filter(SubjectData::isNotEmpty)
                .collect(Collectors.toList());
    }

    public static String actualizeSemesterName() {
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
        return new GregorianCalendar().get(Calendar.DAY_OF_WEEK) - 2;
    }

    public static int mapWeekDayFromCalendar(GregorianCalendar calendar) {
        return calendar.get(Calendar.DAY_OF_WEEK) - 2;
    }
}
