# API Documentation Strategy

This document describes the documentation strategy used for the DataCrowd Lab API,
including tools, standards, naming conventions, versioning approach, formatting rules,
and known limitations.

## Documentation goals

The main goals of the API documentation are:
- to clearly describe available API endpoints and their purpose;
- to explain authentication and authorization rules;
- to provide practical, step-by-step usage examples;
- to make the API understandable for new developers and reviewers.

The documentation is designed to support both learning and evaluation purposes.

## Tools used

The following tools and formats are used:

- **OpenAPI / Swagger (SpringDoc)**  
  Used to generate a machine-readable API specification and interactive Swagger UI.

- **Markdown**  
  Used for human-readable documentation files stored in the repository under the `docs/` directory.

- **PlantUML**  
  Used to create architecture and sequence diagrams in a text-based, version-controlled format.

All documentation artifacts are stored in the same Git repository as the source code.

## Standards followed

- **RESTful API principles**
    - Clear resource-oriented URLs
    - Proper usage of HTTP methods (GET, POST, PATCH)
    - Meaningful HTTP status codes

- **OpenAPI 3.x specification**
    - Used for describing endpoints, request/response schemas, and authentication

- **Consistent error model**
    - Unified JSON error response format
    - Standard HTTP error codes (400, 401, 403, 404, 409, 500)

## Naming conventions

- **Endpoints**
    - Nouns for resources: `/projects`, `/datasets`, `/tasks`
    - Actions expressed via HTTP methods, not verbs in URLs

- **JSON fields**
    - Lower camelCase (`projectId`, `reviewersCount`)
    - Consistent naming across requests and responses

- **Files and folders**
    - Documentation files use lowercase names with hyphens
    - Diagrams are stored in `docs/diagrams/`
    - Tutorials are stored in `docs/tutorials/`

## Versioning approach

- The API is designed with versioning in mind.
- Versioning can be applied at the URL level (e.g. `/api/v1`) if backward-incompatible changes are required.
- Currently, the project focuses on a single API version suitable for a diploma demo.

All changes to the API and documentation are tracked through Git version control.

## Formatting rules

- Markdown files use a clear hierarchy of headings (`#`, `##`, `###`)
- Code examples are shown as plain JSON blocks
- Diagrams are written in PlantUML for readability and reproducibility
- Each document focuses on a single responsibility (overview, authentication, tutorials, etc.)

## Known gaps and limitations

The following limitations are known and acknowledged:

- Not all edge cases are fully documented for every endpoint.
- Advanced topics such as rate limiting and backward compatibility policies are described conceptually but not fully implemented.
- Some request/response examples are simplified for clarity.
- The documentation focuses on core business flows rather than exhaustive endpoint coverage.

These limitations are acceptable for the scope of the diploma project and can be addressed in future iterations.
