FROM nginx:1.27-alpine

COPY nginx.conf.template /etc/nginx/karaogui.conf.template
COPY entrypoint.sh /usr/local/bin/gateway-entrypoint.sh
RUN chmod +x /usr/local/bin/gateway-entrypoint.sh

EXPOSE 443
ENTRYPOINT ["/usr/local/bin/gateway-entrypoint.sh"]
