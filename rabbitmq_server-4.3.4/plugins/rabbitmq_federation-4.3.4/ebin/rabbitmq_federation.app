{application, 'rabbitmq_federation', [
	{description, "Deprecated no-op RabbitMQ Federation"},
	{vsn, "4.3.4"},
	{id, "acd1f2e"},
	{modules, ['rabbitmq_federation_noop']},
	{registered, []},
	{applications, [kernel,stdlib,rabbit,rabbitmq_queue_federation,rabbitmq_exchange_federation]},
	{optional_applications, []},
	{env, []}
]}.