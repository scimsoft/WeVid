# Android build environment for WeVid (JDK 17 + Android SDK 35).
FROM eclipse-temurin:17-jdk-jammy

ARG ANDROID_SDK_VERSION=11076708
ARG ANDROID_COMPILE_SDK=35
ARG ANDROID_BUILD_TOOLS=35.0.0

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk

ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        wget \
        unzip \
        git \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip" -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager \
        "platform-tools" \
        "platforms;android-${ANDROID_COMPILE_SDK}" \
        "build-tools;${ANDROID_BUILD_TOOLS}"

WORKDIR /project

# Pre-create Gradle cache dirs for volume mounts.
RUN mkdir -p /home/android/.gradle && chmod -R 777 /home/android/.gradle

COPY docker/entrypoint.sh /usr/local/bin/wevid-entrypoint
RUN chmod +x /usr/local/bin/wevid-entrypoint

ENTRYPOINT ["wevid-entrypoint"]
CMD ["assembleDebug"]
