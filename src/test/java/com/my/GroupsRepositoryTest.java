package com.my;

import com.my.models.AuthenticationData;
import com.my.models.Group;
import com.my.models.LoggedUser;
import com.my.models.UserToVerify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupsRepositoryTest {

    static final GroupsRepository repository = GroupsRepository.getInstance();

    final String testGroupName = "Тест";
    static Group testGroup;

    @BeforeEach
    void init() {
        repository.deleteMany(testGroupName);
        testGroup = new Group(testGroupName);
    }

    @Test
    void userLogged_thenRelogged_thenUnlogged () {
        // Подготовка
        testGroup
                .setUsers(List.of(1234))
                .setLoggedUser(new LoggedUser(
                1234, new AuthenticationData("login", "pass"), true, false));

        repository.insert(testGroup);

        // Тестируемое
        repository.updateLoggedUser(testGroupName, new LoggedUser(
                12345, new AuthenticationData("login2", "pass2"), false, false));

        // Проверка
        Group group = repository.findByGroupName(testGroupName).get();
        assertEquals(Set.of(1234, 12345), new HashSet<>(group.getUsers()));

        // Тестируемое
        repository.removeLoggedUser(testGroupName, 12345);

        // Проверка
        group = repository.findByGroupName(testGroupName).get();
        assertEquals(Set.of(1234), new HashSet<>(group.getUsers()));
        assertEquals(new LoggedUser(0, null, true, false), group.getLoggedUser());
    }

    @Test
    void moveVerifiedUserToUsersIsCorrect () {
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
        assertEquals(1, group.getUsers().size());
        assertTrue(group.getUsers().contains(userId));
    }

    @Test
    void moveLoginWaitingUsersToUsersIsCorrect () {
        // Подготовка
        final int repeatedId = 1234;
        testGroup
                .setUsers(List.of(1231, 1232, 1233, repeatedId))
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
                        .filter(integer -> integer.equals(repeatedId))
                        .count());
    }
}