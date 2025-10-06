[ React Native App ]
        |
        v
[ Route 53 DNS ]
        |
        v
[ API Gateway / Load Balancer ]
        |
        v
[ Spring Boot Backend (ECS/EKS/Beanstalk) ]
        |                            \
        |                              \
        v                              v
[ RDS PostgreSQL ]      [ Redis (Pub/Sub, Cache) ]

Media & Files:
[ React Native App ] <--> [ S3 Bucket ] <--> [ CloudFront CDN ]

Authentication:
[ React Native App ] <--> [ Cognito ] OR [ Spring Security + JWT ]

Messaging:
[ App ] <--> [ Backend WebSocket ] <--> [ Redis Pub/Sub ] <--> [ PostgreSQL ]

Video Chat:
[ App ] <--> [ AWS Chime SDK ] <--> [ Chime Media Servers ]

Monitoring:
[ CloudWatch ] <-- Logs & Metrics from Backend, DB, Infra

**Component Relationships**
[ React Native App ]
        |
        v
[ API Gateway / Load Balancer ]
        |
        v
[ Spring Boot Backend (Docker on ECS/EKS) ]
        |                            \
        |                              \
        v                              v
[ RDS PostgreSQL ]      [ Redis (Pub/Sub, Cache) ]

Storage:
[ Backend ] <--> [ S3 Bucket ] <--> [ CloudFront CDN ]

Authentication:
[ App ] <--> [ Cognito ] OR [ Spring Security + JWT ]

Video Chat:
[ App ] <--> [ AWS Chime SDK ] <--> [ Chime Media Servers ]

Monitoring:
[ CloudWatch ] <-- Receives logs/metrics from Backend, DB, Infra


**Flow Summary**
- App talks to backend through **API Gateway/Load Balancer**.
    
- Backend runs in containers (ECS/EKS) and communicates with **PostgreSQL (RDS)**.
    
- **Redis** supports fast messaging and WebSocket scaling.
    
- **S3 + CloudFront** handle file storage and distribution.
    
- **Cognito** (or custom JWT) provides secure login.
    
- **Chime SDK** powers P2P and group video calls.
    
- **Route 53** routes the domain to AWS services.
    
- **CloudWatch** handles monitoring and alerting.
