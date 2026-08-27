# Android build environment for FoodTracker.
# Build once, use for compiling/assembling the APK, then discard — only the
# mounted source tree (this repo) persists on the host.
#
#   docker build -t foodtracker-build .
#   docker run --rm -it -v "$PWD":/workspace -w /workspace foodtracker-build gradle assembleDebug
#
# See README.md for the full workflow (including installing to a device via adb).

FROM eclipse-temurin:17-jdk-jammy

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle \
    GRADLE_VERSION=8.7 \
    ANDROID_CMDLINE_TOOLS_VERSION=11076708

ENV PATH=${GRADLE_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        unzip \
        git \
        android-tools-adb \
    && rm -rf /var/lib/apt/lists/*

# Gradle (installed directly — no need for a checked-in gradle-wrapper.jar binary)
RUN curl -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv /opt/gradle-${GRADLE_VERSION} ${GRADLE_HOME} \
    && rm /tmp/gradle.zip

# Android SDK command-line tools + the packages this project needs
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" > /dev/null

WORKDIR /workspace
