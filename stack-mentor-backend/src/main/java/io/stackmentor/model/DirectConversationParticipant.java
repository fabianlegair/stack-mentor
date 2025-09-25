package io.stackmentor.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "direct_conversation_participants")
@IdClass(DirectConversationParticipantId.class)
@Getter
@Setter
public class DirectConversationParticipant {
    // Model
    @Id
    @Column(name = "conversation_id", updatable = false, nullable = false)
    private UUID conversationId; // Foreign key to Conversation

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId; // Foreign key to User
}
