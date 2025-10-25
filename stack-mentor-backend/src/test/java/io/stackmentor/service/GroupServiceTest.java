package io.stackmentor.service;

import io.stackmentor.dto.GroupDto;
import io.stackmentor.enums.GroupMemberType;
import io.stackmentor.model.Group;
import io.stackmentor.model.GroupMember;
import io.stackmentor.model.User;
import io.stackmentor.repository.GroupMemberRepository;
import io.stackmentor.repository.GroupRepository;
import io.stackmentor.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    void createGroup_createsGroupSucessfully() {

        // Arrange
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);
        user.setFirstName("Master");
        user.setLastName("Admin");

        GroupDto dto = new GroupDto();
        dto.setGroupName("Stack Mentors");
        dto.setDescription("A group.");

        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setGroupId(groupId);
            return group;
        });

        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        when(groupMemberRepository.findByGroup_GroupId(groupId)).thenAnswer(invocation -> {
            GroupMember creator = GroupMember.builder()
                    .user(user)
                    .role(GroupMemberType.ADMIN)
                    .joinedAt(LocalDateTime.now())
                    .build();
            return List.of(creator);
        });

        // Act
        GroupDto result = groupService.createGroup(dto, user);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals("Stack Mentors", result.getGroupName());
        assertEquals("A group.", result.getDescription());
        assertEquals(userId, result.getCreatedBy());
        assertEquals(1, result.getMembers().size());
        assertEquals("Master Admin", result.getMembers().get(0).getName());

        verify(groupRepository).save(any(Group.class));
        verify(groupMemberRepository).save(any(GroupMember.class));
        verify(groupMemberRepository).findByGroup_GroupId(groupId);
    }

    @Test
    void addUserToGroup_addsUserToGroupSuccessfully() {

        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        // Arrange
        User user = new User();
        user.setUserId(userId);
        user.setFirstName("Lesser");
        user.setLastName("Admin");

        Group group = new Group();
        group.setGroupId(groupId);

        // Mock GroupMember to be returned after save
        GroupMember newMember = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMemberType.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(groupMemberRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId)).thenReturn(false);
        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(newMember);
        when(groupMemberRepository.findByGroup_GroupId(groupId)).thenReturn(List.of(newMember));


        // Act
        GroupDto result = groupService.addUserToGroup(groupId, userId,
                GroupMemberType.MEMBER);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals(1, result.getMembers().size());
        assertEquals(userId, result.getMembers().get(0).getUserId());
        assertEquals(GroupMemberType.MEMBER, result.getMembers().get(0).getRole());

        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    @Test
    void removeUserFromGroup_removesUserFromGroupSuccessfully() {
        // Arrange
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Group group = new Group();
        group.setGroupId(groupId);
        group.setGroupName("Stack Mentors");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId)).thenReturn(true);
        when(groupMemberRepository.findByGroup_GroupId(groupId)).thenReturn(List.of());

        // Act
        GroupDto result = groupService.removeUserFromGroup(groupId, userId);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals(0, result.getMembers().size());

        verify(groupMemberRepository).deleteByGroup_GroupIdAndUser_UserId(groupId, userId);
    }

    @Test
    void getGroupWithMembers_getsGroupSuccessfully() {
        // Arrange
        UUID groupId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Group group = new Group();
        group.setGroupId(groupId);
        group.setGroupName("Stack Mentors");
        group.setDescription("A group.");
        group.setCreatedBy(userId1);
        group.setCreatedAt(LocalDateTime.now());

        User user1 = new User();
        user1.setUserId(userId1);

        User user2 = new User();
        user2.setUserId(userId2);

        GroupMember member1 = GroupMember.builder()
                .group(group)
                .user(user1)
                .role(GroupMemberType.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();

        GroupMember member2 = GroupMember.builder()
                .group(group)
                .user(user2)
                .role(GroupMemberType.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_GroupId(groupId)).thenReturn(List.of(member1, member2));

        // Act
        GroupDto result = groupService.getGroupWithMembers(groupId);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals("Stack Mentors", result.getGroupName());
        assertEquals(2, result.getMembers().size());
        assertEquals(GroupMemberType.ADMIN, result.getMembers().get(0).getRole());
        assertEquals(GroupMemberType.MEMBER, result.getMembers().get(1).getRole());

        verify(groupRepository).findById(groupId);
        verify(groupMemberRepository).findByGroup_GroupId(groupId);
    }
}
