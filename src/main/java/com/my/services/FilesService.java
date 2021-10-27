package com.my.services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class FilesService {

    public static final String WORDS_PATH = "words";

    private FilesService() {}

    public static List<String> loadWords() {
        return readLines(WORDS_PATH);
    }

    public static List<String> readLines(String path) {
        try (var reader = new BufferedReader(new FileReader(path))) {
            return reader.lines().collect(Collectors.toList());
        } catch (IOException ignored) {
            return List.of();
        }
    }
}
