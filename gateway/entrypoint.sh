#!/bin/sh
set -eu

if [ -z "${KARAOKE_DOMAIN:-}" ] || [ "${KARAOKE_DOMAIN}" = "<<YOUR_DOMAIN>>" ]; then
    echo "ERROR: Set KARAOKE_DOMAIN to your public hostname." >&2
    exit 1
fi

if [ ! -f /etc/nginx/certs/fullchain.pem ] || [ ! -f /etc/nginx/certs/privkey.pem ]; then
    echo "ERROR: Missing /etc/nginx/certs/fullchain.pem or /etc/nginx/certs/privkey.pem." >&2
    exit 1
fi

export KARAOKE_DOMAIN
envsubst '${KARAOKE_DOMAIN}' < /etc/nginx/karaogui.conf.template > /etc/nginx/nginx.conf

nginx -t
exec nginx -g 'daemon off;'
