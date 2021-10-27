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
    private String fileName;
    @NonNull
    private String lkId;

    public LkDocument(LkDocument that) {
        this(that.id, that.name, that.fileName, that.lkId);
    }
}
