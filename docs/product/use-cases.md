# Use Cases

## UC-001 Register a user

**Actor:** Anonymous user

**Preconditions:** The email address is not already registered.

**Flow:**

1. The client submits an email address and password.
2. The server validates the input.
3. The server hashes the password.
4. The server stores the user.

**Result:** The user account is created. The password is not returned or stored as plaintext.

## UC-002 Log in

**Actor:** Registered user

**Flow:**

1. The client submits an email address and password.
2. The server finds the user by email.
3. The server verifies the password hash.
4. The server issues a JWT access token.

**Failures:** Unknown email or invalid password returns `401 Unauthorized` without revealing which value was incorrect.

## UC-003 Access a project device

**Actor:** Authenticated user

**Preconditions:** The user is a member of the device's project.

**Flow:**

1. The client sends a bearer token.
2. The server authenticates the token.
3. The server checks project membership and role.
4. The server returns the device or rejects the request.

## UC-004 Share a project

**Actor:** Project owner or administrator

**Flow:**

1. The authorized user selects another registered user.
2. The authorized user assigns a project role.
3. The server creates or updates project membership.

**Failure:** A member without project-management permission receives `403 Forbidden`.

## UC-005 Authenticate a device

**Actor:** Registered device

**Flow:**

1. The device sends its identifier and secret or API key.
2. The server compares the supplied credential with its stored hash.
3. The server checks that the credential is active.
4. The server accepts or rejects the device request.

## UC-006 Ingest telemetry

**Actor:** Authorized device or authenticated application client

**Flow:**

1. The client submits a device identifier, timestamp, and payload.
2. The server validates the request.
3. The server verifies that the device exists and is authorized.
4. The server stores and returns the telemetry record.