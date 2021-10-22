package com.my.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class Message {
    @NonNull
    String comment;
    @NonNull
    String sender;
    @NonNull
    Date date;
}
