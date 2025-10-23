package io.stackmentor.dto;

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
public class GroupDto {

    private UUID groupId;
    private String groupName;
    private String description;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private String members;
}
