package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors (chain = true)
@EqualsAndHashCode
public class GroupUser {
    @NonNull
    Integer id;
    boolean everydayScheduleEnabled = false;
}
