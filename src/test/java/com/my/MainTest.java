package com.my;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@ExtendWith (MockitoExtension.class)
class MainTest {

    @Test
    void translateFromEnglishKeyboardLayout_thenEqual () {
        final List<String> expected =
                Arrays.asList("rjvfyls", "rjvFYls", "кjvFYls", "кjvF Yls", "rjvF Yls", "rjvF Yls 2", "кjvF Yls 2")
                .stream().map(Main::translateFromEnglishKeyboardLayoutIfNeeds)
                .collect(Collectors.toList());

        final List<String> actual =
                Arrays.asList("команды", "комАНды", "кjvFYls", "кjvF Yls", "комА Нды", "комА Нды 2", "кjvF Yls 2");

        assertIterableEquals(expected, actual);
    }
}