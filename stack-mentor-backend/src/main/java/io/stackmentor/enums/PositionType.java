package io.stackmentor.enums;

import lombok.Getter;

@Getter
public enum PositionType {

    ADMIN("admin"),
    MODERATOR("moderator"),
    MEMBER("member");

    private final String value;

    PositionType(String value) {this.value = value;}
}
