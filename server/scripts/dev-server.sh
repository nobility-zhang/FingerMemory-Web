#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
server_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
repo_root=$(CDPATH= cd -- "$server_dir/.." && pwd)
secret_dir="$repo_root/.local/secrets"
secret_file="$secret_dir/admin.env"

mkdir -p "$secret_dir"
chmod 700 "$secret_dir"

if [ ! -f "$secret_file" ]; then
  umask 077
  password=$(openssl rand -base64 36 | tr -d '\r\n')
  {
    printf '%s\n' '# Local-only default administrator bootstrap. Never commit this file.'
    printf '%s\n' 'APP_ADMIN_BOOTSTRAP_ENABLED=true'
    printf '%s\n' 'APP_ADMIN_BOOTSTRAP_USERNAME=admin'
    printf "APP_ADMIN_BOOTSTRAP_PASSWORD='%s'\n" "$password"
  } > "$secret_file"
  unset password
fi

chmod 600 "$secret_file"
printf 'Default administrator credentials: %s\n' "$secret_file"

cd "$server_dir"
docker compose -f compose.test.yaml -f compose.dev.yaml up -d --wait --wait-timeout 60 mysql fsrs-worker

export DB_URL='jdbc:mysql://127.0.0.1:3306/finger_memory_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export DB_USERNAME='finger_memory_test'
export DB_PASSWORD='finger_memory_test_only'
export FSRS_WORKER_URL='http://127.0.0.1:8090'
export SERVER_PORT="${SERVER_PORT:-8081}"

set -a
. "$secret_file"
set +a

printf 'Local server: http://127.0.0.1:%s\n' "$SERVER_PORT"
exec "$server_dir/mvnw" -f "$server_dir/pom.xml" spring-boot:run
