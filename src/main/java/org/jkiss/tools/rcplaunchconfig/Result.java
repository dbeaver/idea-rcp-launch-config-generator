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
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.nio.file.Path;
import java.util.*;

public class Result {

    private final Map<String, BundleInfo> bundlesByNames = new LinkedHashMap<>();

    private final Set<String> resolvedFeatures = new LinkedHashSet<>();

    private Path osgiSplashPath = null;
    private String productName;
    private String productUID;
    private String productId;
    private String applicationId;

    public void addResolvedFeature(@Nonnull String featureName) {
        resolvedFeatures.add(featureName);
    }

    public void addBundle(@Nonnull BundleInfo bundleInfo) {
        String bundleName = bundleInfo.getBundleName();
        BundleInfo oldInfo = bundlesByNames.get(bundleName);
        if (oldInfo == null) {
            bundlesByNames.put(bundleName, bundleInfo);
        } else if (oldInfo.getBundleVersion().equals(bundleInfo.getBundleVersion())) {
            // Do nothing
            if (bundleInfo.getStartLevel() != null && !Objects.equals(oldInfo.getStartLevel(), bundleInfo.getStartLevel())) {
                bundlesByNames.put(bundleName, bundleInfo);
            }
        } else {
            // Multiple bundle versions
            bundlesByNames.put(bundleName + "_" + bundleInfo.getBundleVersion(), bundleInfo);
        }
    }

    public boolean isFeatureResolved(@Nonnull String featureName) {
        return resolvedFeatures.contains(featureName);
    }

    public boolean isPluginResolved(@Nonnull String pluginName) {
        return bundlesByNames.containsKey(pluginName);
    }

    public @Nullable BundleInfo getBundleByName(@Nonnull String name) {
        return bundlesByNames.get(name);
    }

    public @Nonnull Map<String, BundleInfo> getBundlesByNames() {
        return bundlesByNames;
    }

    public @Nullable Path getOsgiSplashPath() {
        return osgiSplashPath;
    }

    public void setOsgiSplashPath(@Nonnull Path osgiSplashPath) {
        this.osgiSplashPath = osgiSplashPath;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductUID() {
        return productUID;
    }

    public String getProductId() {
        return productId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setProductInfo(String productName, String uid, String id, String application) {
        this.productName = productName.replaceAll("\\s+", "");
        this.productUID = uid;
        this.productId = id;
        this.applicationId = application;
    }
}
