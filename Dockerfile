FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt

ENV PORT=8080
EXPOSE 8080

COPY --from=build /workspace/target/*.jar /opt/app.jar
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/app.jar"]
