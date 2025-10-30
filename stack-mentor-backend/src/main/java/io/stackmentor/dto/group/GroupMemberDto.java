package io.stackmentor.dto.group;

import io.stackmentor.enums.GroupMemberType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMemberDto {

    private UUID userId;
    private String name;
    private GroupMemberType role;
    private LocalDateTime joinedAt;
}
