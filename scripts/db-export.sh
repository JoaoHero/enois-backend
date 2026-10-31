#!/usr/bin/env bash
set -euo pipefail

# Exporta um banco para stdout
# Uso: MARIADB_HOST=... MARIADB_DATABASE=... MARIADB_PASSWORD=... ./scripts/db-export.sh > dumps/arquivo.sql
#       ./scripts/db-export.sh --help

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  echo "Exporta um banco para um arquivo SQL."
  echo ""
  echo "Uso: $0 > dumps/arquivo.sql"
  echo ""
  echo "Variaveis de ambiente:"
  echo "  MARIADB_HOST       (obrigatoria)"
  echo "  MARIADB_PORT       (default: 3306)"
  echo "  MARIADB_USER       (obrigatoria)"
  echo "  MARIADB_PASSWORD   (obrigatoria; nunca vai na linha de comando)"
  echo "  MARIADB_DATABASE   (obrigatoria)"
  echo ""
  echo "Exemplo (dumps/ e ignorado pelo git; o dump tem dados pessoais):"
  echo "  mkdir -p dumps && $0 > dumps/backup_\$(date +%F).sql"
  echo ""
  echo "Requer mariadb-dump ou mysqldump instalado."
  exit 0
fi

# Sem default de destino: o alvo precisa ser escolhido de proposito a cada execucao.
HOST="${MARIADB_HOST:?defina MARIADB_HOST}"
PORT="${MARIADB_PORT:-3306}"
USER="${MARIADB_USER:?defina MARIADB_USER}"
PASS="${MARIADB_PASSWORD:?defina MARIADB_PASSWORD}"
DB="${MARIADB_DATABASE:?defina MARIADB_DATABASE}"

DUMPER=$(command -v mariadb-dump || command -v mysqldump || echo "")

if [ -z "$DUMPER" ]; then
  echo "ERRO: instale mariadb-dump ou mysqldump" >&2
  echo "  brew install mariadb" >&2
  echo "  apt install mariadb-client" >&2
  exit 1
fi

# A senha vai num arquivo de opcoes 0600 (nao aparece em `ps`), apagado na saida.
OPTS_FILE=$(mktemp)
trap 'rm -f "$OPTS_FILE"' EXIT
chmod 600 "$OPTS_FILE"
ESCAPED_PASS=${PASS//\\/\\\\}
printf '[client]\npassword="%s"\n' "${ESCAPED_PASS//\"/\\\"}" > "$OPTS_FILE"

echo "-- Dump de $DB em $(date)"
echo "-- Host: $HOST"
echo ""
echo "CREATE DATABASE IF NOT EXISTS \`$DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
echo "USE \`$DB\`;"
echo ""

"$DUMPER" \
  --defaults-extra-file="$OPTS_FILE" \
  --host="$HOST" \
  --port="$PORT" \
  --user="$USER" \
  --single-transaction \
  --routines \
  --triggers \
  --skip-lock-tables \
  --skip-add-locks \
  --no-tablespaces \
  --complete-insert \
  "$DB"

echo ""
echo "-- Dump concluido em $(date)"
