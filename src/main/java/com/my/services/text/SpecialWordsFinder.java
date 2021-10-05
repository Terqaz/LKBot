package com.my.services.text;

import com.my.services.FilesService;

import java.util.List;
import java.util.Set;

public class SpecialWordsFinder {

    private SpecialWordsFinder() {}

    private static final Set<String> goodWords;
    private static final Set<String> obsceneWords;

    static {
        final List<String> words;
        words = FilesService.loadWords();

        goodWords = Set.of(words.get(0).split(" "));
        obsceneWords = Set.of(words.get(1).split(" "));
    }

    // -1 - найдено нецензурное слово; 0 - не найдено никаких слов; 1 - найдены благодарственные слова
    public static int findSpecialWords(Integer userId, String messageText) {
        final String[] wordsWithSymbols = messageText.split(" ");
        return 0;
    }
}
