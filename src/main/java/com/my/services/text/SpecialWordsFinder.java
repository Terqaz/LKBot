package com.my.services.text;

import com.my.FilesService;

import java.util.List;
import java.util.Set;

public class SpecialWordsFinder {
    private SpecialWordsFinder() {}

    private static final Set<String> goodWords;
    private static final Set<String> obsceneWords;

    static {
        final List<String> words = FilesService.loadWords();
        goodWords = Set.of(words.get(0).split(" "));
        obsceneWords = Set.of(words.get(1).split(" "));
    }

    public static void findSpecialWords(Integer userId, String messageText) {
        final String[] wordsWithSymbols = messageText.split(" ");
    }
}
