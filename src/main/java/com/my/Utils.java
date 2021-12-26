package com.my;

import com.google.gson.*;
import com.my.models.LkDocument;
import com.my.models.LkMessage;
import com.my.models.Subject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public final class Utils {

    private Utils () {}

    private static class GsonLocalDateTime implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            String ldtString = jsonElement.getAsString();
            return LocalDateTime.parse(ldtString,DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        @Override
        public JsonElement serialize(LocalDateTime localDateTime, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }

    public static final Gson gson;
    static {
        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new GsonLocalDateTime())
                .create();
    }

    private static Subject gsonDeepCopy(Subject subject) {
        return gson.fromJson(gson.toJson(subject), Subject.class);
    }

    static DateTimeFormatter reportFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    static DateTimeFormatter timeReportFormatter = DateTimeFormatter.ofPattern("HH:mm");
    static DateTimeFormatter queryFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy+HH:mm");

    public static String reportFormatMessageDate(LocalDateTime date) {
        if (date.toLocalDate().equals(LocalDate.now()))
            return " сегодня в " + date.format(timeReportFormatter);

        else if (date.toLocalDate().equals(LocalDate.now().minusDays(1)))
            return " вчера в " + date.format(timeReportFormatter);
        else
            return " в " + date.format(reportFormatter);
    }

    public static LocalDateTime responseParseMessageDate(String s) {
        try {
            return LocalDateTime.parse(s, reportFormatter);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    public static String queryFormatMessageDate(LocalDateTime date) {
        return date.format(queryFormatter);
    }

    static Path correctFileExt(Path path) {
        String strFilePath = path.toString();
        if (TextUtils.isUnacceptableFileExtension(strFilePath)) {
            final Path newPath = Paths.get(strFilePath + 1);
            try {
                FileUtils.moveFile(path.toFile(), newPath.toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return newPath;
        }
        return path;
    }

    static Boolean isExtensionChanged(Path path) {
        if (path.toString().endsWith("1"))
            return true;
        else return null;
    }

    public static List<Subject> removeOldMaterialsDocuments(List<Subject> oldSubjects,
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

    public static String hashLkMessage(LkMessage message) {
        String s = message.getDate().format(reportFormatter) + message.getSender() + message.getComment();
        if (message.getDocument() != null)
            s += message.getDocument().getLkId();

        return DigestUtils.md5Hex(s).substring(0, 8);
    }
}
