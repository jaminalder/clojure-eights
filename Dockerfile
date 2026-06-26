# syntax=docker/dockerfile:1

FROM clojure:temurin-21-tools-deps AS build
WORKDIR /app
COPY deps.edn build.clj ./
COPY src ./src
COPY resources ./resources
RUN clojure -T:build uber

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
COPY --from=build /app/target/crazy-eights.jar /app/crazy-eights.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/crazy-eights.jar"]
