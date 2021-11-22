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
    private String lkId;
    private String sender;

    private String vkAttachment;
    private Boolean isExtChanged; // null значит false

    private String url; // если прикреплен url, то vkAttachment, isExtChanged == null
                        // а lkId = md5(url)[:10]

    public LkDocument(@NonNull String name, @NonNull String lkId) {
        this.name = name;
        this.lkId = lkId;
    }

    public LkDocument(@NonNull String name, @NonNull String lkId, @NonNull String url) {
        this.name = name;
        this.lkId = lkId;
        this.url = url;
    }
}
