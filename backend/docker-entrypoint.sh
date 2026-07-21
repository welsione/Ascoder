#!/bin/sh
# Entrypoint script for Ascoder backend container.
# Runs as root to chown mounted volumes, then drops to ascoder via gosu.
set -eu

# --- Privilege drop: chown mounted volumes, then re-exec as ascoder ---
# 挂载卷（./data/*）由 docker daemon 以 root 创建，ascoder 无写权限会导致 git clone 报
# Permission denied（如 "could not create work tree dir '/app/data/repos/...'"）。
# 启动时以 root 修正属主，再用 gosu 降权运行 java，兼顾安全与可用。
if [ "$(id -u)" = "0" ]; then
    # 任一挂载卷属主非 ascoder 即全量 chown：只查 repos 会漏掉其他卷被外部改属主的情况。
    # chown 单独放 if 内，失败不吞错（set -e 触发，启动时暴露），而非运行时报 Permission denied。
    need_chown=0
    for d in repos worktrees project-spaces codegraph; do
        [ "$(stat -c %U "/app/data/$d" 2>/dev/null || echo root)" = "ascoder" ] || { need_chown=1; break; }
    done
    if [ "$need_chown" = "1" ]; then
        chown -R ascoder:ascoder /app/data
    fi
    exec gosu ascoder "$0" "$@"
fi

export HOME=/home/ascoder

# --- Git HTTPS credential setup ---

# Validate a host string: must contain only [a-zA-Z0-9.-] and at least one dot.
# Wildcards (*.) are NOT supported — git credential store does not match globs.
# Returns 0 if valid, 1 if invalid (and prints a warning).
validate_host() {
    _host="$1"
    # Must not be empty
    if [ -z "$_host" ]; then
        return 1
    fi
    # Must only contain letters, digits, dots, hyphens (no glob chars, no spaces)
    case "$_host" in
        *[!a-zA-Z0-9.-]*)
            echo "WARN: invalid host skipped (illegal chars): $_host" >&2
            return 1
            ;;
    esac
    # Must contain at least one dot (TLD required)
    case "$_host" in
        *.*)
            return 0
            ;;
        *)
            echo "WARN: invalid host skipped (no TLD): $_host" >&2
            return 1
            ;;
    esac
}

# Write a single credential line if the host is valid.
write_credential() {
    _host="$1"
    if validate_host "$_host"; then
        echo "https://${GIT_USERNAME}:${GIT_TOKEN}@${_host}" >> "$CREDENTIAL_FILE"
        echo "  - ${_host}"
    fi
}

if [ -n "${GIT_TOKEN:-}" ] && [ -n "${GIT_USERNAME:-}" ]; then
    CREDENTIAL_FILE="${HOME}/.git-credentials"
    : > "$CREDENTIAL_FILE"
    chmod 600 "$CREDENTIAL_FILE"

    echo "Git credentials configured for:"

    # 1) Default hosts
    for HOST in github.com gitlab.com gitee.com; do
        write_credential "$HOST"
    done

    # 2) Extra hosts from GIT_EXTRA_HOSTS (comma or space separated)
    if [ -n "${GIT_EXTRA_HOSTS:-}" ]; then
        # Replace commas with newlines, then process each line
        echo "${GIT_EXTRA_HOSTS}" | tr ',' '\n' | while IFS= read -r EXTRA_HOST; do
            # Trim leading/trailing whitespace
            EXTRA_HOST=$(echo "$EXTRA_HOST" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
            [ -z "$EXTRA_HOST" ] && continue
            write_credential "$EXTRA_HOST"
        done
    fi
fi

# --- TLS proxy setup ---
# If GIT_TLS_PROXY is set, configure git to route HTTPS requests through
# a host-side TLS termination proxy. This resolves GnuTLS incompatibility
# with some Git servers (e.g. git.qiyuesuo.me).
# Format: GIT_TLS_PROXY=http://host.docker.internal:8443
# The proxy rewrites https:// URLs to http://proxy/https:// for TLS offloading.
# We also add the proxy host to credential store so Git can authenticate.
if [ -n "${GIT_TLS_PROXY:-}" ]; then
    git config --global "url.${GIT_TLS_PROXY}/https://".insteadOf "https://"
    # Add proxy host to credential store so git doesn't prompt for auth on the proxy URL.
    # The proxy itself doesn't need auth, but git's credential helper tries to match
    # the rewritten URL. We provide a dummy credential for the proxy host.
    PROXY_HOST=$(echo "${GIT_TLS_PROXY}" | sed 's|http://||' | sed 's|/.*||')
    if [ -f "${HOME}/.git-credentials" ]; then
        echo "http://git:x@${PROXY_HOST}" >> "${HOME}/.git-credentials"
    else
        echo "http://git:x@${PROXY_HOST}" > "${HOME}/.git-credentials"
        chmod 600 "${HOME}/.git-credentials"
    fi
    echo "Git TLS proxy configured: ${GIT_TLS_PROXY}"
fi

# --- SSH key permissions ---

if [ -d "${HOME}/.ssh" ]; then
    chmod 700 "${HOME}/.ssh" 2>/dev/null || true
    chmod 600 "${HOME}/.ssh"/* 2>/dev/null || true
    # Configure SSH for non-interactive Git usage
    GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=accept-new"
    export GIT_SSH_COMMAND
fi

# --- Start application ---

# shellcheck disable=SC2086
exec java ${JVM_OPTS:-} -jar /app/ascoder-backend.jar
