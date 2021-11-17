package com.my.models.temp;

import com.my.models.GroupUser;
import com.my.models.LoggedUser;
import com.my.models.Timetable;
import com.my.models.UserToVerify;
import com.my.services.lk.LkParser;
import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@Accessors(chain = true)
public class OldGroup {

    @NonNull
    private String name;
    private String lkId;
    private String lkSemesterId;
    private String lkContingentId;
    private List<OldSubject> subjects = new ArrayList<>();
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
}
