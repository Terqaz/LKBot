package com.my.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class LoggedUser {
    private Integer id;
    private String login;
    private String password;
    private boolean alwaysNotify = true;

    @BsonIgnore
    public boolean equals (Integer otherUserId) {
        return id.equals(otherUserId);
    }
}
