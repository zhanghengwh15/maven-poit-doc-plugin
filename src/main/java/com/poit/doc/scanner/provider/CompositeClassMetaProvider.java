package com.poit.doc.scanner.provider;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Composite ClassMetaProvider that chains QDox -> ASM -> Reflection with caching and source logging.
 */
public final class CompositeClassMetaProvider implements ClassMetaProvider {

    private static final Logger LOG = Logger.getLogger(CompositeClassMetaProvider.class.getName());

    private final ClassMetaProvider qdoxProvider;
    private final ClassMetaProvider asmProvider;
    private final ClassMetaProvider reflectionProvider;
    private final ConcurrentHashMap<String, Optional<ClassMeta>> cache = new ConcurrentHashMap<>();

    public CompositeClassMetaProvider(ClassMetaProvider qdoxProvider, ClassMetaProvider asmProvider,
            ClassMetaProvider reflectionProvider) {
        this.qdoxProvider = qdoxProvider;
        this.asmProvider = asmProvider;
        this.reflectionProvider = reflectionProvider;
    }

    @Override
    public Optional<ClassMeta> find(String fqn) {
        return cache.computeIfAbsent(fqn, key -> resolveWithSource(key));
    }

    private Optional<ClassMeta> resolveWithSource(String fqn) {
        Optional<ClassMeta> result = qdoxProvider.find(fqn);
        if (result.isPresent()) {
            LOG.fine(() -> "ClassMeta hit QDox for: " + fqn);
            return result;
        }

        result = asmProvider.find(fqn);
        if (result.isPresent()) {
            LOG.fine(() -> "ClassMeta hit ASM for: " + fqn);
            return result;
        }

        result = reflectionProvider.find(fqn);
        if (result.isPresent()) {
            LOG.fine(() -> "ClassMeta hit Reflection for: " + fqn);
            return result;
        }

        LOG.warning(() -> "ClassMeta miss for: " + fqn);
        return Optional.empty();
    }

    /**
     * Clear the cache (useful for testing).
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Return cache size for diagnostics.
     */
    public int cacheSize() {
        return cache.size();
    }
}
