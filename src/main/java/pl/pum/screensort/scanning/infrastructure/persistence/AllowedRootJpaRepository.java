package pl.pum.screensort.scanning.infrastructure.persistence;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

interface AllowedRootJpaRepository extends CrudRepository<AllowedRootEntity, Long> {

    Optional<AllowedRootEntity> findByCanonicalPath(String canonicalPath);

}
