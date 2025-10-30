package io.stackmentor.dto.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDto {

    private UUID groupId;
    private String groupName;
    private String description;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private List<GroupMemberDto> members;
}
