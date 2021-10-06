package com.my;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MainTest {

    @Test
    void completableFutures_using () {
        final List<CompletableFuture<Void>> futures = Stream.generate(
                () -> CompletableFuture.runAsync(() -> {
                        try {
                            delayedPrint();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                }))
                .limit(50)
                .collect(Collectors.toList());

        final CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[futures.size()]));

        try {
            allOf.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    static int i = 1;

    void delayedPrint () throws InterruptedException {
        System.out.println(i);
        i++;
    }
}