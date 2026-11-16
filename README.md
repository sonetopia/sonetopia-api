# Sonetopia API

Backend for Sonetopia, a personal music library: upload audio, import it from a URL (YouTube Music, YouTube, SoundCloud and anything else yt-dlp supports), organise it into playlists, and stream it with signed URLs.

- **Stack:** Java 25, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL
- **Audio import:** [yt-dlp](https://github.com/yt-dlp/yt-dlp) + [ffmpeg](https://ffmpeg.org/)
- **Responses** share one envelope: `{ "data": ..., "errors": ... }`

## Contents

- [Requirements](#requirements)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Tests](#tests)
- [API overview](#api-overview)
- [Importing audio from a URL](#importing-audio-from-a-url)
- [Admin commands](#admin-commands)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

## Requirements

| Tool | Notes |
|---|---|
| JDK 25 | The Maven wrapper (`mvnw`) downloads Maven itself |
| PostgreSQL | Flyway creates and migrates the schema on startup |
| yt-dlp | Needed for search and URL import |
| ffmpeg | Needed by yt-dlp to convert downloads to mp3 |

## Getting started

1. Create a database:

   ```bash
   createdb sonetopia
   ```

2. Export the required environment variables (see [Configuration](#configuration) for all of them):

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/sonetopia
   export DB_USERNAME=postgres
   export DB_PASSWORD=password
   export AUDIO_STORAGE_PATH=./storage
   export SIGNED_URL_SECRET=change-me
   export JWT_SECRET=change-me-to-a-long-random-string
   export CORS_ALLOWED_ORIGINS=http://localhost:5173
   ```

   In PowerShell the equivalent is `$env:DB_URL = "jdbc:postgresql://localhost:5432/sonetopia"`, one line per variable.

3. Run the app:

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows use `mvnw.cmd`. The server listens on port 8080 unless `SERVER_PORT` is set.

4. Check it is up: `GET /health`. Swagger UI is at `/swagger-ui.html` and the OpenAPI document at `/v1/api-docs`.

5. Create your first admin user with the [admin commands](#admin-commands). Regular users sign up through `POST /api/auth/register`.

## Configuration

Everything is configured through environment variables (see `src/main/resources/application.properties`). Variables without a default are required.

### Core

| Variable | Default | Description |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | – | PostgreSQL connection |
| `AUDIO_STORAGE_PATH` | – | Directory where audio and covers are stored |
| `SIGNED_URL_SECRET` | – | Secret for signed media URLs |
| `JWT_SECRET` | – | Secret for signing access tokens. Changing it invalidates every access token |
| `CORS_ALLOWED_ORIGINS` | – | Comma-separated list of explicit origins (credentials are enabled, so no wildcard) |
| `SERVER_PORT` | `8080` | HTTP port |
| `USER_STORAGE_QUOTA_BYTES` | `1073741824` | Storage quota per user |
| `TEMP_AUDIO_TTL_MINUTES` | `60` | How long a fetched-but-unsaved audio is kept |
| `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE` | `100MB` / `105MB` | Upload limits (multipart uploads only, not URL import) |

### Authentication

| Variable | Default | Description |
|---|---|---|
| `JWT_EXPIRATION` | `3600000` | Access token lifetime in ms |
| `REFRESH_TOKEN_EXPIRATION` | `2592000000` | Refresh token lifetime in ms (30 days) |
| `REFRESH_COOKIE_SAME_SITE` | `Lax` | `SameSite` attribute of the refresh cookie |
| `EMAIL_VERIFICATION_EXPIRATION` | `86400000` | Verification link lifetime in ms |
| `GOOGLE_CLIENT_ID` | – | Google sign-in |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` | – | GitHub sign-in |
| `APPLE_ENABLED`, `APPLE_CLIENT_ID` | `false`, – | Apple sign-in |

Access tokens are JWTs sent as `Authorization: Bearer ...`. The refresh token lives in an `HttpOnly` cookie named `refresh_token` (path `/api/auth`) and is rotated on every `POST /api/auth/refresh`. Presenting an already-rotated token revokes that whole token family, which forces a new login.

### Email

| Variable | Default | Description |
|---|---|---|
| `MAIL_ENABLED` | `true` | Set to `false` to skip sending mail |
| `BREVO_SMTP_HOST`, `BREVO_SMTP_PORT` | `smtp-relay.brevo.com`, `587` | SMTP relay |
| `BREVO_SMTP_USERNAME`, `BREVO_SMTP_PASSWORD` | – | SMTP credentials |
| `MAIL_FROM`, `MAIL_FROM_NAME`, `MAIL_LOGO_URL` | see properties | Sender details |
| `EMAIL_VERIFICATION_URL` | `http://localhost:8080/api/auth/verify-email` | Link target in verification mails |
| `EMAIL_VERIFICATION_RESEND_COOLDOWN_SECONDS` | `60` | Minimum gap between resends |

### yt-dlp

| Variable | Default | Description |
|---|---|---|
| `YT_DLP_PATH` | `yt-dlp` | Executable to run |
| `YT_DLP_COOKIES_PATH` | – | Cookies file, helps against YouTube bot checks |
| `YT_DLP_TIMEOUT_SECONDS` | `180` | Max time for one download. Keep the reverse proxy timeout above this |
| `YT_DLP_MAX_FILESIZE_MIB` | `100` | Max size, applied to the source stream and to the resulting mp3 |
| `YT_DLP_MAX_DURATION_SECONDS` | `0` | Max duration, `0` means unlimited. Rejects long videos before downloading anything |

As a rule of thumb the mp3 output (VBR quality 0) is about 110 MB per hour of audio.

## Tests

The tests need a local PostgreSQL with a database named `sonetopia` and the credentials from `src/test/resources/application.properties` (`postgres` / `password`). They use their own schema, `sonetopia_test`, which Flyway creates and the tests wipe.

```bash
./mvnw test
```

Tests that need yt-dlp itself mock it, so yt-dlp is not required to run the suite.

## API overview

Authentication is required everywhere except `/health`, `/api/auth/**`, `/api/v1/media/**` (protected by signed URLs instead) and the Swagger/OpenAPI documentation.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `login`, `google`, `github`, `apple`, `refresh`, `logout`; `GET /api/auth/verify-email`; `POST /api/auth/resend-verification` |
| Me | `GET`, `PUT`, `DELETE /api/me`; `GET /api/me/storage` |
| Audio | `POST /api/v1/audio` (multipart upload, or JSON to save a fetched audio); `GET /api/v1/audio`; `GET`, `PUT`, `DELETE /api/v1/audio/{id}`; `GET /api/v1/audio/{id}/cover` |
| Import | `GET /api/v1/audio/search`; `POST /api/v1/audio/fetch` |
| Playlists | `POST`, `GET /api/v1/playlists`; `GET`, `PUT`, `DELETE /api/v1/playlists/{id}`; `GET /api/v1/playlists/{id}/audio`; `POST`, `DELETE /api/v1/playlists/{id}/audio/{audioId}`; `PUT /api/v1/playlists/{id}/audio/{audioId}/position/{newPosition}` |
| Media | `GET /api/v1/media/audio`, `GET /api/v1/media/cover` (signed URLs, returned as `signedUrl` by `GET /api/v1/audio/{id}`) |
| Misc | `GET /health` |

The Swagger UI has the request and response schemas.

## Importing audio from a URL

The flow has three steps, so the user can review the metadata before anything is saved:

1. **Search** (optional): `GET /api/v1/audio/search?q=...` returns URLs that can be fetched.
2. **Fetch**: `POST /api/v1/audio/fetch` with `{"url": "...", "with_thumbnail": true}` downloads the audio into a temporary folder and returns a `tempId` with the detected metadata. `with_thumbnail` is optional and defaults to `false`.
3. **Save**: `POST /api/v1/audio` with JSON `{"tempId": "...", "title": "...", "type": "music"}` moves the file into the library. Temporary files that are never saved are deleted after `TEMP_AUDIO_TTL_MINUTES`.

The canonical source URL is stored with the audio when it is saved. Different links to the same video (`music.youtube.com`, `youtube.com`, `youtu.be`, extra query parameters) count as the same source.

### Search

```
GET /api/v1/audio/search?q=coldplay&limit=10&offset=0&source=youtube_music
```

| Parameter | Default | Description |
|---|---|---|
| `q` | – | Search text, up to 200 characters |
| `limit` | `10` | Page size, 1 to 20 |
| `offset` | `0` | Where the page starts. Up to 50 results per query are available |
| `source` | – | `youtube_music`, `youtube` or `soundcloud` |

Without `source`, sources are tried in the order YouTube Music (songs only), YouTube, SoundCloud, and the first one with results wins. With `source`, only that source is used.

```json
{
  "data": {
    "source": "youtube_music",
    "offset": 0,
    "limit": 10,
    "hasMore": true,
    "results": [
      {
        "title": "Yellow",
        "url": "https://music.youtube.com/watch?v=9qnqYL0eNNI",
        "uploader": null,
        "duration": null,
        "thumbnail": "https://i.ytimg.com/vi/9qnqYL0eNNI/hqdefault.jpg",
        "source": "youtube_music",
        "inLibrary": true,
        "libraryAudioId": "0b8e9f0e-8a5e-4b52-9a8f-2f6a0c1d7e11"
      }
    ]
  },
  "errors": null
}
```

- **Load more:** while `hasMore` is `true`, request again with `offset` increased by `limit` and the `source` from the previous response. The first search fetches a window of 50 results and caches it for 5 minutes, so later pages are instant and consistent (yt-dlp returns results in a different order on every call, so each page cannot be fetched on its own).
- **`inLibrary` / `libraryAudioId`** tell whether the user already imported that URL.
- **`uploader` and `duration` are usually `null` for YouTube Music.** yt-dlp's fast search mode does not return them for that source. The artist and duration become known once the audio is fetched.

## Admin commands

Admin commands are options passed to the application jar. They run once the app has started, and **the app keeps running afterwards, so stop it with `Ctrl+C`** when the command prints its result.

| Option | What it does |
|---|---|
| `--create-admin` | Prompts for name, username, email and password, then creates an admin user whose email is already verified |
| `--promote-admin` | Prompts for a username and makes that existing user an admin. Does nothing if they already are |
| `--sync-audio-urls` | Backfills the source URL of audios imported before it was stored (details below) |
| `--dry-run` | Only valid with `--sync-audio-urls`: shows what would change without writing anything |

`--create-admin` and `--promote-admin` can be combined in one run. The password prompt is hidden when a real terminal is attached.

### Running a command locally

Bash:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--create-admin"
```

PowerShell splits an unquoted `-Dname.with.dots=...` at the first dot, which makes Maven fail with `Unknown lifecycle phase ".run.arguments=..."`. Quote the whole argument:

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--create-admin"
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--sync-audio-urls --dry-run"
```

Or stop PowerShell from parsing the rest of the line with `--%`:

```powershell
./mvnw --% spring-boot:run -Dspring-boot.run.arguments="--sync-audio-urls --dry-run"
```

Both forms were checked to pass the arguments through intact.

#### Environment variables in a terminal

The whole application starts before the command runs, so it needs the same environment variables as a normal run. A plain terminal does not see the `env` block of `.vscode/launch.json`; that block is only applied when the app is started from VS Code. Without it the app fails on startup with `'url' must start with "jdbc"`, because Spring uses the unresolved text `${DB_URL}` as the database URL.

Load the `env` block of the launch configuration into the current PowerShell session first (it understands the comments and trailing commas that `launch.json` allows):

```powershell
$raw = (Get-Content .vscode/launch.json -Raw) -replace '(?m)^\s*//.*$', '' -replace ',(\s*[}\]])', '$1'
$cfg = ($raw | ConvertFrom-Json).configurations | Where-Object { $_.name -eq 'Spring Boot - SonetopiaApiApplication' }
$cfg.env.PSObject.Properties | ForEach-Object { Set-Item -Path "env:$($_.Name)" -Value $_.Value }
```

Then run the command in the same window:

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=0 --sync-audio-urls --dry-run"
```

- The variables only exist in that PowerShell window. In a new window, load them again.
- Check `echo $env:DB_URL` first. It must start with `jdbc:`; if it is empty the loader did not run in the same session.
- `--server.port=0` avoids a clash when the dev server is already running.
- Starting the app applies any pending Flyway migrations to the database in `DB_URL`.
- Use local values only. Do not load a copy of the production environment: its database user, storage path and secrets do not belong on a dev machine (they will not even match the local database).
- The app keeps running after the command prints its result. Stop it with `Ctrl+C`.

### Running a command on the server

The command needs the same environment as the service, and it must not use the service's port. Pass `--server.port=0` so it picks a free one.

```bash
sudo -iu deploy
cd /opt/sonetopia-api

# Load the service's environment. Find the file with: systemctl cat sonetopia-api.service
set -a; source /path/to/the/EnvironmentFile; set +a

java -jar releases/sonetopia-api.jar --server.port=0 --create-admin
```

If sourcing the file fails because a value is not valid shell syntax, quote that value or export the variables by hand.

### Syncing source URLs of existing audios

New imports save their URL automatically. For audios imported earlier, `--sync-audio-urls` reads the URL that yt-dlp embedded in the mp3 tags (`purl`, falling back to `comment`) and stores it, so search can mark them as already in the library.

```bash
# 1. See what would change
java -jar releases/sonetopia-api.jar --server.port=0 --sync-audio-urls --dry-run

# 2. Apply it
java -jar releases/sonetopia-api.jar --server.port=0 --sync-audio-urls
```

The output lists one line per audio:

| Prefix | Meaning |
|---|---|
| `+` | URL found, will be (or was) stored |
| `-` | No URL in the tags, for example a manual upload. It stays empty |
| `!` | The file could not be read |

It only touches audios that have no URL yet, so it is safe to run again. Only a tag that is exactly one URL is used; free-text comments on uploaded files are ignored.

## Deployment

Production runs as the systemd service `sonetopia-api.service`, as user `deploy`, behind nginx (`127.0.0.1:8081`).

### Deploy a new version

```bash
sudo -iu deploy
cd /opt/sonetopia-api

git pull origin main
./mvnw clean package -DskipTests

# Optional: keep the running jar so a bad release can be rolled back
cp releases/sonetopia-api.jar releases/sonetopia-api.previous.jar

install -o deploy -g deploy -m 644 target/sonetopia-api-0.0.1-SNAPSHOT.jar releases/sonetopia-api.jar
sudo systemctl restart sonetopia-api.service
```

Then check that it came up:

```bash
sudo systemctl status sonetopia-api.service
sudo journalctl -u sonetopia-api.service -n 100 --no-pager
curl -s https://sonetopia-api.luckyabdillah.com/health
```

### Notes

- **Run `./mvnw test` on your own machine before pushing.** The deploy build skips tests (`-DskipTests`) because they need a local PostgreSQL.
- **Database migrations run on startup** (Flyway). The app refuses to start if the schema does not match the entities, so check the journal after every deploy that adds a migration.
- **Use `sudo` with `journalctl`.** Without it only your own user's messages are shown, so the service log looks empty.
- **Rolling back:** `install ... releases/sonetopia-api.previous.jar releases/sonetopia-api.jar`, then restart. Migrations are not reverted, so only roll back across releases that did not change the schema.
- **Environment variables** belong to the service. Find where with `systemctl cat sonetopia-api.service` (look for `EnvironmentFile=` or `Environment=`). After editing, run `sudo systemctl restart sonetopia-api.service`; if the unit file itself changed, run `sudo systemctl daemon-reload` first.
- **yt-dlp breaks when YouTube changes.** If search or import suddenly fails everywhere, update it. For the standalone binary: `sudo /usr/local/bin/yt-dlp -U`. Otherwise update it the same way it was installed. An expired cookies file (`YT_DLP_COOKIES_PATH`) causes the same symptoms.

### Values currently used on the VPS

For reference, so they do not have to be looked up again:

| Setting | Value |
|---|---|
| `YT_DLP_PATH` | `/usr/local/bin/yt-dlp` |
| `YT_DLP_COOKIES_PATH` | `/home/deploy/sonetopia/yt-dlp-cookies.txt` |
| `YT_DLP_TIMEOUT_SECONDS` | `900` |
| `YT_DLP_MAX_DURATION_SECONDS` | `14400` (4 hours) |
| `YT_DLP_MAX_FILESIZE_MIB` | `500` |
| nginx `proxy_read_timeout` | `960s` |
| nginx `client_max_body_size` | `105m` |

### nginx

The site config is `/etc/nginx/sites-available/sonetopia-api.luckyabdillah.com`.

- `client_max_body_size` only affects multipart uploads. Keep it at least as large as `MAX_REQUEST_SIZE`. URL import sends a tiny JSON body and is not affected.
- `proxy_read_timeout` must be larger than `YT_DLP_TIMEOUT_SECONDS`, otherwise nginx cuts a long import off with a 504 before the app can answer. The default is only 60 seconds.

```nginx
location / {
    proxy_pass http://127.0.0.1:8081;
    proxy_read_timeout 960s;
    # ...
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

After a reload, connections that are already open (for example a browser tab that has been open for a while) can keep using the old configuration. Reload the page, or run `sudo systemctl restart nginx`, before concluding that a change did not work.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `504` with an HTML page from nginx | nginx gave up waiting. Raise `proxy_read_timeout` above `YT_DLP_TIMEOUT_SECONDS` (see above) |
| `504` with `"Audio fetch timed out"` | The app's own timeout. Raise `YT_DLP_TIMEOUT_SECONDS`, and keep nginx above it |
| `422` `"Audio file must not exceed N MiB"` | The audio is over `YT_DLP_MAX_FILESIZE_MIB` |
| `422` `"Audio duration must not exceed N minutes"` | The video is over `YT_DLP_MAX_DURATION_SECONDS` |
| `422` `"Unable to fetch from the provided URL"` | yt-dlp failed for another reason. Look for `yt-dlp failed for` or `yt-dlp produced no audio file` in `sudo journalctl -u sonetopia-api.service`. Common causes are an outdated yt-dlp or expired cookies |
| `502` `"Search is currently unavailable"` | Every search source failed. Same checks as above |
| `413` from nginx | A multipart upload is larger than `client_max_body_size` |

To see the real error body of a failing request, use `curl -i` so the status line and body are both shown:

```bash
curl -i -X POST https://sonetopia-api.luckyabdillah.com/api/v1/audio/fetch \
  -H "Authorization: Bearer <access token>" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.youtube.com/watch?v=..."}'
```

A JSON body comes from the app. An HTML body comes from nginx.
