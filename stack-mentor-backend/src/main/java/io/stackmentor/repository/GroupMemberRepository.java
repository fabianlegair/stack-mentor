package io.stackmentor.repository;

import io.stackmentor.model.GroupMember;
import io.stackmentor.model.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    //Repository methods can be defined here if needed
    List<GroupMember> findByGroup_GroupId(UUID groupId);
    void deleteByGroup_GroupIdAndUser_UserId(UUID groupId, UUID userId);
    boolean existsByGroup_GroupIdAndUser_UserId(UUID groupId, UUID userId);
}
