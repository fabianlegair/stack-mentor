package io.stackmentor.service;

import io.stackmentor.dto.CreateGroupDto;
import io.stackmentor.dto.GroupDto;
import io.stackmentor.model.Group;
import io.stackmentor.model.User;
import io.stackmentor.repository.GroupRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public GroupDto createGroup(CreateGroupDto dto,
                                User creatingUser) {

        // Create entity
        Group group = new Group();
        group.setGroupName(dto.getGroupName());
        group.setDescription(dto.getDescription());
        group.setCreatedBy(creatingUser.getUserId());
        group.setCreatedAt(LocalDateTime.now());
        group.setMembers(String.join(", ", dto.getMembers()));

        groupRepository.save(group);

        return GroupDto.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .description(group.getDescription())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .members(group.getMembers())
                .build();
    }

//    public GroupDto addUserToGroup(Group group, User addedUser) {
//
//    }
}
