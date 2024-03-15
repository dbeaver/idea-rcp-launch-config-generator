/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package org.jkiss.tools.rcplaunchconfig.resolvers;

import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;

public enum PackageChecker {
    INSTANCE;

    private final Set<String> excludedPackages = Set.of(
        "javax.net",
        "javax.crypto",
        "javax.security",
        "javax.sql",
        "javax.naming",
        "javax.xml.",
        "javax.management",
        "javax.imageio",
        "javax.script",
        "javax.crypto.interfaces",
        "org.h2",
        "system.bundle"
    );
    private final List<String> excludedPackageGroups = List.of(
        "java.",
        "sun."
    );

    public boolean isPackageExcluded(@Nonnull String bundleName) {
        return excludedPackages.contains(bundleName) || excludedPackageGroups.stream().anyMatch(bundleName::startsWith);
    }
}