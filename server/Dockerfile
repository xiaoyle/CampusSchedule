FROM gradle:8.11.1-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/build/install/campus-schedule-community/ /app/
USER appuser
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/campus-schedule-community"]
