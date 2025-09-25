package io.stackmentor.enums;

public enum ConversationType {

    PRIVATE("private"),
    GROUP("group");

    private final String value;

    ConversationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
