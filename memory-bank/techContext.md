# Technical Context: Cloud Archiver

## Technologies Used

- **Backend:**
    - **Language:** Java 17+
    - **Framework:** Spring Boot 3.x
    - **Build Tool:** Maven
    - **Database:** MongoDB (via Spring Data MongoDB)
    - **Cloud SDKs:** Google Cloud Storage Client Library for Java (for GCP integration)
    - **Logging:** SLF4J with Logback
    - **Testing:** JUnit 5, Mockito

- **Containerization:**
    - Docker
    - Docker Compose (for local development setup)

- **Version Control:**
    - Git / GitHub

- **CI/CD (Future):**
    - Jenkins, GitHub Actions, or similar

- **Monitoring & Alerting (Future):**
    - Prometheus, Grafana, ELK Stack

## Development Setup

### Prerequisites

- Java Development Kit (JDK) 17 or higher
- Maven 3.x
- Docker Desktop (for running MongoDB locally and containerizing the application)
- Git

### Local Environment Setup

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/ringuerel/cloud-archiver.git
    cd cloud-archiver
    ```
2.  **Run MongoDB (via Docker Compose):**
    ```bash
    docker-compose -f docker/cloud-archiver.yml up -d mongodb
    ```
3.  **Build the Application:**
    ```bash
    mvn clean install
    ```
4.  **Run the Application:**
    ```bash
    mvn spring-boot:run
    ```
    Alternatively, run the JAR:
    ```bash
    java -jar target/cloud-archiver-0.0.1-SNAPSHOT.jar
    ```

### Configuration

- Application properties are managed in `src/main/resources/application.yml` and `application-dev.yml`.
- Cloud provider credentials (e.g., GCP service account key) should be configured securely, preferably via environment variables or Spring Cloud Config.

## Technical Constraints

- **File Size Limits:** Cloud providers have limits on single file upload sizes. Large files may require chunking and resumable upload strategies.
- **API Rate Limits:** Cloud APIs impose rate limits. The application must implement appropriate backoff and retry mechanisms.
- **Network Latency:** Archiving performance is highly dependent on network bandwidth and latency.
- **Security:** Sensitive data (credentials, file content) must be handled securely (encryption at rest and in transit).
- **Scalability:** The system should be designed to handle an increasing number of files and users without significant performance degradation.

## Dependencies

Key dependencies are managed in `pom.xml`:

- `spring-boot-starter-data-mongodb`: For MongoDB integration.
- `spring-boot-starter-web`: For RESTful API capabilities.
- `google-cloud-storage`: For GCP integration.
- `spring-boot-starter-test`: For testing.
- `lombok`: For boilerplate code reduction.

## Tool Usage Patterns

- **Maven:** Used for building, testing, and packaging the application.
- **Docker:** Used for creating isolated development environments and deploying the application.
- **IDE (e.g., IntelliJ IDEA, VS Code):** For code development, debugging, and project management.
