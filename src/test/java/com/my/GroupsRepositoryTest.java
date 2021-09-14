package com.my;

import com.my.models.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// MONGO_STRING = "mongodb://localhost:27017/lk-bot?retryWrites=true&w=majority"
class GroupsRepositoryTest {

    static final GroupsRepository repository = GroupsRepository.getInstance();

    static final String testGroupName = "Тест";
    static Group testGroup;

    @BeforeEach
    void init() {
        testGroup = new Group(testGroupName);
    }

    @AfterAll
    static void end () {
        repository.deleteMany(testGroupName);
    }

    @Test
    void userLogged_thenRelogged_thenUnlogged_isCorrect () {
        // Подготовка
        testGroup
                .setUsers(List.of(new GroupUser(1234)))
                .setLoggedUser(new LoggedUser(
                1234, new AuthenticationData("login", "pass"), true, false));

        repository.insert(testGroup);

        // Тестируемое
        repository.updateLoggedUser(testGroupName, new LoggedUser(
                12345, new AuthenticationData("login2", "pass2"), false, false));

        // Проверка
        Group group = repository.findByGroupName(testGroupName).get();
        assertEquals(Set.of(new GroupUser(1234), new GroupUser(12345)), new HashSet<>(group.getUsers()));

        // Тестируемое
        repository.removeLoggedUser(testGroupName, 12345);

        // Проверка
        group = repository.findByGroupName(testGroupName).get();
        assertEquals(Set.of(new GroupUser(1234)), new HashSet<>(group.getUsers()));
        assertEquals(new LoggedUser(0, null, true, false), group.getLoggedUser());
    }

    @Test
    void moveVerifiedUserToUsers_IsCorrect () {
        // Подготовка
        final int userId = 123;
        final UserToVerify verifiesUser = new UserToVerify(userId, 123456);
        final UserToVerify user2 = new UserToVerify(userId, 345678);
        final UserToVerify user3 = new UserToVerify(12345, 123456);

        testGroup.setUsersToVerify(List.of(verifiesUser, user2, user3));
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
                .setUsers(List.of(new GroupUser(1231), new GroupUser(1232), new GroupUser(1233), new GroupUser(repeatedId)))
                .setLoginWaitingUsers(List.of(repeatedId, 12345, 123456));
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
        testGroup.setUsers(List.of(
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
                repository.findByGroupName(testGroupName).get().getUsers());
    }

    @Test
    void groupScheduleCorrectSaves () {
        final var timetable = new Timetable()
                .addWhiteWeekSubject(0, new TimetableSubject("a1", "b1", "c1", "d1"))
                .addWhiteWeekSubject(0, new TimetableSubject("a2", "b2", "c2", "d3"))
                .addWhiteWeekSubject(3, new TimetableSubject("a3", "b3", "c3", "d3"))
                .addGreenWeekDaySubject(1, new TimetableSubject("a4", "b4", "c4", "d4"))
                .addGreenWeekDaySubject(1, new TimetableSubject("a5", "b5", "c5", "d5"))
                .addGreenWeekDaySubject(5, new TimetableSubject("a6", "b6", "c6", "d6"));

        testGroup.setTimetable(timetable);
        repository.insert(testGroup);

        final var group2 = repository.findByGroupName(testGroupName).get();
        assertEquals(timetable, group2.getTimetable());
    }
}