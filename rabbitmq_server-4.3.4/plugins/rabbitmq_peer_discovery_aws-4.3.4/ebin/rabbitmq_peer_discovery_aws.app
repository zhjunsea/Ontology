{application, 'rabbitmq_peer_discovery_aws', [
	{description, "AWS-based RabbitMQ peer discovery backend"},
	{vsn, "4.3.4"},
	{id, "acd1f2e"},
	{modules, ['rabbit_peer_discovery_aws','rabbitmq_peer_discovery_aws']},
	{registered, []},
	{applications, [kernel,stdlib,inets,rabbit_common,rabbitmq_peer_discovery_common,rabbitmq_aws,rabbit]},
	{optional_applications, []},
	{env, []}
]}.