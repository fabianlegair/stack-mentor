package io.stackmentor.enums;

import lombok.Getter;

@Getter
public enum RoleType {

    MENTOR("mentor"),
    MENTEE("mentee");

    private final String value;

    RoleType(String value) { this.value = value;}
}
