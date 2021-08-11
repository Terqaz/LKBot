package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    private long updateInterval = 12L * 3600 * 1000; // 12 часов

    private LoggedUser loggedUser;
    private List<Integer> users = new ArrayList<>();
    private List<Integer> loginWaitingUsers = new ArrayList<>();

    private int silentModeStart = 2; // Час ночи
    private int silentModeEnd = 6;   // Час ночи

    @BsonIgnore
    public boolean isNotLoggedNow () {
        return loggedUser == null;
    }

    @BsonIgnore
    public boolean isLoggedBefore () {
        return !(subjectsData.isEmpty() && users.isEmpty());
    }

    @BsonIgnore
    public Date getNextCheckDate () {
        return new Date(lastCheckDate.getTime() + updateInterval);
    }

}