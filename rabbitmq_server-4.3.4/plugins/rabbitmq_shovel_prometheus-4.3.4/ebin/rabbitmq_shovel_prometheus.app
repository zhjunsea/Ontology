{application, 'rabbitmq_shovel_prometheus', [
	{description, "Exposes rabbitmq_shovel metrics to Prometheus"},
	{vsn, "4.3.4"},
	{id, "acd1f2e"},
	{modules, ['rabbit_shovel_prometheus_app','rabbit_shovel_prometheus_collector','rabbit_shovel_prometheus_sup']},
	{registered, []},
	{applications, [kernel,stdlib,rabbit_common,rabbit,rabbitmq_shovel,rabbitmq_prometheus]},
	{optional_applications, []},
	{mod, {'rabbit_shovel_prometheus_app', []}},
	{env, []},
		{broker_version_requirements, []}
]}.