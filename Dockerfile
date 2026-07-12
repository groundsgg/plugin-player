# syntax=docker/dockerfile:1
#
# Builds the plugin-player Velocity-plugin JAR and packages it for the
# platform-test environment. The output image carries the JAR at
# /jar/plugin.jar — the shape the `plugin-velocity-jar` Helm chart expects
# (oci://ghcr.io/groundsgg/charts/plugin-velocity-jar). Velocity proxy pods
# in a per-engineer vCluster fetch this image's JAR via the chart's
# init-container + httpd indirection.
#
# Pushed as `ghcr.io/groundsgg/plugin-player:edge` (main) / `:<semver>` (tag)
# by .github/workflows/docker-gradle-build-push.yml.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# GitHub Packages credentials for the gg.grounds.velocity convention plugin.
# The token comes from the `github_token` build secret (never a build-arg —
# that would leak it into the layer history).
ARG GITHUB_USER

# Copy gradle wrapper + root config first so dependency caches stay warm
# across source-only changes.
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./

COPY common/ common/
COPY velocity/ velocity/

# `:velocity:build` produces the shaded plugin JAR. plugin-proxy-api stays
# compileOnly (never shaded): the ProxyServiceRegistry this plugin writes its
# session lookup into must be the class plugin-proxy loaded at runtime.
RUN --mount=type=secret,id=github_token,required=true \
    /bin/sh -euc '\
      : "${GITHUB_USER:?GITHUB_USER build arg is required}"; \
      token="$(cat /run/secrets/github_token)"; \
      ./gradlew --no-daemon :velocity:build \
        -Pgithub.user="${GITHUB_USER}" \
        -Pgithub.token="${token}" \
    '

RUN mkdir -p /out && \
    cp "$(ls -S /src/velocity/build/libs/*.jar | head -n1)" /out/plugin.jar

FROM alpine:3
RUN mkdir -p /jar
COPY --from=build /out/plugin.jar /jar/plugin.jar
# No ENTRYPOINT — the plugin-velocity-jar chart's init-container `cp`s
# /jar/plugin.jar out, then the chart's main container (busybox httpd)
# serves the JAR. This image only carries data.
