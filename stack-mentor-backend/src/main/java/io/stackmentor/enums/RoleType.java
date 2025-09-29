package io.stackmentor.enums;

public enum RoleType {

    MENTOR("mentor"),
    MENTEE("mentee");

    private final String value;

    RoleType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
