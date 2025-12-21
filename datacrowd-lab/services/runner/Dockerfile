# --- build stage ---
FROM golang:1.22-alpine AS build
WORKDIR /app
COPY go.mod ./
COPY . .
RUN go build -o runner .

# --- run stage ---
FROM alpine:3.20
WORKDIR /app
COPY --from=build /app/runner ./runner

ENV RUNNER_PORT=8090
EXPOSE 8090
ENTRYPOINT ["./runner"]
