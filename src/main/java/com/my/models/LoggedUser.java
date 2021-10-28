package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Accessors (chain = true)
public class LoggedUser {
    @NonNull
    private Integer id;
    @NonNull
    private AuthenticationData authData;
    private boolean updateAuthDataNotified = false;

    @BsonIgnore
    public boolean is (Integer otherUserId) {
        return id.equals(otherUserId);
    }
}
