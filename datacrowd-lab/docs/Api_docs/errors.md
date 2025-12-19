# Error Handling

The API returns errors in a consistent JSON format.

## HTTP status codes

- 400 Bad Request — validation errors
- 401 Unauthorized — missing or invalid authentication
- 403 Forbidden — insufficient permissions
- 404 Not Found — resource does not exist
- 409 Conflict — invalid state transition
- 500 Internal Server Error — unexpected error

## Error response format

{
"timestamp": "2025-12-19T10:00:00+02:00",
"status": 403,
"error": "FORBIDDEN",
"message": "Access denied",
"path": "/projects"
}
