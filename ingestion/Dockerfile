FROM golang:1.27-alpine AS build
WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/ingestion ./cmd/ingestion

FROM scratch
COPY --from=build /out/ingestion /ingestion
USER 65532:65532
ENTRYPOINT ["/ingestion"]
