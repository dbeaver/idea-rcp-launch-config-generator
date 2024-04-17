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
