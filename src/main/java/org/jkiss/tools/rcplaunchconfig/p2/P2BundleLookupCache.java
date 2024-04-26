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

package org.jkiss.tools.rcplaunchconfig.p2;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;

import java.util.Collection;

public class P2BundleLookupCache {
    private final MultiValuedMap<String, RemoteP2BundleInfo> remoteBundlesByNames = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<String, RemoteP2Feature> remoteFeaturesByNames = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<String, RemoteP2BundleInfo> remoteBundlesByExports = new ArrayListValuedHashMap<>();

    public P2BundleLookupCache() {
    }

    public Collection<RemoteP2BundleInfo> getRemoteBundlesByName(String name) {
        return remoteBundlesByNames.get(name);
    }

    public Collection<RemoteP2Feature> getRemoteFeaturesByName(String name) {
        return remoteFeaturesByNames.get(name);
    }

    public Collection<RemoteP2BundleInfo> getRemoteBundlesByExport(String export) {
        return remoteBundlesByExports.get(export);
    }

    public void addRemoteBundle(RemoteP2BundleInfo remoteP2BundleInfo) {
        remoteBundlesByNames.put(remoteP2BundleInfo.getBundleName(), remoteP2BundleInfo);
        for (String exportPackage : remoteP2BundleInfo.getExportPackages()) {
            remoteBundlesByExports.put(exportPackage, remoteP2BundleInfo);
        }
    }

    public void addRemoteFeature(RemoteP2Feature feature) {
        remoteFeaturesByNames.put(feature.name, feature);
    }
}
