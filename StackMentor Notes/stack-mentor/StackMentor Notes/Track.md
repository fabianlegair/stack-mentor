
Models Defined
	Populated with temp. database
	

Built out main skeleton
	src
	|
	----- main
	|	|
	|	----- java
	|	|	|
	|	|	----- io
	|	|	|	|
	|	|	|	----- stackmentor
	|	|	|	|   ----- config
	|	|	|	|	----- controller
	|	|	|	|   ----- dto
	|	|	|	|   ----- enums
	|	|	|	|   |   ----- ConversationType.java
	|	|	|	|   |   ----- RoleType.java
	|	|	|	|   ----- exception
	|	|	|	|	----- model
	|	|	|	|   |   ----- Conversation.java
	|	|	|	|   |   ----- DirectConversationParticipant.java
	|	|	|	|   |   ----- DirectConversationParticipantId.java
	|	|	|	|   |   ----- Group.java
	|	|	|	|   |   ----- GroupMember.java
	|	|	|	|   |   ----- GroupMemberId.java
	|	|	|	|   |   ----- Message.java
	|	|	|	|   |   ----- MessageReadStatus.java
	|	|	|	|   |   ----- MessageReadStatusId.java
	|	|	|	|   |   ----- User.java
	|	|	|	|   |   ----- VerificationToken.java
	|	|	|	|	----- repository
	|	|	|	|   |   ----- specs
	|	|	|	|   |   |   ----- UserSpecifications.java
	|	|	|	|   |   ----- ConversationRepository.java
	|	|	|	|   |   ----- GroupMemberRepository.java
	|	|	|	|   |   ----- GroupRepository.java
	|	|	|	|   |   ----- MessageReadStatusRepository.java
	|	|	|	|   |   ----- MessageRepository.java
	|	|	|	|   |   ----- UserRepository.java
	|	|	|	|   |   ----- VerificationTokenRepository.java
	|	|	|	|	----- service	
	|	|	|	|   |   ----- UserService.java
	|	|	|	|   |   ----- EmailService.java
	|	|	|	|   ----- util
	|	|	|	|	----- StackMentorBackendApplication	
	|	
	|	----- resources
	|	|	|
	|	|	----- db
	|	|	|	|
	|	|	|	----- changelog
	|	|	|	|	|
	|	|	|	|	----- changes
	|	|	|	|	|	----- 001-init.xml
	|	|	|	|	|	----- 002-insert-users.xml
	|	|	|	|	|	----- 003-insert-groups.xml	
	|	|	|	|	|	----- 004-insert-group-member.xml	
	|	|	|	|	|	----- 005-insert-messages.xml
	|	|	|	|   |   ----- 006-insert-direct-conversation-participants.xml
	|	|	|	|   |   ----- 007-insert-messages.xml
	|	|	|	|   |   ----- 008-insert-message-read-status.xml
	|	|	----- application.yml
	----- test
	|	|
	|	----- java
	|	|	----- io
	|	|	|	----- stackmentor
	|	|	|	|	----- DemoTest
	|	|	|	|	----- StackMentorBackendApplicationTests
	|	|	----- resources
	build.gradle.kts
	docker-compose.yml

Got the database to run in the docker container
Got Spring to run in the container

Completed JPA Entities and Repositories 
	Model
		Conversation
		DirectConversationParticipant
		DirectConversationParticipantId
		Group
		GroupMember
		GroupMemberId
		Message
		MessageReadStatus
		MessageReadStatusId
		User
		VerificationToken
	Repository
		ConversationRepository
		GroupMemberRepository
		GroupRepository
		MessageReadStatusRepository
		MessageRepository
		UserRepository
		VerificationTokenRepository

Completed logic for email verification to save user to db
	Completed EmailService and VerificationToken
Implemented search functions in UserService
Implemented code for UserSpecifications for search filters
