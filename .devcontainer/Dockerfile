FROM mcr.microsoft.com/devcontainers/java:ubuntu-22.04

RUN apt-get update \
    && apt-get install -y maven chromium-browser chromium-chromedriver \
    && rm -rf /var/lib/apt/lists/*
