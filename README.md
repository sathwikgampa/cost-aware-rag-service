# Cost-Aware RAG Service

A beginner-friendly Java project that demonstrates a small Retrieval-Augmented Generation (RAG) service with local language models, semantic answer caching, simple model routing, and request metrics.

## What the application does

When you ask a question, the application:

1. Converts the question into an embedding using Ollama's local embedding model.
2. Checks Redis for a previously generated answer to a question with similar meaning.
3. If there is no cache hit, compares the question with the notes in `src/main/resources/knowledge/rag-notes.txt` and retrieves the closest relevant note.
4. If no note is sufficiently relevant, returns `I don't know based on the available notes.` without asking a language model.
5. For a supported question, chooses a small or large local model using a simple rule, then asks Ollama to generate an answer using the retrieved note as context.
6. Saves the answer in Redis and records request, cache, token, and latency metrics.

This is an educational MVP. Its router uses simple question-length and keyword rules; it does not calculate a dollar cost or dynamically optimize routing. Latency and token counts are useful local compute proxies, not a bill or a direct measure of money saved.

## What was installed or downloaded

- **Java Development Kit (JDK):** runs the Java application. The Maven configuration targets Java 21. The machine used during development reported Java 26; use a compatible installed JDK when running the project.
- **Maven Wrapper (`mvnw.cmd`):** included in the project. It downloads and runs the Maven version needed by the project, so a separate Maven installation is not required.
- **Docker Desktop:** runs Redis in a container.
- **Redis 7 Alpine Docker image:** a small Redis image used to store semantic cache entries. The project container is named `rag-redis`; host port `6380` maps to Redis container port `6379` because another Redis container was already using host port `6379`.
- **Ollama:** runs the models on your own computer and exposes a local HTTP API at `http://localhost:11434`. The Java app calls this local API; it does not call a hosted model provider or require a hosted API key.
- **`nomic-embed-text`:** Ollama embedding model used to represent questions and notes as vectors for similarity comparisons. Its embeddings have 768 values in the setup checked during development.
- **`qwen3:0.6b`:** the small local chat model, selected for simpler questions.
- **`qwen3:1.7b`:** the larger local chat model, selected by the current heuristic for longer or more complex questions.
- **`qwen3:4b`:** also downloaded during setup, but the current application does not select it. The smaller two chat models are configured in the application.

The application uses Spring Boot with Web MVC for HTTP endpoints, Spring Data Redis for Redis access, and Actuator for health information.

## Prerequisites

Before starting the app, make sure:

- You are using PowerShell from the project directory.
- A JDK is installed. The project is configured for Java 21; check your installed version with `java -version`.
- Docker Desktop is open and its engine is running.
- Ollama is installed and running.

Pull the models used by the current application (each model only needs to be downloaded once):

```powershell
ollama pull nomic-embed-text
ollama pull qwen3:0.6b
ollama pull qwen3:1.7b
```

You can check that Ollama sees them with:

```powershell
ollama list
```

The earlier `qwen3:4b` download is optional for this code; it is not currently used by the model router.

## Start Redis

From PowerShell, start the Redis container:

```powershell
docker run -d --name rag-redis -p 6380:6379 redis:7-alpine
```

What this command means:

- `docker run` creates and starts a container.
- `-d` runs it in the background.
- `--name rag-redis` gives this project's container a memorable name.
- `-p 6380:6379` maps port `6380` on your computer to Redis's port `6379` inside the container.
- `redis:7-alpine` is the Redis 7 image with a compact Alpine Linux base.

The Spring application is configured to connect to Redis at `localhost:6380` in `src/main/resources/application.properties`.

Verify Redis is ready:

```powershell
docker exec rag-redis redis-cli ping
```

Expected response:

```text
PONG
```

If the container already exists but is stopped, start it instead of creating another one:

```powershell
docker start rag-redis
```

To see whether it is running:

```powershell
docker ps
```

## Build and run the Spring Boot app

Open PowerShell in the folder containing `pom.xml`, then compile the project:

```powershell
.\mvnw.cmd compile
```

`compile` asks the Maven Wrapper to compile the Java source code and report build errors. It does not start the web server.

Start the application:

```powershell
.\mvnw.cmd spring-boot:run
```

This command starts Spring Boot and keeps the terminal attached to the running server. Leave this terminal open while using the app. Press `Ctrl+C` in that terminal to stop Spring Boot.

The first run can take longer while Maven downloads project dependencies. The first request to a model can also take longer while Ollama loads it into memory. Your setup runs the local models on CPU, so the larger model can take several seconds per request.

## Try the application

Open the dashboard in a browser:

- [http://localhost:8080/](http://localhost:8080/)

The dashboard lets you ask questions and view the in-process metrics. It refreshes the metrics automatically.

You can also try these endpoints directly:

- Ask a question: [http://localhost:8080/api/ask?question=What%20is%20RAG](http://localhost:8080/api/ask?question=What%20is%20RAG)
- See the routing decision: [http://localhost:8080/api/route?question=What%20is%20RAG](http://localhost:8080/api/route?question=What%20is%20RAG)
- View metrics: [http://localhost:8080/api/metrics](http://localhost:8080/api/metrics)
- Check Spring Boot health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Try a question supported by the notes, such as `What is RAG?` or `What is semantic caching?`. Then ask a paraphrase, such as `Would similar questions reuse a prior answer?`, to see whether the semantic cache recognizes it. Ask an unrelated question to see the no-matching-note response.

A cache hit is indicated by `"cacheHit": true` and typically `"selectedModel": "cache"`. A cache hit still needs an embedding request and Redis lookup, so its elapsed time may be above zero. It avoids another chat-model generation when the cache finds a sufficiently similar question.

## Endpoints and response fields

### `GET /api/ask?question=...`

Retrieves a relevant note, checks the semantic cache, routes supported cache misses, calls Ollama if needed, and returns an answer and usage information. The response includes fields such as:

- `answer`: generated, cached, or no-match answer.
- `retrievedFact`: the selected note, cache-hit marker, or no-match marker.
- `selectedModel`: `small`, `large`, `cache`, or `none`.
- `modelName`: the Ollama model name, `semantic-cache`, or `not-called`.
- `totalDurationMs`: measured request time.
- `modelLoadMs`, `promptTokens`, `generatedTokens`: Ollama-provided details for generation calls; these are zero on cache hits or when no model is called.
- `cacheHit`: whether the answer came from the semantic cache.

### `GET /api/route?question=...`

Shows the current heuristic's routing decision without generating an answer. Longer questions and questions containing terms such as `compare`, `step by step`, or `why` are routed to `large`; other questions generally go to `small`.

### `GET /api/metrics`

Shows aggregate request counts, cache hits, model calls, token totals, average latency, and cache hit rate since the Spring app started.

## Where to look in the source

- `src/main/java/com/example/cost_aware_rag/HelloController.java` — HTTP endpoints and request flow.
- `src/main/java/com/example/cost_aware_rag/KnowledgeService.java` — loads notes, creates embeddings, and finds a relevant note.
- `src/main/java/com/example/cost_aware_rag/EmbeddingClient.java` — calls Ollama's local embedding API.
- `src/main/java/com/example/cost_aware_rag/OllamaClient.java` — calls Ollama's local chat API and reads timing/token details.
- `src/main/java/com/example/cost_aware_rag/ModelRouter.java` — the small-versus-large model heuristic.
- `src/main/java/com/example/cost_aware_rag/SemanticAnswerCache.java` — stores and searches cached questions and answers in Redis.
- `src/main/java/com/example/cost_aware_rag/UsageMetricsService.java` — in-memory metrics.
- `src/main/resources/knowledge/rag-notes.txt` — the knowledge source used for retrieval.
- `src/main/resources/application.properties` — application name and Redis connection settings.
- `src/main/resources/static/index.html` — browser dashboard.
- `pom.xml` — Java version and Spring Boot dependencies.

## Current MVP limits

- Model selection is based on a simple heuristic, not an intelligent or cost-optimized policy.
- The app uses local Ollama models. It does not currently use a paid hosted LLM API or calculate hosted API charges.
- Semantic cache similarity uses a configured threshold of `0.80`; knowledge retrieval uses `0.70`. These are project settings to tune with examples, not universal values.
- Cache search compares against up to 500 Redis entries, so it is a learning-scale approach rather than a scalable vector database.
- Cache entries expire after 24 hours, and the cache index is capped at 500 entries.
- Metrics are held in application memory and reset when Spring Boot restarts.
- Redis and Ollama need to be running for requests that use embeddings or cached answers.
- The API is for local development and learning. It has no authentication, and GET request questions can appear in browser history or server logs; do not expose it publicly as-is.

## Troubleshooting

- **`docker` cannot connect to the daemon:** open Docker Desktop and wait until its engine is running.
- **Port 6380 is already allocated:** check whether another process or container is using it. If you change the host port, update `spring.data.redis.port` in `src/main/resources/application.properties` to match.
- **Redis connection failure:** check `docker ps` and run `docker exec rag-redis redis-cli ping`.
- **Ollama connection failure:** confirm Ollama is running and that `ollama list` includes the models above. Its local API should be available at `http://localhost:11434`.
- **A model is missing:** pull the exact model name configured in the application with `ollama pull <model-name>`.
- **The answer is slow:** local CPU model generation, especially the first request after model load, can take seconds. Cache hits avoid chat generation but still perform embedding and cache lookup work.
- **The app fails at startup:** read the first `Caused by:` section in the Spring error output; it usually names the missing file, dependency, or service.
