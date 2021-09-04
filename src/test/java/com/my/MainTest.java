package com.my;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

class MainTest {

    @Test
    void reactorTrying () {
        Flux
                .generate(() -> )
                .interval(Duration.of(1, ChronoUnit.DAYS))
    }

}