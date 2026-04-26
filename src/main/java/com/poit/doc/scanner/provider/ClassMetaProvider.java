package com.poit.doc.scanner.provider;

import java.util.List;
import java.util.Optional;

/**
 * Unified class metadata abstraction — single entry point for field/annotation/generic info.
 */
public interface ClassMetaProvider {

    Optional<ClassMeta> find(String fqn);
}
