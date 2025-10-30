package io.stackmentor.service;

import io.stackmentor.dto.message.MessageDto;
import io.stackmentor.model.Message;
import io.stackmentor.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageReadStatusRepository messageReadStatusRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    SimpMessagingTemplate messagingTemplate;


    private MessageDto convertToDto(Message message, boolean isRead) {
        return MessageDto.builder()
                .messageId(message.getMessageId())
                .senderId(message.getSender().getUserId())
                .senderName(message.getSender().getFirstName()
                        + " " + message.getSender().getLastName())
                .content(message.getContent())
                .mediaUrl(String.join(", ", message.getMediaUrl()))
                .sentAt(message.getSentAt())
                .editedAt(message.getEditedAt())
                .deletedAt(message.getDeletedAt())
                .isRead(isRead)
                .build();
    }


}
