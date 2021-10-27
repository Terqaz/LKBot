package com.my;

import com.my.models.Group;
import com.my.services.lk.LkParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

class MainTest {

    @Test
    void prodAddNewDataInGroup () {
        final LkParser lkParser = new LkParser();
        final GroupsRepository groupsRepository = GroupsRepository.getInstance();

        for (Group group : groupsRepository.findAll()) {
            final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds("2021-О");
            group.setLkIds(
                    lkIds.get(LkParser.SEMESTER_ID),
                    lkIds.get(LkParser.GROUP_ID),
                    lkIds.get(LkParser.CONTINGENT_ID)
            );
        }
    }

}