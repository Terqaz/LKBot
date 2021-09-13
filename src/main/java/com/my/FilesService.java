package com.my;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

public class FilesService {

    public static final String TS_PATH = "ts";
    public static final String WORDS_PATH = "words";

    private FilesService() {}

    public static Integer loadLastTs() {
        return Integer.parseInt(readLines(TS_PATH).get(0));
    }

    public static void saveLastTs(Integer ts) {
        try (var writer = new BufferedWriter(new FileWriter(TS_PATH))) {
            writer.write(ts.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> loadWords() {
        return readLines(WORDS_PATH);
    }

    public static List<String> readLines(String path) {
        try (var reader = new BufferedReader(new FileReader(path))) {
            return reader.lines().collect(Collectors.toList());
        } catch (IOException ignored) {
            return null;
        }
    }
}
