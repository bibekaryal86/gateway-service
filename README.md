# gateway-service

A small utility for proxying of HTTP traffic. It is a high-performance proxy server built to act as an intermediary
between requesting clients and requested servers. This forwards requests and responses between the application
seamlessly. In addition, this utility now also supports database proxying, enabling secure and efficient query 
forwarding. Its flexible architecture allows it to mediate both application-layer and data-layer 
interactions with minimal configuration.

### Key Features

* Netty, OkHttp, Security, Customizable
* Built-in resilience features (Circuit Breakers and Rate Limiters) and logging
* Request/Response forwarding
* Database Proxy
* Dynamic configuration

This proxy application is ideal for scenarios such as load balancing, request inspection, or adding middleware logic
between clients and backend systems. Or like in my case - to overcome CORS issue with React/Next.js SPA for hobby
projects. Also good for resource constraints because this has small CPU/RAM footprint.

### Local Development

* Navigate to project root
* `./gradlew run`
* The `run` process reads environment variables from gcp folder's `app-credentials.yaml` file
* There is an example `app-credentials_DUMMY.yaml` file provided, create `app-credentials.yaml` file and update values
* These environment variables are checked during application start, and if not present the application won't start
* During the build process, these variables are used in flyway and bootrun scripts

### TODO

* Update this README.md for proper documentation
* Implement unit/integration tests
