package com.my;

import com.my.models.Group;
import com.my.models.Subject;
import com.my.services.CipherService;
import com.my.services.lk.LkParser;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Log4j2
class MainTest {

    static final GroupsRepository repository = GroupsRepository.getInstance();
    static CipherService cipherService;

    @Test
    @Disabled("Только для ручного обновления группы")
    void updateGroup () throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        cipherService = CipherService.getInstance();
        String actualSemester = Utils.getSemesterName();

        String groupName = "ТЭ-20-2";
        log.info("Обновление группы: " + groupName);

        final Optional<Group> optional = repository.findByGroupName(groupName);
        if (optional.isEmpty()) {
            log.error("Группа не найдена");
            return;
        }

        final Group group = optional.get();
        final LkParser lkParser = new LkParser();

        group.setLkParser(new LkParser());

        lkParser.login(cipherService.decrypt(group.getLoggedUser().getAuthData()));

        List<Subject> newSubjects = lkParser.getSubjectsFirstTime(actualSemester);
        log.info("Документы скачаны");

        lkParser.setSubjectsGeneralLkIds(group, actualSemester);
        log.info("Айдишники скачаны");

        group
                .setSubjects(newSubjects)
                .setLastCheckDate(new Date());

        group.setTimetable(lkParser.parseTimetable(group.getLkSemesterId(), group.getLkId()));
        log.info("Расписание скачано");

        final long count = repository.updateGroup(group);

        if (count == 1) {
            log.info("Группа обновлена");
        } else if (count == 0) {

            log.info("Группа не обновлена");

        } else if (count > 1) {
            log.error("!Обновлено больше одной группы");
        }
    }
}