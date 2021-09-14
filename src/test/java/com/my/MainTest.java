package com.my;

import com.my.models.Group;
import com.my.services.lstu.LstuParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

class MainTest {

//    @Test
//    void reactorTrying () {
//        Flux
//                .generate(() -> )
//                .interval(Duration.of(1, ChronoUnit.DAYS))
//    }

    @Test
    void prodAddNewDataInGroup () {
        final LstuParser lstuParser = LstuParser.getInstance();
        final GroupsRepository groupsRepository = GroupsRepository.getInstance();

        for (Group group : groupsRepository.findAll()) {
            final Map<String, String> lkIds = lstuParser.getSubjectsGeneralLkIds("2021-О");
            group.setLkIds(
                    lkIds.get(LstuParser.SEMESTER_ID),
                    lkIds.get(LstuParser.GROUP_ID),
                    lkIds.get(LstuParser.CONTINGENT_ID)
            );
        }
    }

}