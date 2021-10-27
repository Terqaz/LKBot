package com.my.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Accessors(chain = true)
public class LkDocument {
    private Integer id;
    @NonNull
    private String name;
    private String fileName;
    @NonNull
    private String lkId;
}
