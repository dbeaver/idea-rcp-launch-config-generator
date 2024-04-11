/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.tools.rcplaunchconfig.resolvers;

import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;

public enum PackageChecker {
    INSTANCE;

    private final Set<String> excludedPackages = Set.of(
        "org.h2",
        "system.bundle"
    );
    private final List<String> excludedPackageGroups = List.of(
        "java.",
        "sun.",
        "javax.net",
        "javax.crypto",
        "javax.security",
        "javax.sql",
        "javax.naming",
        "javax.xml.",
        "javax.xml.stream",
        "javax.mail",
        "javax.servlet",
        "javax.activation",
        "javax.management",
        "javax.imageio",
        "javax.script",
        "org.xml.sax",
        "org.w3c.dom",
        "javax.crypto.interfaces"
    );

    public boolean isPackageExcluded(@Nonnull String bundleName) {
        return excludedPackages.contains(bundleName) || excludedPackageGroups.stream().anyMatch(bundleName::startsWith);
    }
}