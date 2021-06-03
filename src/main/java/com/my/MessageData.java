package com.my;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@RequiredArgsConstructor
public class MessageData {
    @NonNull
    String comment;
    @NonNull
    String sender;
    @NonNull
    Date date;


}
