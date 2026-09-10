# ScreenSort — Package structure

Status: Active

ScreenSort is one deployable Spring Boot application organized as a modular monolith. Packages represent business capabilities first; technical layers live inside each capability.

## Root layout

~~~text
pl.pum.screensort
├── ScreenSortApplication
├── catalog
├── scanning
├── analysis
├── classification
├── review
├── search
├── organization
├── web
└── shared
~~~

**ScreenSortApplication** remains in the root package. Spring Boot uses its package as the default component, configuration, repository, and entity search boundary.

## Feature layout

A feature adds only the layers it needs:

~~~text
feature
├── domain
├── application
│   └── port
└── infrastructure
    ├── filesystem
    └── persistence
~~~

| Layer | Responsibility | May depend on |
|---|---|---|
| **domain** | Business values, rules and state | Java and small shared-domain primitives |
| **application** | Use cases and transaction boundaries | Its domain and application ports |
| **application.port** | Interfaces needed from external systems | Domain types |
| **infrastructure** | JPA, PostgreSQL, filesystem, OCR and other adapters | The port it implements and framework APIs |
| **web** | HTTP requests, form validation and view models | Application use cases |

Spring annotations such as **@Service** and **@Transactional** may appear on application orchestration classes. Domain classes should remain independent of Spring, JPA, MVC, filesystem implementations and analyzer libraries.

## Dependency direction

~~~text
web -> application -> domain
          |
          v
     application ports
          ^
          |
   infrastructure adapters
~~~

The arrow means “may call or depend on.” Infrastructure is selected and injected by Spring at runtime.

## Module responsibilities

| Package | Owns |
|---|---|
| **catalog** | File identity, metadata, checksums, duplicates and lifecycle |
| **scanning** | Allowed roots and safe discovery of source files |
| **analysis** | OCR, visual analysis, analyzer jobs and partial failures |
| **classification** | Categories, tags, safe names, confidence and explanations |
| **review** | Manual corrections and explicit approval |
| **search** | Queries over names, OCR text, categories, tags and status |
| **organization** | Copy planning, verified output, collision handling and recovery |
| **web** | Spring MVC and Thymeleaf user interface |
| **shared** | Small dependency-free values with genuine cross-feature ownership |

## Placement rules

- Put behavior in the feature that owns the business decision.
- Web controllers call application use cases, never Spring Data repositories.
- JPA entities stay under **infrastructure.persistence** and are not returned to the UI.
- Filesystem implementations stay under **infrastructure.filesystem**.
- Interfaces implemented by infrastructure stay under **application.port**.
- Features must not depend on another feature's infrastructure package.
- Prefer a feature's public application API when one feature needs another.
- Keep **shared** small; similarity alone is not enough reason to move code there.
- Do not create placeholder classes. A package receives real classes only when its slice is implemented.

## Current example

The **scanning** feature is the first implemented example:

~~~text
scanning
├── domain
│   ├── AllowedRoot
│   ├── DiscoveredImage
│   ├── ImageFormat
│   ├── SourcePathAccessException
│   └── UnsafeSourcePathException
├── application
│   ├── DiscoverImages
│   ├── RegisterAllowedRoot
│   └── port
│       ├── AllowedRootCatalog
│       ├── ImageDiscovery
│       └── SourcePathResolver
└── infrastructure
    ├── filesystem
    │   ├── LocalImageDiscovery
    │   └── LocalSourcePathResolver
    └── persistence
        ├── AllowedRootEntity
        ├── AllowedRootJpaRepository
        └── JpaAllowedRootCatalog
~~~

Tests mirror the production package when they need package-level access. Full Spring/PostgreSQL tests may remain in the root test package when they verify integration across layers.

## Resources

~~~text
src/main/resources
├── application.properties
├── application-local.properties
├── application-test.properties
├── application-demo.properties
├── db/migration
├── templates
└── static
~~~

Flyway migrations are append-only after they have been shared. Never edit an applied migration; add a new numbered migration instead.
