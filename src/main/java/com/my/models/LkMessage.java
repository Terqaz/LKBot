package com.my.models;

import lombok.*;

import java.time.LocalDateTime;

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
    LocalDateTime date;
    LkDocument document;
}
