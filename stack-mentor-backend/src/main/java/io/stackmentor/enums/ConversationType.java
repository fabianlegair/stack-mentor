package io.stackmentor.enums;

import lombok.Getter;

@Getter
public enum ConversationType {

    PRIVATE("private"),
    GROUP("group");

    private final String value;

    ConversationType(String value) {
        this.value = value;
    }
}
