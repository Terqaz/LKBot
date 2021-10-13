package com.my.models;

import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
public class LkMessage {
    @NonNull
    String comment;
    @NonNull
    String sender;
    @NonNull
    Date date;
    LkDocument document;
}
