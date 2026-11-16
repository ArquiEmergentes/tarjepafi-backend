# RabbitMQ with the TarjePAFI configuration baked in, for hosts that cannot mount files (Azure Container Instances).
# Local development keeps using the stock image plus the mounts in compose.yaml.
FROM rabbitmq:4-management-alpine

COPY enabled_plugins /etc/rabbitmq/enabled_plugins
COPY rabbitmq.conf /etc/rabbitmq/conf.d/20-tarjepafi.conf
COPY tarjepafi-entrypoint.sh /usr/local/bin/tarjepafi-entrypoint.sh
RUN chmod +x /usr/local/bin/tarjepafi-entrypoint.sh

# AMQP for the backend, MQTT for the readers. The management UI (15672) is not meant to be published.
EXPOSE 5672 1883

ENTRYPOINT ["tarjepafi-entrypoint.sh"]
CMD ["rabbitmq-server"]
