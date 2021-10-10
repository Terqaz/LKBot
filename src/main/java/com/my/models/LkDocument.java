package com.my.models;

import com.my.models.enums.LkDocumentDestination;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Accessors(chain = true)
public class LkDocument implements Serializable {
    private int id;
    @NonNull
    private String name;
    private String fileName;
    @NonNull
    private String lkId;
    @NonNull
    private LkDocumentDestination destination;
}
