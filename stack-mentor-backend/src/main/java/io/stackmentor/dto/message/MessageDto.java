package io.stackmentor.dto.message;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {

    private UUID messageId;
    private UUID conversationId;
    private UUID senderId;
    private String senderName;  // Useful for display
    private String content;
    private String mediaUrl;
    private LocalDateTime sentAt;
    private LocalDateTime editedAt;
    private boolean isDeleted;
    private LocalDateTime deletedAt;
    private boolean isRead;  // From MessageReadStatus
}
