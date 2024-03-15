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
package org.jkiss.tools.rcplaunchconfig.producers;

import jakarta.annotation.Nonnull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DevPropertiesProducer {

    public static final List<String> PACKAGES_FOR_DEV_PROPERTIES = List.of(
        "org.jkiss",
        "io.cloudbeaver",
        "com.dbeaver",
        "swtbot-simple"
    );
    private static final String DEFAULT_CLASSPATH = "target/classes";

    public static @Nonnull Map<String, String> generateDevProperties(@Nonnull Iterable<BundleInfo> bundles) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var bundleInfo : bundles) {
            if (isBundleAcceptable(bundleInfo.getBundleName())) {
                result.put(bundleInfo.getBundleName(), generateValue(bundleInfo.getClasspathLibs()));
            }
        }
        result.put("@ignoredot@", Boolean.TRUE.toString());
        return result;
    }

    public static boolean isBundleAcceptable(@Nonnull String bundleName) {
        return !bundleName.startsWith("org.jkiss.bundle") &&
            PACKAGES_FOR_DEV_PROPERTIES.stream().anyMatch(bundleName::startsWith);
    }

    private static @Nonnull String generateValue(@Nonnull List<String> bundleClassPath) {
        return Stream.concat(Stream.of(DEFAULT_CLASSPATH), bundleClassPath.stream())
            .collect(Collectors.joining(","));
    }
}
