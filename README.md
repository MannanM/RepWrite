# RepWrite

RepWrite is an open-source platform designed to empower citizens to communicate effectively with their political representatives. By leveraging AI, RepWrite helps users craft personalized, respectful, and compelling appeals tailored to specific political causes and the unique backgrounds of their Senators.

## What is RepWrite?
In a healthy democracy, your voice matters. But sometimes, it's hard to know how to start a conversation with your representative. Generic form letters often get ignored. RepWrite helps you bridge this gap.

### How it Works
1.  **Select a Cause**: Choose from a list of active political issues.
2.  **Find Your Senator**: Enter your postcode or select your state to find your representatives.
3.  **Personalize Your Message**: (Optional) Provide some context about why the issue matters to you, your background, or your occupation.
4.  **Generate**: Our AI crafts a unique email, a social media post (𝕏/Twitter), and a phone script just for you.
5.  **Take Action**: Use the generated content to contact your Senator directly.

---

## For Developers (Technical)

### Tech Stack
-   **Backend**: Kotlin, Spring Boot 3.x
-   **Frontend**: Thymeleaf, Vanilla CSS, Javascript
-   **Database**: DynamoDB (initialized via `DynamoDbInitializer`)
-   **AI Integration**: Google Gemini AI
-   **Infrastructure**: AWS (CloudFormation, EC2, Secrets Manager)

### Prerequisites
-   Java 21
-   Maven 3.9+
-   Docker (for running DynamoDB Local)

### Local Setup

1.  **Run DynamoDB Local**:
    The project includes a `docker-compose.yaml` to spin up DynamoDB Local.
    ```bash
    docker-compose up -d
    ```

2.  **Configure API Keys**:
    Ensure you have a Gemini API key. You can set it in `src/main/resources/application.properties` or as an environment variable `GEMINI_API_KEY`. The app also supports fetching keys from AWS Secrets Manager if configured.

3.  **Run the Application**:
    ```bash
    mvn spring-boot:run
    ```
    The application will be available at `http://localhost:8080`.

### Testing
To run the automated test suite:
```bash
mvn clean test -Dheadless.mode=true
```

### Infrastructure & Deployment
The project includes CloudFormation templates in `Scripts/CloudFormation/` for AWS deployment. The `deploy.sh` script automates the build and deployment process.
