# Read Me First
The following was discovered as part of building this project:

* The original package name 'com.balaji.sync-engine' is invalid and this project uses 'com.balaji.sync_engine' instead.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.1/reference/using/devtools.html)
* [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

# ADR 0002: Pull cursor is the server's ingestion sequence

**Status:** Accepted

**Context:** Pull originally used the event's own HLC as the cursor. Offline
devices push events older than checkpoints other devices already moved past,
so those events were never delivered. Pagination also mixed two orderings.

**Decision:** change_event.server_seq (bigserial) is the cursor. Appends are
serialized by a Postgres transaction-scoped advisory lock, so sequence order
equals commit order and a reader can never see a higher number before a lower
one. HLC remains the tool for "which write is later"; the sequence answers
"what has this device not received yet".

**Consequences:** Delivery is gapless and ordered. Appends are single-lane,
which is fine at this scale. Gaps in the numbering (rolled-back transactions)
are harmless. Scaling past one lane would need a transaction-horizon cursor
or a log broker. A brand-new device still replays full history (snapshot sync
is a known limitation).
