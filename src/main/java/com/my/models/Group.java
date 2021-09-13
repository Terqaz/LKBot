package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    private List<SubjectData> subjectsData = new ArrayList<>();
    private Date lastCheckDate;
    private long updateInterval = 12L * 3600 * 1000; // 12 часов

    private LoggedUser loggedUser;
    private List<GroupUser> users = new ArrayList<>();
    private List<UserToVerify> usersToVerify = new ArrayList<>();
    private List<Integer> loginWaitingUsers = new ArrayList<>();

    private Timetable timetable;

    private int silentModeStart = 2; // Час [0, 23]
    private int silentModeEnd = 6;   // Час [0, 23]

    @BsonIgnore
    public boolean isNotLoggedNow () {
        return loggedUser.getAuthData() == null;
    }

    @BsonIgnore
    public boolean isLoggedBefore () {
        return !(subjectsData.isEmpty() && users.isEmpty());
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
}