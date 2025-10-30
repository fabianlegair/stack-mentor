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
    @ManyToOne
    @JoinColumn(name = "message_id", updatable = false, nullable = false)
    private Message message; // Foreign key to Message

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    private User user; // Foreign key to User

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt; // Timestamp when the message was read
}
