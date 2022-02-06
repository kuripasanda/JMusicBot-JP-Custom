#　JMusicBot JP Docker container configuration file
#  Maintained by CyberRex (CyberRex0)

FROM openjdk:11-buster

# change the version if new release is appeared
ENV JMUSICBOT_VERSION=0.6.5

# DO NOT EDIT UNDER THIS LINE
RUN mkdir -p /opt/jmusicbot

WORKDIR /opt/jmusicbot

RUN \
    apt update && \
    apt install -y ffmpeg wget && \
    wget https://github.com/Cosgy-Dev/JMusicBot-JP/releases/download/$JMUSICBOT_VERSION/JMusicBot-$JMUSICBOT_VERSION.jar -O jmusicbot.jar && \
    echo "cd /opt/jmusicbot && java -Dnogui=true -jar jmusicbot.jar" > /opt/jmusicbot/execute.bash

CMD ["bash", "/opt/jmusicbot/execute.bash"]