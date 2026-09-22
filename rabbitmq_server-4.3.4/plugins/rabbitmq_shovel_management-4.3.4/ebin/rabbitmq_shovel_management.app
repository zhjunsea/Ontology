{application, 'rabbitmq_shovel_management', [
	{description, "Management extension for the Shovel plugin"},
	{vsn, "4.3.4"},
	{id, "acd1f2e"},
	{modules, ['rabbit_shovel_mgmt_shovel','rabbit_shovel_mgmt_shovels','rabbit_shovel_mgmt_util']},
	{registered, []},
	{applications, [kernel,stdlib,rabbit_common,rabbit,rabbitmq_management,rabbitmq_shovel]},
	{optional_applications, []},
	{env, []},
		{broker_version_requirements, []}
]}.