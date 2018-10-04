FROM debian:9

RUN groupadd --gid 113 jenkins && \
    useradd --uid 109 --gid 113 jenkins

RUN apt-get update && \
    apt-get install -y git openjdk-8-jdk maven

RUN mkdir -p /var/maven && \
    chmod -R 775 /var/maven && \
    chown -R jenkins:jenkins /var/maven

RUN mkdir -p /var/maven/.m2 && \
    chmod -R 775 /var/maven/.m2 && \
    chown -R jenkins:jenkins /var/maven/.m2

RUN echo "<settings><localRepository>/var/maven/.m2</localRepository></settings>" > /settings.xml && \
    chmod 755 /settings.xml
