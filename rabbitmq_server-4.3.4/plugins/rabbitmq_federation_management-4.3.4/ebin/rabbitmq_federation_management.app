{application, 'rabbitmq_federation_management', [
	{description, "RabbitMQ Federation Management"},
	{vsn, "4.3.4"},
	{id, "acd1f2e"},
	{modules, ['rabbit_federation_mgmt']},
	{registered, []},
	{applications, [kernel,stdlib,amqp_client,rabbit_common,rabbit,rabbitmq_management,rabbitmq_federation]},
	{optional_applications, []},
	{env, []},
		{broker_version_requirements, []}
]}.