{
  'info' => {
    'api_username' => "",
    'api_token' => "",
    'api_location' => "https://acme.atlassian.net/rest/api/3",
    'enable_debug_logging' => 'true'
  },
  'parameters' => {
    'error_handling' => 'Raise Error',
    'method' => 'POST',
    'path' => '/project',
    'body' => '{
      "key": "ER",
      "projectTypeKey": "software",
      "description": "Example Project description",
      "leadAccountId": "",
      "name": "Example"
    }'
  }
}
