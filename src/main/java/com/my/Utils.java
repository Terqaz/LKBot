package com.my;

import com.my.models.SubjectData;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class Utils {

    private Utils () {}

    public static String formatDate (Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }

    // oldSubjectsData и newSubjectsData должны быть отсортированы в порядке возрастания имени
    public static List<SubjectData> removeOldDocuments (
            List<SubjectData> oldSubjectsData, List<SubjectData> newSubjectsData) {

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

    public static String getNewScannedSemesterName () {
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

    private static final Map<Character, Character> enToRuCharsMap = new HashMap<>();
    static {
        final String enChars = "qwertyuiop[]asdfghjkl;'zxcvbnm,./QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?";
        final String ruChars = "йцукенгшщзхъфывапролджэячсмитьбю.ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ.";
        for (int i = 0; i < enChars.length(); i++) {
            enToRuCharsMap.put(enChars.charAt(i), ruChars.charAt(i));
        }
    }

    private static boolean isEnCharsString(String s) {
        return StandardCharsets.US_ASCII.newEncoder().canEncode(s);
    }

    public static String translateFromEnglishKeyboardLayoutIfNeeds (String s1) {
        if (!isEnCharsString(s1))
            return s1;

        StringBuilder s2 = new StringBuilder();
        for (int i = 0; i < s1.length(); i++) {
            final char s1char = s1.charAt(i);
            s2.append(enToRuCharsMap.getOrDefault(s1char, s1char));
        }
        return s2.toString();
    }

    private static final Random random = new Random();

    public static Integer generateVerificationCode () {
        return nextRandomInt(100_000, 1_000_000);
    }

    private static int nextRandomInt(int min, int max) {
        return random.nextInt(max - min) + min;
    }
}
