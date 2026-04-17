FROM gradle:8.8-jdk21
WORKDIR /app
COPY . .
RUN gradle clean bootJar -x test
RUN cp build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
