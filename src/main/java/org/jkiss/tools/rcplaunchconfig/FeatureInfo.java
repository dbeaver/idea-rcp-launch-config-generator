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

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class FeatureInfo implements ModuleInfo {

    private final String featureName;
    private final Path featureXmlFile;
    private final Set<BundleInfo> bundles = new LinkedHashSet<>();
    private final Set<FeatureInfo> features = new LinkedHashSet<>();

    public FeatureInfo(String featureName, File featureXmlFile) {
        this.featureName = featureName;
        this.featureXmlFile = featureXmlFile.toPath();
    }

    public String getFeatureName() {
        return featureName;
    }

    public Path getFeatureXmlFile() {
        return featureXmlFile;
    }

    public Set<BundleInfo> getBundles() {
        return bundles;
    }

    public Set<FeatureInfo> getFeatures() {
        return features;
    }

    public void addBundleDependency(BundleInfo bundleInfo) {
        bundles.add(bundleInfo);
    }

    public void addFeatureDependency(FeatureInfo featureInfo) {
        features.add(featureInfo);
    }

    @Override
    public String toString() {
        return "FeatureInfo[" + featureName + "]";
    }

    @Override
    public String getModuleName() {
        return getFeatureName();
    }

    @Override
    public Path getModuleFile() {
        return getFeatureXmlFile();
    }
}