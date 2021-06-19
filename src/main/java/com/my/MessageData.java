package com.my;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class MessageData {
    @NonNull
    String comment;
    @NonNull
    String sender;
    @NonNull
    Date date;
}
