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
import org.jkiss.tools.rcplaunchconfig.RemoteBundleInfo;

public class P2BundleLookupCache {
    private final MultiValuedMap<String, RemoteBundleInfo> remoteBundlesByNames = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<String, RemoteBundleInfo> remoteBundlesByExports = new ArrayListValuedHashMap<>();

    public P2BundleLookupCache() {
    }

    public MultiValuedMap<String, RemoteBundleInfo> getRemoteBundlesByNames() {
        return remoteBundlesByNames;
    }

    public MultiValuedMap<String, RemoteBundleInfo> getRemoteBundlesByExports() {
        return remoteBundlesByExports;
    }

    public void addRemoteBundle(RemoteBundleInfo remoteBundleInfo) {
        remoteBundlesByNames.put(remoteBundleInfo.getBundleName(), remoteBundleInfo);
        for (String exportPackage : remoteBundleInfo.getExportPackages()) {
            remoteBundlesByExports.put(exportPackage, remoteBundleInfo);
        }
    }
}
