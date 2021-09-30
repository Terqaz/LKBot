package com.my.models;

import com.my.services.lk.LkParser;
import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@Accessors (chain = true)
public class Group {

    @NonNull
    private String name;
    private String lkId;
    private String lkSemesterId;
    private String lkContingentId;
    private List<Subject> subjects = new ArrayList<>();
    private Date lastCheckDate;
    private long updateInterval = 12L * 3600 * 1000; // 12 часов

    private LoggedUser loggedUser;
    private Set<GroupUser> users = new HashSet<>();
    private Set<UserToVerify> usersToVerify = new HashSet<>();
    private Set<Integer> loginWaitingUsers = new HashSet<>();

    private Timetable timetable;

    private int silentModeStart = 2; // Час [0, 23]
    private int silentModeEnd = 6;   // Час [0, 23]

    @BsonIgnore
    private LkParser lkParser;

    @BsonIgnore
    public boolean isNotLoggedNow () {
        return loggedUser.getAuthData() == null;
    }

    @BsonIgnore
    public boolean isLoggedBefore () {
        return !(subjects.isEmpty() && users.isEmpty());
    }

    @BsonIgnore
    public Date getNextCheckDate () {
        return new Date(lastCheckDate.getTime() + updateInterval);
    }

    @BsonIgnore
    public boolean containsUser(Integer userId) {
        return users.stream()
                .anyMatch(user -> user.getId().equals(userId));
    }

    @BsonIgnore
    public void setLkIds (String semesterId, String groupId, String unknownId) {
        lkSemesterId = semesterId;
        lkId = groupId;
        lkContingentId = unknownId;
    }

    @BsonIgnore
    public List<Integer> getUserIds () {
        return users.stream()
                .map(GroupUser::getId)
                .collect(Collectors.toList());
    }

    @BsonIgnore
    public boolean getUserSchedulingEnabled (Integer userId) {
        return users.stream()
                .filter(user -> user.getId().equals(userId))
                .map(GroupUser::isEverydayScheduleEnabled)
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Неизвестный id пользователя: "+userId+" в группе: "+name));
    }

    @BsonIgnore
    public void removeLoggedUser(Integer id) {
        removeUserFromGroup(id);
        loggedUser.setId(0).setAuthData(null);
    }

    @BsonIgnore
    public void removeUserFromGroup(Integer id) {
        users = users.stream()
                .filter(user -> !user.getId().equals(id))
                .collect(Collectors.toSet());

        usersToVerify = usersToVerify.stream()
                .filter(user -> !user.getId().equals(id))
                .collect(Collectors.toSet());

        loginWaitingUsers.remove(id);
    }

    @BsonIgnore
    public void moveVerifiedUserToUsers(UserToVerify user) {
        users.add(new GroupUser(user.getId()));
        usersToVerify = usersToVerify.stream()
                .filter(user1 -> !user1.equals(user))
                .collect(Collectors.toSet());
    }
}