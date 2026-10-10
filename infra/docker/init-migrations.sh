#!/usr/bin/env bash
set -eo pipefail

# Canonical migrations use protocol SQL, which has no client DELIMITER commands.
# The official MariaDB entrypoint sources non-executable .sh files. Also support
# execution as a child script, without starting another database process.
if ! declare -F docker_process_sql >/dev/null; then
    source /usr/local/bin/docker-entrypoint.sh
    docker_setup_env mariadbd
fi

for migration in /database/migrations/[0-9][0-9][0-9]_*.sql; do
    echo "Applying initial migration: $(basename "$migration")"
    # Compound triggers/routines have BEGIN and a standalone END in canonical SQL.
    # Buffer their header until the next line distinguishes simple ones.
    awk '
        { sub(/\r$/, "") }
        /^CREATE( OR REPLACE)? (TRIGGER|PROCEDURE) / { header=$0; next }
        header != "" {
            if ($0 == "BEGIN") { print "DELIMITER $$"; compound=1 }
            print header; header=""
        }
        compound && /^END;$/ { print "END$$"; print "DELIMITER ;"; compound=0; next }
        { print }
        END { if (header != "" || compound) exit 1 }
    ' "$migration" | docker_process_sql --database="$MARIADB_DATABASE"
done
