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
COPY ./cloud-archiver-0.9.9.002.jar /app/cloud-archiver.jar
COPY ./application.yml /app/applicaiton.yml

EXPOSE 8080

# Command to run the Java application
CMD ["java", "-jar", "-Dspring.config.location=./applicaiton.yml", "cloud-archiver.jar"]
