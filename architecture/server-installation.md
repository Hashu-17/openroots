# Server Installation Guide


### 1. Linux Server OS

### 2. Java Runtime

### 3. Spring Boot Application

Spring Boot itself is not installed as a separate server package. What you deploy is the built application artifact.

- Spring Boot executable JAR
- systemd service file for the application

### 4. ChirpStack Stack

- ChirpStack Gateway Bridge
- ChirpStack Network Server
- ChirpStack Application Server
- ChirpStack Join Server, if your setup requires it

### 5. MQTT Broker

- Eclipse Mosquitto
- or another MQTT broker compatible with ChirpStack

### 6. Redis

### 7. PostgreSQL

### 8. Git

### 9. Reverse Proxy

- Nginx

for:

- TLS termination
- clean public ports
- request forwarding
- future scaling

### 10. SSL Certificates

certificate tooling if the server will be exposed over HTTPS.

- Certbot

### 11. Docker

Install Docker only if you want to run the application and database in containers.

- Docker Engine
- Docker Compose
