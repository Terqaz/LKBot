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

    private LoggedUser loggedUser;
    private Set<GroupUser> users = new HashSet<>();
    private Set<UserToVerify> usersToVerify = new HashSet<>();
    private Set<Integer> loginWaitingUsers = new HashSet<>();

    private Timetable timetable;
    private boolean isScheduleSent = false;

    private int silentModeStart = 2; // Час [0, 23]
    private int silentModeEnd = 6;   // Час [0, 23]

    @BsonIgnore
    private LkParser lkParser;

    @BsonIgnore
    public boolean hasLeader() {
        return loggedUser != null;
    }

    @BsonIgnore
    public boolean isNotLoggedBefore() {
        return subjects.isEmpty() && users.isEmpty();
    }

    @BsonIgnore
    public boolean containsUser(Integer userId) {
        return users.stream()
                .anyMatch(user -> user.getId().equals(userId));
    }

    @BsonIgnore
    public List<Integer> getUserIds () {
        return users.stream()
                .map(GroupUser::getId)
                .collect(Collectors.toList());
    }

    @BsonIgnore
    public List<Integer> getSchedulingEnabledUsers () {
        return getUsers().stream()
                .filter(GroupUser::isEverydayScheduleEnabled)
                .map(GroupUser::getId)
                .collect(Collectors.toList());
    }

    @BsonIgnore
    public Optional<Subject> getSubjectById(int id) {
        return subjects.stream()
                .filter(subject -> subject.getId() == id)
                .findFirst();
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
    public void setLkIds (String semesterId, String groupId, String unknownId) {
        lkSemesterId = semesterId;
        lkId = groupId;
        lkContingentId = unknownId;
    }

    @BsonIgnore
    public void removeLoggedUser(Integer id) {
        removeUserFromGroup(id);
        loggedUser = null;
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

    @BsonIgnore
    public void setNewSemesterData(List<Subject> subjects, Date checkDate, Timetable timetable) {
        this.subjects = subjects;
        this.lastCheckDate = checkDate;
        this.timetable = timetable;
    }
}