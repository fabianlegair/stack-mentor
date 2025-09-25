package io.stackmentor.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "message_read_status")
@IdClass(MessageReadStatusId.class)
@Getter
@Setter
public class MessageReadStatus {

    @Id
    @Column(name = "message_id", updatable = false, nullable = false)
    private UUID messageId; // Foreign key to Message

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId; // Foreign key to User

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt; // Timestamp when the message was read
}
