package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class LkDocument {
    private Integer id;
    @NonNull
    private String name;
    @NonNull
    private String lkId;
    private String sender;
    private String vkAttachment;
    private Boolean isExtChanged = false;
}
