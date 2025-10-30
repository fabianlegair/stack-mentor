package io.stackmentor.service;

import io.stackmentor.dto.group.GroupDto;
import io.stackmentor.dto.group.GroupMemberDto;
import io.stackmentor.enums.GroupMemberType;
import io.stackmentor.model.Group;
import io.stackmentor.model.GroupMember;
import io.stackmentor.model.User;
import io.stackmentor.repository.GroupMemberRepository;
import io.stackmentor.repository.GroupRepository;
import io.stackmentor.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class GroupService {


    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private UserRepository userRepository;


    @Transactional
    public GroupDto createGroup(GroupDto dto,
                                User creatingUser) {

        // Create entity
        Group group = new Group();
        group.setGroupName(dto.getGroupName());
        group.setDescription(dto.getDescription());
        group.setCreatedBy(creatingUser.getUserId());
        group.setCreatedAt(LocalDateTime.now());

        groupRepository.save(group);

        // Add creator as admin
        GroupMember creator = GroupMember.builder()
                .group(group)
                .user(creatingUser)
                .role(GroupMemberType.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.save(creator);

        return buildGroupDto(group);
    }

    @Transactional
    public GroupDto addUserToGroup(UUID groupId, UUID userId,
                                   GroupMemberType role) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (groupMemberRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId)) {
            throw new RuntimeException("User already in group");
        }

        GroupMember groupMember = GroupMember.builder()
                .group(group)
                .user(user)
                .role(role != null ? role : GroupMemberType.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.save(groupMember);

        return buildGroupDto(group);
    }

    @Transactional
    public GroupDto removeUserFromGroup(UUID groupId, UUID userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!groupMemberRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId)) {
            throw new RuntimeException("User not in group");
        }

        groupMemberRepository.deleteByGroup_GroupIdAndUser_UserId(groupId, userId);
        return buildGroupDto(group);
    }

    public GroupDto getGroupWithMembers(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        return buildGroupDto(group);
    }

    private GroupDto buildGroupDto(Group group) {

        List<GroupMemberDto> members = groupMemberRepository.findByGroup_GroupId(group.getGroupId())
                .stream()
                .map(groupMember -> GroupMemberDto.builder()
                        .userId(groupMember.getUser().getUserId())
                        .name(groupMember.getUser().getFirstName()
                                + " " + groupMember.getUser().getLastName())
                        .role(groupMember.getRole())
                        .joinedAt(groupMember.getJoinedAt())
                        .build())
                .toList();

        return GroupDto.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .description(group.getDescription())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .members(members)
                .build();
    }
}
