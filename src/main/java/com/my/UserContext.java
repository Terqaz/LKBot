package com.my;

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
    int loginTries = 0;

    public void incrementLoginTries() {
        loginTries++;
    }
}
