# Docker 镜像构建
FROM openjdk:8-jre-slim

# Copy local code to the container image.
WORKDIR /app
COPY ./wobi-backend-0.0.1-SNAPSHOT.jar /app/wobi-backend-0.0.1-SNAPSHOT.jar
COPY ./avatar /app/avatars

# 暴露应用端口
EXPOSE 8101

# Run the web service on container startup.
CMD ["java","-jar","/app/wobi-backend-0.0.1-SNAPSHOT.jar"]