# TT_Coach devcontainer
#
# Debian-based devcontainer base image: ships a non-root "vscode" user (uid 1000),
# git, curl, sudo, zsh, etc. already configured via the baked-in common-utils feature.
# We layer on: JDK 21 (Temurin), Node 24, Python 3.13, the Android SDK cmdline-tools
# (platform 36 + build-tools 34.0.0, no adb/emulator — the phone stays on the host),
# and the firewall tooling that init-firewall.sh needs at container start.
#
# Gradle is deliberately NOT installed here: the repo's Gradle wrapper (9.3.1) pins
# AGP 9.1.1 / Kotlin 2.2.21 and downloads itself on first `./gradlew` invocation.
FROM mcr.microsoft.com/devcontainers/base:bookworm

ARG TZ
ENV TZ="${TZ:-UTC}"
ENV DEBIAN_FRONTEND=noninteractive

# Docker/buildkit auto-populated build arg: "amd64" or "arm64" (matches Temurin's
# package-name suffix), so this Dockerfile builds correctly on both Intel and
# Apple Silicon hosts.
ARG TARGETARCH

# ---------------------------------------------------------------------------
# Base OS packages: general dev tools + everything init-firewall.sh needs
# (iptables/ipset to build the egress allowlist, dnsutils to resolve domains,
# jq/curl to query the GitHub meta API, aggregate to collapse CIDR ranges).
# ---------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gnupg \
    sudo \
    git \
    unzip \
    jq \
    dnsutils \
    iptables \
    ipset \
    iproute2 \
    aggregate \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# JDK 21 (Temurin) — via the Adoptium apt repo
# ---------------------------------------------------------------------------
RUN mkdir -p /etc/apt/keyrings && \
    curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
      | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg && \
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" \
      > /etc/apt/sources.list.d/adoptium.list && \
    apt-get update && apt-get install -y --no-install-recommends temurin-21-jdk && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-${TARGETARCH}
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# ---------------------------------------------------------------------------
# Node 24 — via the NodeSource apt repo
# ---------------------------------------------------------------------------
RUN curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# Python 3.13 is NOT installed here: Debian bookworm apt only ships 3.11.
# It is provided instead by the devcontainers/features/python feature pinned to
# 3.13 in devcontainer.json (installs into the container post-build).
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Android SDK: cmdline-tools + platform 36 + build-tools 34.0.0.
# No adb / no emulator image — the connected phone stays on the host; this
# container only needs enough SDK to satisfy Gradle's compileSdk/AGP resolution.
# ---------------------------------------------------------------------------
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT="${ANDROID_HOME}"
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" && \
    curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" && \
    unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" && \
    mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" && \
    rm /tmp/cmdline-tools.zip

# `yes |` accepts the SDK license prompts non-interactively.
RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses > /dev/null && \
    sdkmanager --sdk_root="${ANDROID_HOME}" \
      "platform-tools" \
      "platforms;android-36" \
      "build-tools;34.0.0"

ARG USERNAME=vscode
RUN chown -R ${USERNAME}:${USERNAME} "${ANDROID_HOME}"

# ---------------------------------------------------------------------------
# Firewall init script.
#
# The base image's bundled common-utils feature already grants the vscode user
# broad passwordless sudo (/etc/sudoers.d/*). Wipe that and replace it with a
# single narrow NOPASSWD rule scoped to init-firewall.sh only, mirroring
# Anthropic's reference devcontainer's "sudo for firewall init only" posture.
# ---------------------------------------------------------------------------
COPY init-firewall.sh /usr/local/bin/init-firewall.sh
RUN chmod +x /usr/local/bin/init-firewall.sh && \
    rm -f /etc/sudoers.d/* && \
    echo "${USERNAME} ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" > /etc/sudoers.d/${USERNAME}-firewall && \
    chmod 0440 /etc/sudoers.d/${USERNAME}-firewall

USER ${USERNAME}
WORKDIR /workspace
