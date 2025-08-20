1. Current Work:
   The task was to create a GitHub Actions workflow to build a Java project, create a Docker image, and push it to Docker Hub on commits to the `develop` branch.

2. Key Technical Concepts:
   - GitHub Actions: Workflow automation for CI/CD.
   - Docker: Containerization technology.
   - Docker Hub: Cloud-based registry service for Docker images.
   - Maven: Build automation tool for Java projects.
   - `pom.xml`: Maven project configuration file, used to extract project version.
   - `Dockerfile`: Instructions for building a Docker image.
   - GitHub Secrets: Securely store sensitive information like Docker Hub credentials.
   - Build Context: The set of files at a specified path that are sent to the Docker daemon during a build.

3. Relevant Files and Code:
   - `.github/workflows/build-and-push-docker.yml`:
     ```yaml
     name: Build and Push Docker Image

     on:
       push:
         branches:
           - develop

     jobs:
       build-and-push:
         runs-on: ubuntu-latest
         steps:
           - name: Checkout code
             uses: actions/checkout@v4

           - name: Set up JDK 17
             uses: actions/setup-java@v4
             with:
               java-version: '17'
               distribution: 'temurin'
               cache: maven

           - name: Build with Maven
             run: mvn -B package --file pom.xml

           - name: Extract project version
             id: get_version
             run: echo "VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)" >> $GITHUB_OUTPUT

           - name: Rename JAR for Docker
             run: mv target/cloud-archiver-${{ steps.get_version.outputs.VERSION }}.jar target/app.jar

           - name: Copy JAR to Docker build context
             run: cp target/app.jar src/main/docker/app.jar

           - name: Log in to Docker Hub
             uses: docker/login-action@v3
             with:
               username: ${{ secrets.DOCKERHUB_USERNAME }}
               password: ${{ secrets.DOCKERHUB_TOKEN }}

           - name: Build and push Docker image
             uses: docker/build-push-action@v5
             with:
               context: src/main/docker
               file: src/main/docker/Dockerfile
               push: true
               tags: ${{ secrets.DOCKERHUB_USERNAME }}/cloud-archiver:${{ steps.get_version.outputs.VERSION }}
     ```
   - `src/main/docker/Dockerfile`:
     ```dockerfile
     # Use Alpine Linux as base image
     FROM alpine:latest

     # Set environment variables for Java
     ENV JAVA_HOME=/usr/lib/jvm/default-jvm
     ENV PATH=$PATH:/usr/lib/jvm/default-jvm/bin

     # Install OpenJDK 17 and other necessary packages
     RUN apk add --no-cache openjdk17

     # Confirm Java installation
     RUN java -version

     # Create a new user
     RUN adduser -D cloud-archiver

     # Switch to the new user
     USER cloud-archiver

     # Set working directory
     WORKDIR /app

     RUN chown -R cloud-archiver:cloud-archiver /app

     # Copy your Java application JAR file into the container
     COPY app.jar /app/app.jar
     COPY ./application.yml /app/applicaiton.yml

     EXPOSE 8080

     # Command to run the Java application
     CMD ["java", "-jar", "-Dspring.config.location=./applicaiton.yml", "app.jar"]
     ```
   - `pom.xml`: Used to determine the project version and confirm it's a Maven project.

4. Problem Solving:
   - **Initial JAR not found error**: The Dockerfile was looking for a specific JAR name that included the version, which caused a "file not found" error. This was resolved by:
     - Modifying the `Dockerfile` to expect a generic `app.jar`.
     - Adding a step in the GitHub Actions workflow to rename the built JAR file to `app.jar` before the Docker image build.
   - **Incorrect secret names**: The user corrected the secret names to `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`.
   - **JAR not in Docker build context**: The `mv: cannot stat 'target/cloud-archiver-.jar': No such file or directory` error indicated that the `mv` command was executed before the `get_version` step, so the `VERSION` variable was not available. This was resolved by reordering the steps in the workflow to ensure `Extract project version` runs before `Rename JAR for Docker`.
   - **`app.jar` not found during image build**: The `"/app.jar": not found` error indicated that the `Dockerfile` could not find `app.jar` because it was not in the Docker build context. This was resolved by:
     - Adding a `Copy JAR to Docker build context` step in the workflow to copy `target/app.jar` into `src/main/docker/`.
     - Setting the `context` for the `docker/build-push-action` to `src/main/docker`.

5. Pending Tasks and Next Steps:
   The user has requested to "update memory bank". This task is now complete.
