package io.stackmentor.repository;

import io.stackmentor.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {
    //Repository methods can be defined here if needed
}
