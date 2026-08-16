FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system --gid 10001 storyweaver \
    && useradd --system --uid 10001 --gid 10001 --no-create-home storyweaver \
    && mkdir -p /data/imports \
    && chown 10001:10001 /data/imports

COPY --from=builder --chown=10001:10001 /workspace/target/storyweaver-backend-*.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
