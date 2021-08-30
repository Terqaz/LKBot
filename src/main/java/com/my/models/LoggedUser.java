package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Accessors (chain = true)
public class LoggedUser {
    private Integer id;
    private AuthenticationData authData;
    private boolean alwaysNotify = true;
    private boolean updateAuthDataNotified = false;

    @BsonIgnore
    public boolean is (Integer otherUserId) {
        return id.equals(otherUserId);
    }
}
