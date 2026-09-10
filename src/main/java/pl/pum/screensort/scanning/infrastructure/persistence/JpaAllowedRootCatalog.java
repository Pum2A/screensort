package pl.pum.screensort.scanning.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pl.pum.screensort.scanning.application.port.AllowedRootCatalog;
import pl.pum.screensort.scanning.domain.AllowedRoot;

@Repository
class JpaAllowedRootCatalog implements AllowedRootCatalog {

    private final AllowedRootJpaRepository repository;

    JpaAllowedRootCatalog(AllowedRootJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AllowedRoot save(AllowedRoot allowedRoot) {
        AllowedRootEntity entity = this.repository.findByCanonicalPath(allowedRoot.canonicalPath().toString())
                .orElseGet(() -> new AllowedRootEntity(allowedRoot));
        entity.updateFrom(allowedRoot);
        return this.repository.save(entity).toDomain();
    }

}
