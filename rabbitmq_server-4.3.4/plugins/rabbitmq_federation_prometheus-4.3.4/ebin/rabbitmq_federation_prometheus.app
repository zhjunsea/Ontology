{application, 'rabbitmq_federation_prometheus', [
	{description, "Exposes rabbitmq_federation metrics to Prometheus"},
	{vsn, "4.3.4"},
	{id, "acd1f2e"},
	{modules, ['rabbit_federation_prometheus_app','rabbit_federation_prometheus_collector','rabbit_federation_prometheus_sup']},
	{registered, []},
	{applications, [kernel,stdlib,rabbit_common,rabbit,rabbitmq_federation,rabbitmq_prometheus]},
	{optional_applications, []},
	{mod, {'rabbit_federation_prometheus_app', []}},
	{env, []},
		{broker_version_requirements, []}
]}.