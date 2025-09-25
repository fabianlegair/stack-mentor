package io.stackmentor.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_members")
@IdClass(GroupMemberId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember {
    // Model
    @Id
    @Column(name = "group_id", updatable = false, nullable = false)
    private UUID groupId;

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "role", length = 15) // e.g., "admin", "member"
    private String role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
}
