package com.my.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoggedUser {
    private Integer id;
    private AuthenticationData authData;
    private boolean alwaysNotify = true;

    @BsonIgnore
    public boolean equals (Integer otherUserId) {
        return id.equals(otherUserId);
    }
}
