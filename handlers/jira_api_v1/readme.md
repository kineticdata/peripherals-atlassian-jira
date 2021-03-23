# Jira API V1
Jira REST API V1 Client

## Info Values
[api_username] The Jira user that will act as an integrator for the service.

[api_token] The api token associated with the user.  Create token at [Atlassian Token Manger](https://id.atlassian.com/manage-profile/security/api-tokens).

[api_location] The url to the Jira instance.

[enable_debug_logging] Sets logging for handler execution.  Set to true or yes for debug level logging.

## Parameters
[Error Handling]
  Select between returning an error message, or raising an exception.

[Method]
  HTTP Method to use for the Jira API call being made.
  Options are:
   - GET
   - POST
   - PUT
   - PATCH
   - DELETE

[Path]
  The relative API path (to the `api_location` info value) that will be called.
  This value should begin with a forward slash `/`.

[Body]
  The body content (JSON) that will be sent for POST, PUT, and PATCH requests.

## Results
[Response Body]
  The returned value from the Rest Call (JSON format)
