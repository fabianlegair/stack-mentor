package io.stackmentor.repository;

import io.stackmentor.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    //Repository methods can be defined here if needed
    List<Message> findByConversation_ConversationIdIn(List<UUID> conversationIds);
}
