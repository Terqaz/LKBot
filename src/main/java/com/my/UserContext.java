package com.my;

import com.my.exceptions.UserTriesLimitExhaustedException;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UserContext {
    @NonNull
    Integer userId;
    @NonNull
    String groupName;
    int triesCount = 0;

    public void incrementTriesCount () {
        triesCount++;
        if (triesCount > 4) {
            throw new UserTriesLimitExhaustedException("User with id:" + userId + "has a problem");
        }
    }
}
