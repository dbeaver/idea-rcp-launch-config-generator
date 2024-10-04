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
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jkiss.tools.rcplaunchconfig.util.Version;
import org.jkiss.tools.rcplaunchconfig.util.VersionRange;
import org.jkiss.utils.Pair;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class Result {

    private final Map<String, Set<BundleInfo>> bundlesByNames = new LinkedHashMap<>();

    private final Map<String, FeatureInfo> resolvedFeatures = new LinkedHashMap<>();

    private Path osgiSplashPath = null;
    private String productName;
    private String productUID;
    private String productId;
    private String applicationId;

    private Path productPath;

    private final ProductLaunchArguments arguments = new ProductLaunchArguments();

    public FeatureInfo addResolvedFeature(@Nonnull String featureName, File featureXmlFile) {
        return resolvedFeatures.computeIfAbsent(featureName, s -> new FeatureInfo(featureName, featureXmlFile));
    }

    public void addBundle(@Nonnull BundleInfo bundleInfo) {
        String bundleName = bundleInfo.getBundleName();
        Set<BundleInfo> oldInfo = bundlesByNames.get(bundleName);
        if (oldInfo == null) {
            bundlesByNames.computeIfAbsent(bundleName, it -> new HashSet<>()).add(bundleInfo);
            return;
        }
        Optional<BundleInfo> oldBundle = oldInfo.stream().filter(it -> it.getBundleVersion().equals(bundleInfo.getBundleVersion())).findFirst();
        if (oldBundle.isEmpty()) {
            bundlesByNames.computeIfAbsent(bundleName, it -> new HashSet<>()).add(bundleInfo);
        } else if (oldBundle.get().getBundleVersion().equals(bundleInfo.getBundleVersion())) {
            // Do nothing
            if (bundleInfo.getStartLevel() != null && !Objects.equals(oldBundle.get().getStartLevel(), bundleInfo.getStartLevel())) {
                bundlesByNames.computeIfAbsent(bundleName, it -> new HashSet<>()).add(bundleInfo);
            }
        }
    }

    public boolean isFeatureResolved(@Nonnull String featureName) {
        return resolvedFeatures.containsKey(featureName);
    }

    public boolean isPluginResolved(@Nonnull String pluginName) {
        return bundlesByNames.containsKey(pluginName);
    }

    public @Nullable Set<BundleInfo> getBundlesByName(@Nonnull String name) {
        return bundlesByNames.get(name);
    }

    public @Nullable BundleInfo getBundleByInfoAndVersion(Pair<String, VersionRange> bundle) {
        if (bundlesByNames.get(bundle.getFirst()) == null) {
            return null;
        }
        return bundlesByNames.get(bundle.getFirst()).stream().filter(it -> VersionRange.
            isVersionsCompatible(bundle.getSecond(), new Version(it.getBundleVersion()))).findFirst().orElse(null);
    }

    public @Nonnull Map<String, Set<BundleInfo>> getBundlesByNames() {
        return bundlesByNames;
    }

    public Map<String, FeatureInfo> getResolvedFeatures() {
        return resolvedFeatures;
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

    public Path getProductPath() {
        return productPath;
    }

    public String getProductId() {
        return productId;
    }

    public String getApplicationId() {
        return applicationId;
    }


    @Nullable
    public String getWorkDir() {
        return workDir;
    }

    public ProductLaunchArguments getArguments() {
        return arguments;
    }

    @Nullable
    public String workDir;

    public void setWorkDir(@Nullable String workDir) {
        this.workDir = workDir;
    }

    public void setProductPath(Path productPath) {
        this.productPath = productPath;
    }

    public void setProductInfo(String productName, String uid, String id, String application) {
        this.productName = productName.replaceAll("\\s+", "");
        this.productUID = uid;
        this.productId = id;
        this.applicationId = application;
    }

    public static class ProductLaunchArguments {
        private String[] vmARGS;
        private String[] vmARGSMac;
        private String[] programARGS;

        private String[] getProgramARGSMacOS;

        public ProductLaunchArguments() {

        }

        public void setVmARGS(String[] vmARGS) {
            this.vmARGS = vmARGS;
        }

        public void setVmARGSMac(String[] vmARGSMac) {
            this.vmARGSMac = vmARGSMac;
        }

        public void setProgramARGS(String[] programARGS) {
            this.programARGS = programARGS;
        }

        public void setGetProgramARGSMacOS(String[] getProgramARGSMacOS) {
            this.getProgramARGSMacOS = getProgramARGSMacOS;
        }

        public String[] getVmARGS() {
            return vmARGS;
        }

        public String[] getVmARGSMac() {
            return vmARGSMac;
        }

        public String[] getProgramARGS() {
            return programARGS;
        }

        public String[] getGetProgramARGSMacOS() {
            return getProgramARGSMacOS;
        }
    }
}
