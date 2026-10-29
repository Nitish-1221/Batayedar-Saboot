# Batayedar Saboot — Secure Backend API

A production-oriented, secure REST API and cloud backend for the **Batayedar Saboot** tenant farmer crop evidence platform.

This service manages farmer identity, land parcels (fields), crop records, GPS-validated photographic evidence verification with SHA-256 integrity checks, landlord confirmations, and offline-first batch synchronization.

---

## 1. Architectural Highlights

- **Platform**: Node.js (>= 18), Express.js.
- **Database**: MongoDB with Mongoose schemas, compound indexes, and TTL token expiration.
- **Authentication**: JWT access tokens (15m expiry) + rotating refresh tokens with token-family reuse detection (theft protection) and instant revocation.
- **Password Hashing**: Strong bcrypt (12 salt rounds).
- **Validation**: Strict schema validation via Zod on all route parameters, query strings, and payloads.
- **Security & Headers**: Helmet security headers, CORS origin restrictions, body payload size caps, strict login/auth rate limiting, and general API rate limiting.
- **Storage**: AWS S3-compatible private object storage with short-lived presigned PUT/GET URLs and server-side magic byte inspection + SHA-256 recalculation.
- **Audit Logging**: Immutable, append-only audit trail for all significant authentication, record alteration, evidence verification, and export actions.
- **API Documentation**: OpenAPI 3.0 specification served via interactive Swagger UI at `/api-docs`.

---

## 2. Environment Variables (.env)

Create a `.env` file in the root directory based on `.env.example`:

| Variable | Description | Example / Default |
|---|---|---|
| `PORT` | HTTP port for server | `5000` |
| `NODE_ENV` | Environment mode | `development` / `production` |
| `API_PREFIX` | Versioned route prefix | `/api/v1` |
| `MONGODB_URI` | MongoDB connection URI | `mongodb://localhost:27017/batayedar_saboot` |
| `JWT_ACCESS_SECRET` | High-entropy HMAC secret for access tokens | *(min 64 chars random string)* |
| `JWT_REFRESH_SECRET` | High-entropy HMAC secret for refresh tokens | *(min 64 chars random string)* |
| `JWT_ACCESS_EXPIRY` | Short access token lifespan | `15m` |
| `JWT_REFRESH_EXPIRY` | Refresh token lifespan | `7d` |
| `CORS_ALLOWED_ORIGINS`| Comma-separated allowed origins | `http://localhost:3000,http://localhost:5173` |
| `STORAGE_PROVIDER` | Object storage provider | `s3` or `local` |
| `AWS_REGION` | AWS S3 region | `ap-south-1` |
| `AWS_ACCESS_KEY_ID` | Private AWS IAM Access Key | *(AWS credential)* |
| `AWS_SECRET_ACCESS_KEY`| Private AWS IAM Secret Key | *(AWS credential)* |
| `S3_BUCKET_NAME` | Private S3 evidence vault bucket | `batayedar-evidence-vault-private` |
| `PRESIGNED_URL_EXPIRES_SECONDS` | Presigned URL expiry | `900` (15 min) |
| `MAX_FILE_SIZE_BYTES` | Maximum evidence photo upload size | `15728640` (15 MB) |

> **IMPORTANT**: In production, never commit `.env` or hardcode secrets into the source code or mobile application.

---

## 3. Getting Started

### Prerequisites
- Node.js (v18.0 or newer)
- npm (v9.0 or newer)
- MongoDB instance (local `mongod` or MongoDB Atlas URI)

### Installation
```bash
cd batayegedar-backend
npm install
```

### Running Locally
```bash
# Start backend in development mode with auto-reload
npm run dev

# Or start for production
npm start
```

The server will start at `http://localhost:5000`.
- API endpoints: `http://localhost:5000/api/v1`
- Interactive OpenAPI Docs: `http://localhost:5000/api-docs`

### Running Automated Tests
The backend test suite uses `mongodb-memory-server` and Jest, running in-memory without needing an external MongoDB instance:
```bash
npm test
```

---

## 4. API Endpoints Overview

All endpoints are versioned under `/api/v1`:

### Authentication & Sessions
- `POST /api/v1/auth/register` — Register a new farmer.
- `POST /api/v1/auth/login` — Log in with phone and password.
- `POST /api/v1/auth/refresh` — Rotate refresh token and obtain new access token.
- `POST /api/v1/auth/logout` — Revoke active refresh token.
- `POST /api/v1/auth/change-password` — Change password and revoke all existing sessions.

### Farmer Profile
- `GET /api/v1/users/me` — Retrieve authenticated farmer profile.
- `PATCH /api/v1/users/me` — Update farmer profile details.
- `DELETE /api/v1/users/me` — Request account deletion (subject to statutory retention).

### Land Parcels (Fields)
- `POST /api/v1/fields` — Create a field parcel.
- `GET /api/v1/fields` — List fields belonging to authenticated farmer.
- `GET /api/v1/fields/:fieldId` — Get single field details.
- `PATCH /api/v1/fields/:fieldId` — Update field parcel.
- `DELETE /api/v1/fields/:fieldId` — Soft-delete field parcel.

### Crop Cultivation Records
- `POST /api/v1/crop-records` — Record crop cultivation.
- `GET /api/v1/crop-records` — List crop records.
- `GET /api/v1/crop-records/:recordId` — Get crop record details.
- `PATCH /api/v1/crop-records/:recordId` — Update crop record.
- `GET /api/v1/crop-records/:recordId/evidence` — List evidence photos for a record.

### Evidence & Photographs
- `POST /api/v1/evidence/upload-url` — Request short-lived presigned upload URL with GPS validation.
- `POST /api/v1/evidence/:evidenceId/complete` — Finalize upload, verify magic bytes, calculate server SHA-256 hash.
- `GET /api/v1/evidence/:evidenceId` — Get evidence metadata.
- `GET /api/v1/evidence/:evidenceId/download-url` — Generate short-lived presigned URL for private evidence viewing.
- `DELETE /api/v1/evidence/:evidenceId` — Soft-delete evidence record.

### Landlord Confirmations
- `POST /api/v1/crop-records/:recordId/confirmation-requests` — Create confirmation request for a crop record.
- `POST /api/v1/confirmations/:confirmationId/respond` — Record verbal, written, or affidavit confirmation.

### Offline Synchronization
- `POST /api/v1/sync/push` — Push batch of locally captured records/photos idempotently.
- `GET /api/v1/sync/pull` — Pull updates modified since a specific timestamp.

---

## 5. Security & Multi-Tenancy Architecture

1. **Derived Identity**: All protected endpoints derive the farmer identity strictly from the cryptographically verified JWT token (`req.farmerId`), never from client-supplied URL query or body parameters.
2. **Strict Multi-Tenant Isolation**: A farmer cannot view, edit, or delete another farmer's fields, records, or photographs. Attempts result in `403 Forbidden`.
3. **No Credential Leaks**: API error responses never expose database errors, internals, or stack traces in production.
4. **GPS Evidentiary Principle**: Server validation enforces horizontal accuracy within 20m. However, as noted in the system design:
   > *"GPS metadata and photographs do not independently establish land ownership, legal tenancy, or legal entitlement. The legal significance of the evidence depends on applicable state laws and assessment by the relevant authorities."*
