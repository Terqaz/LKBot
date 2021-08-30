package com.my.models;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserToVerify {

    @NonNull
    Integer id;
    @NonNull
    Integer code;
}
