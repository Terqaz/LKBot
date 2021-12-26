package com.my;

import com.mongodb.client.FindIterable;
import com.my.models.*;
import com.my.repositories.GroupsRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

// MONGO_STRING=mongodb://localhost:27017/lk-bot?retryWrites=true&w=majority
@Log4j2
class GroupsRepositoryTest {

    static final GroupsRepository repository = GroupsRepository.getInstance();

    static final String testGroupName = "Тест";
    static Group testGroup;

    @BeforeEach
    void init() {
        testGroup = new Group(testGroupName);
    }

    @AfterEach
    void end () {
        repository.deleteMany(testGroupName);
    }

    @Test
    void userLogged_thenRelogged_thenUnlogged_isCorrect () {
        // Подготовка
        testGroup
                .setUsers(Set.of(new GroupUser(1234)))
                .setLoggedUser(new LoggedUser(
                1234, new AuthenticationData("login", "pass"), false));

        repository.insert(testGroup);

        // Тестируемое
        repository.updateLoggedUser(testGroupName, new LoggedUser(
                12345, new AuthenticationData("login2", "pass2"), false));

        // Проверка
        Group group = repository.findByGroupName(testGroupName).get();
        assertEquals(Set.of(new GroupUser(1234), new GroupUser(12345)), new HashSet<>(group.getUsers()));

        // Тестируемое
        repository.removeLoggedUser(testGroupName, 12345);

        // Проверка
        group = repository.findByGroupName(testGroupName).get();
        assertEquals(Set.of(new GroupUser(1234)), new HashSet<>(group.getUsers()));
        assertNull(group.getLoggedUser());
    }

    @Test
    void moveVerifiedUserToUsers_IsCorrect () {
        // Подготовка
        final int userId = 123;
        final UserToVerify verifiesUser = new UserToVerify(userId, 123456);
        final UserToVerify user2 = new UserToVerify(userId, 345678);
        final UserToVerify user3 = new UserToVerify(12345, 123456);

        testGroup.setUsersToVerify(Set.of(verifiesUser, user2, user3));
        repository.insert(testGroup);

        // Тестируемое
        repository.moveVerifiedUserToUsers(testGroupName, verifiesUser);

        // Проверка
        final Group group = repository.findByGroupName(testGroupName).get();

        assertFalse(group.getUsersToVerify().contains(verifiesUser));
        assertEquals(2, group.getUsersToVerify().size());
        assertTrue(group.getUsers().stream()
                .allMatch(user -> user.getId().equals(userId)));
    }

    @Test
    void moveLoginWaitingUsersToUsers_IsCorrect () {
        // Подготовка
        final int repeatedId = 1234;
        testGroup
                .setUsers(Set.of(new GroupUser(1231), new GroupUser(1232), new GroupUser(1233), new GroupUser(repeatedId)))
                .setLoginWaitingUsers(Set.of(repeatedId, 12345, 123456));
        repository.insert(testGroup);

        // Тестируемое
        repository.moveLoginWaitingUsersToUsers(testGroupName);

        // Проверка
        final Group group = repository.findByGroupName(testGroupName).get();

        assertEquals(6, group.getUsers().size());
        assertTrue(group.getLoginWaitingUsers().isEmpty());

        assertEquals(1,
                group.getUsers().stream()
                        .filter(user -> user.getId().equals(repeatedId))
                        .count());
    }

    @Test
    void updateUserScheduling_isCorrect() {
        testGroup.setUsers(Set.of(
                new GroupUser(1, false),
                new GroupUser(2, false),
                new GroupUser(3, true)
                ));

        repository.insert(testGroup);
        repository.updateUserScheduling(testGroupName, 2, true);

        assertIterableEquals(List.of(
                new GroupUser(1, false),
                new GroupUser(2, true),
                new GroupUser(3, true)
        ),
                repository.findByGroupName(testGroupName).get().getUsers().stream()
                        .sorted(Comparator.comparing(GroupUser::getId))
                        .collect(Collectors.toList()));
    }

    @Test
    void groupScheduleCorrectSaves () {
        final var timetable = new Timetable()
                .addWhiteSubject(0, new TimetableSubject("a1", "b1", "c1", "d1"))
                .addWhiteSubject(0, new TimetableSubject("a2", "b2", "c2", "d3"))
                .addWhiteSubject(3, new TimetableSubject("a3", "b3", "c3", "d3"))
                .addGreenSubject(1, new TimetableSubject("a4", "b4", "c4", "d4"))
                .addGreenSubject(1, new TimetableSubject("a5", "b5", "c5", "d5"))
                .addGreenSubject(5, new TimetableSubject("a6", "b6", "c6", "d6"));

        testGroup.setTimetable(timetable);
        repository.insert(testGroup);

        final var group2 = repository.findByGroupName(testGroupName).get();
        assertEquals(timetable, group2.getTimetable());
    }

    @Test
    void findAllWithoutTimetable_isCorrect () {
        final var timetable = new Timetable()
                .addWhiteSubject(0, new TimetableSubject("a1", "b1", "c1", "d1"))
                .addGreenSubject(5, new TimetableSubject("a6", "b6", "c6", "d6"));

        testGroup.setTimetable(timetable);
        repository.insert(testGroup);

        final Group group2 = assertDoesNotThrow(() ->
                repository.findAllWithoutTimetable().
                        filter(eq("name", testGroupName))
                        .first());

        assertNull(group2.getTimetable());
    }

    @Test
    void updateEachField_isCorrect() {
        final String eachFieldTest = "eachFieldTest";
        final List<Group> groups = Stream.generate(() ->
                new Group().setName(eachFieldTest).setScheduleSent(true))
                .limit(3)
                .collect(Collectors.toList());
        repository.insertMany(groups);

        repository.updateEachField(GroupsRepository.SCHEDULE_SENT, false);

        final FindIterable<Group> groups1 = repository.findAll().filter(eq("name", eachFieldTest));
        groups1.forEach(group ->
                assertFalse(group.isScheduleSent())
        );
        repository.deleteMany(eachFieldTest);
    }

    @Test
    void updateDocumentVkAttachment_isCorrect() {
        testGroup.setSubjects(TestUtils.createSubjects1());

        repository.insert(testGroup);

        repository.updateDocumentVkAttachment(testGroupName, 1, 1, false, "attachment1");
        repository.updateDocumentVkAttachment(testGroupName, 2, 2, true, "attachment2");

        final Group group2 = repository.findByGroupName(testGroupName).get();

        assertEquals("attachment1", group2.getSubjectById(1).get().getMessageDocumentById(1).getVkAttachment());
        assertEquals("attachment2", group2.getSubjectById(2).get().getMaterialsDocumentById(2).getVkAttachment());
    }

    @Test
    @Disabled("Пройден")
    void updateLkIds_isCorrect() {
        testGroup.setLkId("lkid");
        testGroup.setLkSemesterId("semesterid");
        testGroup.setLkContingentId("lkcontingentid");

        repository.updateLkIds(testGroupName,
                testGroup.getLkId(), testGroup.getLkSemesterId(), testGroup.getLkContingentId());
        repository.insert(testGroup);

        final Group group2 = repository.findByGroupName(testGroupName).get();
        assertEquals("lkid", group2.getLkId());
        assertEquals("semesterid", group2.getLkSemesterId());
        assertEquals("lkcontingentid", group2.getLkContingentId());
    }
}