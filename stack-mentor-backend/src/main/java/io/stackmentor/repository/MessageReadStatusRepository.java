package io.stackmentor.repository;

import io.stackmentor.model.MessageReadStatus;
import io.stackmentor.model.MessageReadStatusId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, MessageReadStatusId> {
    //Repository methods can be defined here if needed
    boolean existsByMessage_MessageIdAndUser_UserId(UUID messageId, UUID userId);

    @Query("SELECT mrs.message.messageId FROM MessageReadStatus mrs " +
            "WHERE mrs.user.userId = :userId AND mrs.message.messageId IN :messageIds")
    Set<UUID> findReadMessageIdsByUserIdAndMessageIdIn(@Param("userId") UUID userId,
                                                       @Param("messageIds") Set<UUID> messageIds);
}
