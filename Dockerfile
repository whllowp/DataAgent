# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS ui
WORKDIR /src
COPY studio/package.json studio/package-lock.json ./studio/
RUN cd studio && npm ci --no-audit --no-fund
COPY studio ./studio
COPY dsh-plugin/icon.svg ./dsh-plugin/icon.svg
RUN cd studio && npm run build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml ./backend/pom.xml
RUN cd backend && mvn -B -DskipTests dependency:go-offline
COPY backend/src ./backend/src
COPY --from=ui /src/backend/src/main/resources/static/studio ./backend/src/main/resources/static/studio
RUN cd backend && mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home dataagent
COPY --from=build /src/backend/target/dataagent-cloud-0.1.0.jar /app/dataagent-cloud.jar
RUN mkdir -p /app/data && chown -R dataagent:dataagent /app
USER dataagent
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/dataagent-cloud.jar"]
