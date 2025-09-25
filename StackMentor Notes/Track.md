
Models Defined
	Models
		Users
		Groups
		Group members
		Messages
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
	|	|	|	|	----- controller
	|	|	|	|	----- model
	|	|	|	|	----- repository
	|	|	|	|	----- service	
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
	|	|	|	|	|
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
