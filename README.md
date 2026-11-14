# Room API

Spring Boot REST API for rooms and reservations, running on MySQL through Spring Data JPA,
protected with Spring Security (HTTP Basic and JWT).

**JDK:** 21  **Database:** MySQL 8

## Database setup

Run as root once (the root password is not stored in the project):

```sql
CREATE DATABASE roomdb;
CREATE USER 'roomapp'@'localhost' IDENTIFIED BY 'roomapp1234';
GRANT ALL PRIVILEGES ON roomdb.* TO 'roomapp'@'localhost';
```

Before testing, reset the tables and sample data with `reset.sql`. It drops and recreates
`room`, `reservation` and `app_user`, and inserts 3 rooms, 5 reservations and 3 accounts.
Use either way:

**Terminal** (from the project folder):

```bash
mysql -u roomapp -p roomdb < reset.sql
```

On macOS, if you get `command not found: mysql`, use the full path:

```bash
/usr/local/mysql/bin/mysql -u roomapp -p roomdb < reset.sql
```

Enter `roomapp1234` when asked for the password.

**MySQL Workbench:**

1. Open a connection as `roomapp` (password `roomapp1234`).
2. Open `reset.sql` in any editor, copy everything, and paste it into a new SQL tab in Workbench.
3. Click the lightning bolt ⚡ (Execute) to run the whole script.
4. Check with `SELECT * FROM roomdb.room;` (3 rooms) and `SELECT * FROM roomdb.app_user;` (3 accounts, passwords stored as BCrypt hashes).

Spring does not create the tables: `spring.jpa.hibernate.ddl-auto=validate` only checks that the entities match them.

## How to run

Run `reset.sql` first, then:

```bash
./gradlew bootRun        # macOS / Linux
gradlew.bat bootRun      # Windows
```

The server starts on http://localhost:8080. Swagger UI: http://localhost:8080/swagger-ui.html

## Authentication

### Public endpoints (no login needed)

| Method | Path |
|---|---|
| GET | `/api/rooms`, `/api/rooms/{id}`, `/api/rooms/count` |
| GET | `/api/rooms/{roomId}/reservations` |
| POST | `/api/auth/signup` |
| POST | `/api/auth/login` |
| GET | Swagger UI (`/swagger-ui.html`, `/v3/api-docs`) |

Everything else needs a login:

- every `POST`, `PUT` and `DELETE` (except sign-up and login)
- `GET /api/reservations/**` (who booked what is personal)
- `GET /api/auth/me`

Without a valid login the API answers **401** in the usual `ApiError` shape, with one of three messages:

| Message | When |
|---|---|
| `login required` | No credentials were sent |
| `wrong username or password` | Login or HTTP Basic with a wrong username or password |
| `token is invalid or expired` | A Bearer token that is broken, changed or expired (also on public GETs: a token that is sent is always checked) |

### Getting a token

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "username": "mina", "password": "mina1234" }
```

Answer (200):

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 1800 }
```

The token is a JWT signed with HS256 and is valid for 30 minutes.

### Sending a token

Put it in the `Authorization` header of every protected request:

```http
POST http://localhost:8080/api/rooms
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

{ "name": "Token Room", "capacity": 3 }
```

HTTP Basic also works: `Authorization: Basic <base64 of username:password>`.
For example, `Authorization: Basic bWluYTptaW5hMTIzNA==` is `mina:mina1234`.
`api.http` uses Basic for requests 1–49 and the token from request 50 for requests 52–53.

### Signing up

```http
POST http://localhost:8080/api/auth/signup
Content-Type: application/json

{ "username": "lee", "password": "lee12345" }
```

- `username`: 3 to 20 lowercase letters or digits, must be unique (409 if taken)
- `password`: 8 to 72 characters

The answer (201) has only `id`, `username` and `role`. Every new account gets the role `USER`.

### Sample accounts (created by reset.sql)

| Username | Password | Role |
|---|---|---|
| mina | mina1234 | USER |
| omar | omar1234 | USER |
| admin | admin1234 | ADMIN |

Passwords are never stored: the `app_user` table holds only BCrypt hashes (`$2a$10$...`).
No password appears in any response or log, and no hash appears in any response.

### The JWT secret

`app.jwt.secret` is read from the `JWT_SECRET` environment variable. Without it, a class-only default
from `application.properties` is used. To use your own secret (at least 32 bytes), set it before starting:

```bash
export JWT_SECRET="put-a-long-random-secret-of-at-least-32-bytes-here"
./gradlew bootRun
```

## API Endpoints

| Method | Endpoint | Login | Status | Description |
|---|---|---|---|---|
| GET | `/api/rooms` | no | 200 | All rooms; optional `minCapacity`, `keyword`, `sort=capacity` or `sort=name` |
| GET | `/api/rooms/count` | no | 200 | Number of rooms |
| GET | `/api/rooms/{id}` | no | 200 / 400 / 404 | One room |
| POST | `/api/rooms` | yes | 201 / 400 / 401 | Create a room |
| PUT | `/api/rooms/{id}` | yes | 200 / 400 / 401 / 404 | Replace a room's name and capacity |
| DELETE | `/api/rooms/{id}` | yes | 204 / 401 / 404 / 409 | Delete a room (409 while it has reservations) |
| GET | `/api/rooms/{roomId}/reservations` | no | 200 / 404 | Reservations of one room, by start time |
| POST | `/api/rooms/{roomId}/reservations` | yes | 201 / 400 / 401 / 404 / 409 | Book a room (409 if the time overlaps) |
| GET | `/api/reservations?reservedBy={name}` | yes | 200 / 400 / 401 | One person's reservations, with room names |
| GET | `/api/reservations/{id}` | yes | 200 / 400 / 401 / 404 | One reservation |
| DELETE | `/api/reservations/{id}` | yes | 204 / 401 / 404 | Cancel a reservation |
| POST | `/api/auth/signup` | no | 201 / 400 / 409 | Create an account |
| POST | `/api/auth/login` | no | 200 / 400 / 401 | Get a JWT |
| GET | `/api/auth/me` | yes | 200 / 401 | Who is logged in |

## Testing with api.http

Run `reset.sql`, start the app, then run the requests in `api.http` **in order, from 1 to 58**.
The expected results assume the starting state; a second run without a reset gives different answers.

### Requests 1–25: Rooms and reservations

| # | Test | Expected |
|---:|---|---:|
| 1 | Get all rooms | 200 |
| 2 | Filter by minimum capacity | 200 |
| 3 | Filter by keyword | 200 |
| 4 | Combined filters | 200 |
| 5 | Sort by capacity | 200 |
| 6 | Get room by ID | 200 |
| 7 | Get nonexistent room | 404 |
| 8 | Update room | 200 |
| 9 | Update with invalid capacity | 400 |
| 10 | Update nonexistent room | 404 |
| 11 | Create room | 201 |
| 12 | Create room with too high capacity | 400 |
| 13 | Delete room without reservations | 204 |
| 14 | Count rooms | 200 |
| 15 | Reservations of room 1 | 200 |
| 16 | Reservations of a nonexistent room | 404 |
| 17 | Create reservation | 201, `/api/reservations/6` |
| 18 | Overlapping reservation | 409 |
| 19 | Reservation starting when another ends | 201, `/api/reservations/7` |
| 20 | End before start | 400 |
| 21 | Reservation for a nonexistent room | 404 |
| 22 | Mina's reservations | 200, 2 items |
| 23 | Delete room with reservations | 409 |
| 24 | Reservation by ID | 200 |
| 25 | Nonexistent reservation | 404 |

### Requests 26–33: Dirty checking and error bodies

| # | Test | Expected |
|---:|---|---:|
| 26 | Update room with the same values | 200 |
| 27 | Update room with new values | 200 |
| 28 | Create room with blank name | 400 |
| 29 | Create room with capacity 0 | 400 |
| 30 | Create room with two invalid fields | 400 |
| 31 | Nonexistent room | 404 |
| 32 | Delete room with reservations | 409 |
| 33 | Nonexistent URL | 404 |

For request 26, the console shows one SELECT and no UPDATE.
For request 27, the console shows a SELECT followed by an UPDATE.

### Requests 34–41: Reservation validation and bad input

| # | Test | Expected |
|---:|---|---:|
| 34 | Blank reservedBy | 400 |
| 35 | Missing endTime | 400 |
| 36 | End before start | 400 |
| 37 | Overlapping reservation | 409 |
| 38 | Nonexistent reservation | 404 |
| 39 | Missing query parameter | 400 |
| 40 | Non-numeric capacity | 400 |
| 41 | Non-numeric room ID | 400 |

Every write in 1–41, and the reservation reads 22, 24, 25, 38 and 39, carry Mina's Basic header.

### Requests 42–49: Spring Security and HTTP Basic

| # | Test | Expected |
|---:|---|---:|
| 42 | Create room without logging in | 401, `login required` |
| 43 | Sign up lee / lee12345 | 201, id, username, role |
| 44 | Sign up lee again | 409, `username lee is taken` |
| 45 | Sign up `Lee!` / `short` | 400, two fieldErrors |
| 46 | Create room as lee (Basic) | 201, `/api/rooms/5` |
| 47 | Create room with a wrong password | 401, `wrong username or password` |
| 48 | Get rooms without logging in | 200, 4 rooms |
| 49 | `GET /api/auth/me` as lee (Basic) | 200, lee, `ROLE_USER` |

### Requests 50–56: Tokens and Lab F

| # | Test | Expected |
|---:|---|---:|
| 50 | Log in as lee (keeps the token) | 200, accessToken |
| 51 | Log in with a wrong password | 401, `wrong username or password` |
| 52 | `GET /api/auth/me` with the token | 200, lee, no `ROLE_USER` yet |
| 53 | Create room with the token | 201, `/api/rooms/6` |
| 54 | Create room with a broken token | 401, `token is invalid or expired` |
| 55 | Get rooms with a broken token | 401, `token is invalid or expired` |
| 56 | Mina's reservations without logging in | 401, `login required` |

### Requests 57–58: My extra error tests

| # | Test | Expected |
|---:|---|---:|
| 57 | Reservation with a non-numeric ID | 400 |
| 58 | Room name longer than 100 characters | 400 |

## Validation

The API validates incoming request data before processing it:

- Room names must not be blank and must be at most 100 characters.
- Room capacity must be between 1 and 20.
- Reservation `reservedBy` must not be blank (at most 50 characters).
- Reservation start and end times are required, and the start must be before the end.
- Sign-up usernames are 3 to 20 lowercase letters or digits; passwords are 8 to 72 characters.

Invalid fields are listed in the `fieldErrors` property of the error response.

## Error Responses

Every error, including a 401 from the security filters, uses the same `ApiError` body:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "request has invalid fields",
  "path": "/api/rooms",
  "fieldErrors": [
    { "field": "name", "message": "name is required" }
  ]
}
```

| Field | Description |
|---|---|
| `status` | HTTP status code |
| `error` | HTTP error description |
| `message` | Explanation of the error |
| `path` | Requested API endpoint |
| `fieldErrors` | One entry per invalid field (empty for other errors) |

### HTTP Status Codes

| Status | Meaning |
|---:|---|
| 200 | Request successful |
| 201 | Resource created successfully |
| 204 | Resource deleted successfully |
| 400 | Invalid request, input validation failure, or a wrong type in the path |
| 401 | Not logged in, wrong username or password, or an invalid or expired token |
| 404 | Requested resource or endpoint not found |
| 409 | Conflict: an overlapping reservation, a room that still has reservations, or a username that is taken |
| 500 | Unexpected server error (details are logged on the server, never sent to the client) |

## AI use

No AI used.
