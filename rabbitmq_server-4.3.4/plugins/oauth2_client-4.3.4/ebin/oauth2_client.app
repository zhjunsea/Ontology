{application, 'oauth2_client', [
	{description, "OAuth2 client from the RabbitMQ Project"},
	{vsn, "4.3.4"},
	{id, "acd1f2e"},
	{modules, ['jwt_helper','oauth2_client']},
	{registered, []},
	{applications, [kernel,stdlib,ssl,inets,crypto,public_key,rabbit_common]},
	{optional_applications, []},
	{env, []}
]}.