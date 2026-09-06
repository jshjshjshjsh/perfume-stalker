FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

WORKDIR /app

RUN apt-get update && apt-get install -y xvfb wget \
    && wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y ./google-chrome-stable_current_amd64.deb \
    && rm google-chrome-stable_current_amd64.deb \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "xvfb-run -a -e /dev/stdout -s '-screen 0 1920x1080x24' java -jar -Duser.timezone=Asia/Seoul app.jar"]