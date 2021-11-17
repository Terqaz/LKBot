package com.my;

import com.google.gson.Gson;
import com.my.models.LkDocument;
import com.my.models.Subject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class Utils {

    private static final Gson gson = new Gson();

    private Utils () {}

    public static String formatDate (Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }

    public static List<Subject> removeOldDocuments (List<Subject> oldSubjects,
                                                    List<Subject> newSubjects) {
        if (oldSubjects.isEmpty())
            return newSubjects;

        final Iterator<Subject> oldSubjectsIterator = oldSubjects.stream()
                .sorted(Comparator.comparing(Subject::getId)).iterator();
        
        final List<Subject> newSubjectsCopy = newSubjects.stream()
                .map(Utils::gsonDeepCopy)
                .sorted(Comparator.comparing(Subject::getId)).collect(Collectors.toList());
        
        final Iterator<Subject> newSubjectsCopyIterator = newSubjectsCopy.iterator();

        while (newSubjectsCopyIterator.hasNext()) {
            final Subject oldSubject = oldSubjectsIterator.next();
            final Subject newSubject = newSubjectsCopyIterator.next();

            final Map<String, LkDocument> newDocumentsMap = newSubject.getMaterialsDocuments().stream()
                    .collect(Collectors.toMap(d -> d.getLkId()+d.getName(), doc -> doc));

            oldSubject.getMaterialsDocuments().forEach(oldDocument ->
                    newDocumentsMap.remove(oldDocument.getLkId() + oldDocument.getName()));

            newSubject.setMaterialsDocuments(new HashSet<>(newDocumentsMap.values()));
        }
        return newSubjectsCopy.stream().filter(Subject::isNotEmpty).collect(Collectors.toList());
    }

    private static Subject gsonDeepCopy(Subject subject) {
        return gson.fromJson(gson.toJson(subject), Subject.class);
    }

    // Changes newDocuments
    public static void copyIdsFrom(Collection<LkDocument> newDocuments,
                                   Collection<LkDocument> oldDocuments) {

        if (newDocuments.isEmpty() || oldDocuments.isEmpty()) return;

        Map<String, Integer> oldDocumentByName = oldDocuments.stream()
                .collect(Collectors.toMap(d -> d.getLkId()+d.getName(), LkDocument::getId));

        newDocuments.forEach(newDocument -> {
            final Integer oldDocumentId = oldDocumentByName.get(newDocument.getLkId()+newDocument.getName());
            newDocument.setId(oldDocumentId);
        });
    }

    // Changes subject
    public static Subject setIdsWhereNull(Subject subject) {
        Integer nextId = Math.max(getMaxId(subject.getMaterialsDocuments()),
                getMaxId(subject.getMessagesDocuments())) + 1;
        nextId = setDocumentsIdsWhereNull(subject.getMaterialsDocuments(), nextId);
        setDocumentsIdsWhereNull(subject.getMessagesDocuments(), nextId);
        return subject;
    }

    // Changes lkDocuments
    public static Integer setDocumentsIdsWhereNull(Collection<LkDocument> lkDocuments, Integer nextId) {
        final List<LkDocument> nullIdDocuments = lkDocuments.stream()
                .filter(lkDocument -> lkDocument.getId() == null)
                .sorted(Comparator.comparing(LkDocument::getName))
                .collect(Collectors.toList());
        for (LkDocument document: nullIdDocuments) {
            document.setId(nextId);
            ++nextId;
        }
        return nextId;
    }

    private static Integer getMaxId(Collection<LkDocument> lkDocuments) {
        return lkDocuments.stream().map(LkDocument::getId)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Integer::intValue))
                .orElse(0);
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

    // Нулевой индекс на понедельнике (Monday), а Calendar.MONDAY = 2 ->
    // отнимаем 2 - попали на индекс нужного дня
    public static int getThisWeekDayIndex() {
        return (new GregorianCalendar().get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7;
    }

    // Как в предыдущем методе, но прибавляем 1 - попали на индекс следующего дня
    public static int getNextWeekDayIndex(GregorianCalendar calendar) {
        return calendar.get(Calendar.DAY_OF_WEEK) - 1;
    }

    public static <T> boolean isNullOrEmptyCollection(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }
}
