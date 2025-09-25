package io.stackmentor.model;

import io.stackmentor.enums.ConversationType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {
    // Model
    // Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "conversation_id", updatable = false, nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10) // e.g., "private", "group")
    private ConversationType conversationType;

    @Column(name = "group_id")
    private UUID groupId; // Nullable, foreign key to Group if type is "group"

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
