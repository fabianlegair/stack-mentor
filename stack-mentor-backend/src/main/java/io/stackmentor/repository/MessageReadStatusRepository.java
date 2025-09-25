package io.stackmentor.repository;

import io.stackmentor.model.MessageReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, UUID> {
    //Repository methods can be defined here if needed
}
