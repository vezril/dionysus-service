# dionysus-service

_TODO: describe dionysus-service_

## Status

Early scaffolding — nothing to see yet.

## Getting started

- **Layout**: `core/` (pure domain, no Pekko) and `server/` (Pekko HTTP runtime + Docker).
- **Endpoints**: `GET /` → hello; `GET /health` → status/service/version JSON (503 during shutdown).
- **Develop**: `sbt compile`, `sbt test`, `sbt server/run` (port 8080, `HTTP_PORT` overrides),
  `sbt scalafmtAll`.
- **Docker**: `sbt server/Docker/publishLocal`, then `docker run -p 8080:8080 calvinference/dionysus:<version>`.
- **CI/CD**: ci.yml on PRs; dev.yml publishes `:dev` images from `development`; release.yml
  publishes `:X.Y.Z` + `:latest` from a `vX.Y.Z` tag; versioning is git-tag-driven
  (sbt-dynver); image publishing needs the `DOCKERHUB_*` secrets (skipped gracefully if absent).

## License

MIT — see [LICENSE.md](LICENSE.md).
