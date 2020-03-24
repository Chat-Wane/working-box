# Working Box

This project is a simple service that burns resources during a
configurable time and call other services doing the same. They report
their execution to a [Jaeger tracing
service](https://www.jaegertracing.io/). This builds a configurable
workflow that can be checked, and used at runtime or postmortem. Along
with energy monitoring of services, another service may be able to map
the consumption of services to input parameters.

## Usage

Either by modifying the ```application.properties``` file or
environment variable. The following command starts the
service ```working-box1```
(names must be unique for tracing purposes), with a configurable time (ms)
depending on the input parameter ```x``` such
that ```f(x)= 1000 + 10x + 100x²```. 
At 80 percent of the execution time, the service calls two other services
with the same input ```x```. 

```
JAEGER_ENDPOINT=http://192.168.99.100:14268/api/traces \
SPRING_APPLICATION_NAME=working-box1 SERVER_PORT=8080 \
BOX_POLYNOME_COEFFICIENTS=1000.,10.,100. \
BOX_REMOTE_CALLS=http://localhost:8081@80,http://localhost:8082@80 \
mvn spring-boot:run
```

To call this service: ```curl "http://localhost:8080?x=10"```

By chaining boxes, the result on Jaeger looks like the screenshot
below. A first box calls two other boxes at 80% of its 1s workflow. A
second box executes its workflow during ~100s. A third box calls a
fourth one at 80% of its 11s workflow. The forth box execution time is
5s.

![Monitoring containers](img/screenshot.png)
