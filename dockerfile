FROM debian:9

RUN apt-get update && \
    apt-get install -y openjdk-8-jdk maven
