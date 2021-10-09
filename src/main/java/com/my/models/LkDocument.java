package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;

import java.net.URL;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode
public class LkDocument {
    @NonNull
    private String name;
    @NonNull
    private URL url;
}
