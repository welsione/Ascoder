FROM eclipse-temurin:17-jre
WORKDIR /app

# Install git, curl, ca-certificates first
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl ca-certificates gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" \
       > /etc/apt/sources.list.d/nodesource.list \
    # Pin nodesource to prefer its nodejs over Ubuntu's
    && echo "Package: nodejs\nPin: origin deb.nodesource.com\nPin-Priority: 1001" \
       > /etc/apt/preferences.d/nodesource \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g @colbymchenry/codegraph \
    && apt-get purge -y gnupg \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

# Configure git defaults
RUN git config --system user.name "Ascoder" \
    && git config --system user.email "ascoder@localhost" \
    && git config --system core.longpaths true \
    && git config --system credential.helper store

# Create non-root user for git operations
RUN groupadd -r ascoder && useradd -r -g ascoder -m -d /home/ascoder ascoder \
    && mkdir -p /app/data/repos /app/data/worktrees /app/data/project-spaces /app/data/codegraph \
    && chown -R ascoder:ascoder /app/data /home/ascoder

COPY backend/target/ascoder-backend-0.1.0-SNAPSHOT.jar /app/ascoder-backend.jar
COPY backend/docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh \
    && chown ascoder:ascoder /app/docker-entrypoint.sh

USER ascoder

EXPOSE 18080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
