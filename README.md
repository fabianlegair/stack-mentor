# Stack Mentor 

---
This project is called Stack Mentor, 
an application for developers to share knowledge 
with the less experienced, and for 
developers of all levels to communicate and seek 
guidance.

Here's a preview of the Login/Signup Flow:

![Stack Mentor Mock-Up Image](https://github.com/fabianlegair/stack-mentor/blob/main/demo-images/StackMentor-Mockup.png?raw=true)

## How It's Made:

---
### Mock-Ups
I used Figma do brainstorm a design before the 
actual development process. As this is my first 
full-stack application, this part of the process was 
fundamental for me. It allowed me to imagine the 
user flow of the application and define the 
models/components I would need to make the application.

### Frontend

For the frontend I wanted to minimalize the amount of 
time spent coding, so I chose **React Native** as my 
codebase. I chose React Native for the:
- Single codebase for iOS + Android compatibility
- JavaScript ecosystem (npm packages, large userbase)
- Performance speed

### Backend

For the backend I chose **Java Spring Boot**, mostly 
because the language I'm most comfortable with, 
but also because it offers:
- A mature ecosystem (years of documentation, large userbase)
- Extensive libraries
- Strong typing catches bugs at compile time
- WebSocket support
- JVM Performance
- Single codebase
- Lombok to reduce boilerplate

### Build Tool: Gradle + Kotlin

- Flexible
- Incremental compilation
- Dependency management (Maven Central)
- Kotlin DSL (catches typos, autocomplete)

### Database
For my database I chose **PostgreSQL**, again, mostly 
because it's the querying language I'm most 
comfortable with. It's JSON support and powerful 
query optimization also made it a complete 
no-brainer for me.

## Technologies

---
**Docker**: Package the application and its dependencies,

**Liquibase**: Database migration, Version controlled 
schema, Rollback support, Tracks applied changesets, 
Integrates with Spring Boot.

**AWS ECS**: Container management, Auto-scaling built 
in, integrates with CloudWatch

**AWS RDS**: Automation.

**AWS Cognito**: Managed user pool, OAuth/SAML federation
(Google/ Facebook login), JWT token issuance, 
enforceable password policies. Automatic scaling also 
a huge plus.

**AWS S3**: For object storage. Also allows for 
versioning.

## Optimizations:

---
WIP

## Lessons learned:

---
WIP

## My Foundational Principles

---
**Test-driven development**: One of the first things 
I learned in my bootcamp was that writing tests as 
you code is the best practice. It prevents regression 
and enables refactoring.

**Infrastructure as code**: Using CDK for reproducible 
environments and version control.

**Observability first**: Structured logs + detailed 
comments = Faster debugging.

**Cost-conscious engineering**: I am a one-man team, 
and I am poor.