package io.stackmentor.enums;

import lombok.Getter;

@Getter
public enum GroupMemberType {

    ADMIN("admin"),
    MEMBER("member");

    private final String value;

    GroupMemberType(String value) {
        this.value = value;
    }
}