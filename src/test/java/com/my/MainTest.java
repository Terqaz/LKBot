package com.my;

import com.mongodb.client.FindIterable;
import com.my.models.Group;
import com.my.models.LoggedUser;
import com.my.models.Subject;
import com.my.models.temp.OldGroup;
import com.my.repositories.GroupsRepository;
import com.my.services.CipherService;
import com.my.services.lk.LkParser;
import com.my.threads.PlannedSubjectsUpdate;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
class MainTest {

    static final GroupsRepository repository = GroupsRepository.getInstance();
    static CipherService cipherService;

    @Test
    @Disabled("Только для ручного обновления группы")
    void updateGroup () throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        cipherService = CipherService.getInstance();
        String actualSemester = Utils.getActualSemester();

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

        group.setSubjects(newSubjects);

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

    @Test
    @Disabled("Только для ручного обновления групп")
    void temp_updateGroups() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        System.out.println("Обновление");

        final LkParser lkParser = new LkParser();
        CipherService cipherService = CipherService.getInstance();

        final FindIterable<OldGroup> oldGroups = repository.olgGroupsCollection.find();

        List<OldGroup> oldGroupList = new LinkedList<>();
        oldGroups.forEach(oldGroupList::add);

//        FileUtils.write(new File("dump.json"), Utils.gson.toJson(oldGroupList), StandardCharsets.UTF_8);

        Set<String> updatedGroups = Set.of(); // Заполнять по мере необходимости

        oldGroups.forEach(oldGroup -> {
            if (updatedGroups.contains(oldGroup.getName())) {
                System.out.println("Группа " + oldGroup.getName() + " уже обновлена");
                return;
            }

            try {
                updateGroup(lkParser, cipherService, oldGroup);
            } catch (Exception e) {
                System.out.println("Ошибка у группы " + oldGroup.getName());
                e.printStackTrace();
            }
        });
    }

    private void updateGroup(LkParser lkParser, CipherService cipherService, OldGroup oldGroup) {
        final String groupName = oldGroup.getName();

//            if (document.get("scheduleSent") != null) {
//                System.out.println("Группа "+ groupName+" уже обновлена");
//                return;
//            }

        final LoggedUser loggedUser = oldGroup.getLoggedUser();
        if (loggedUser == null || loggedUser.getAuthData() == null) {
            System.out.println("group " + groupName + "need to delete");
            return;
        }

        try {
            lkParser.login(cipherService.decrypt(loggedUser.getAuthData()));
        } catch (Exception e) {
            System.out.println("auth exception with: "+groupName);
            e.printStackTrace();
            return;
        }

        List<Subject> newSubjects = lkParser.getSubjectsFirstTime("2021-О");
        if (newSubjects == null || newSubjects.isEmpty())
            newSubjects = lkParser.getSubjectsFirstTime("2021-О");

        Map<String, Subject> newSubjectsMap = newSubjects.stream()
                .collect(Collectors.toMap(Subject::getLkId, subject -> subject));

        oldGroup.getSubjects().forEach(oldSubject -> {
            final Subject newSubject = newSubjectsMap.get(oldSubject.getLkId());
            PlannedSubjectsUpdate.copyVkAttachments(newSubject.getMaterialsDocuments(), oldSubject.getMaterialsDocuments());
            PlannedSubjectsUpdate.copyVkAttachments(newSubject.getMessagesDocuments(), oldSubject.getMessagesDocuments());
        });

        final Group newGroup = new Group()
                .setName(oldGroup.getName())
                .setLkId(oldGroup.getLkId())
                .setLkSemesterId(oldGroup.getLkSemesterId())
                .setLkContingentId(oldGroup.getLkContingentId())
                .setSubjects(newSubjects)
                .setLoggedUser(loggedUser)
                .setUsers(oldGroup.getUsers())
                .setUsersToVerify(oldGroup.getUsersToVerify())
                .setLoginWaitingUsers(oldGroup.getLoginWaitingUsers())
                .setTimetable(oldGroup.getTimetable())
                .setScheduleSent(oldGroup.isScheduleSent())
                .setSilentModeStart(oldGroup.getSilentModeStart())
                .setSilentModeEnd(oldGroup.getSilentModeEnd());

        lkParser.logout();

        if (TestUtils.listsSizeCount(newGroup.getTimetable().getWhiteSubjects()) == 0) {
            System.out.println("Расписание группы "+groupName+" пустое");
            return;
        }
        if (newGroup.getSubjects().isEmpty()) {
            System.out.println("Предметы группы "+groupName+" пустые");
            return;
        }

        repository.updateGroup(newGroup);
        System.out.println(groupName +" обновлена");
    }
}