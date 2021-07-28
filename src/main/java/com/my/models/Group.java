package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.*;

@Getter
@Setter
@RequiredArgsConstructor
@NoArgsConstructor
@Accessors (chain = true)
public class Group {
    @NonNull
    private String name;
    private List<SubjectData> subjectsData = new ArrayList<>();
    private Date lastCheckDate;
    private long updateInterval = 12L * 3600 * 1000;

    private Integer loggedUserId;
    private String login;
    private String password;
    private boolean alwaysNotifyLoggedUser = true;
    private List<Integer> users = new ArrayList<>();
    private Set<Integer> loginWaitingUsers = new HashSet<>();

    @BsonIgnore
    public boolean userIsLogged (Integer userId) {
        return Objects.equals(loggedUserId, userId);
    }

    @BsonIgnore
    public boolean isLoggedBefore () {
        return !(subjectsData.isEmpty() && users.isEmpty());
    }

    @BsonIgnore
    public boolean isNotLoggedNow () {
        return loggedUserId == null;
    }

    @BsonIgnore
    public Date getNextCheckDate () {
        return new Date(lastCheckDate.getTime() + updateInterval);
    }

}