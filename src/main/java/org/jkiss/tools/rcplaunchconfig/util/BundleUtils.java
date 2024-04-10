package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class BundleUtils {
    public static boolean matchesDeclaredOS(String ws, String os, String arch) {
        if (ws != null && !ws.equals(BundleInfo.currentWS)) {
            return false;
        }
        if (os != null && !os.equals(BundleInfo.currentOS)) {
            return false;
        }
        if (arch != null && !arch.equals(BundleInfo.currentArch)) {
            return false;
        }
        return true;
    }

    /**
     *
     * @param bundleName bundle name
     * @param cache repository registry cache
     * @return bundle with max version of remote bundles with same name
     */
    @NotNull
    public static Optional<RemoteP2BundleInfo> getMaxVersionRemoteBundle(@NotNull String bundleName, P2BundleLookupCache cache) {
        boolean max = !FileUtils.preferOlderBundles.contains(bundleName);
        Stream<RemoteP2BundleInfo> bundleStream = cache.getRemoteBundlesByNames().get(bundleName).stream();
        Optional<RemoteP2BundleInfo> remoteP2BundleInfo;
        if (max) {
            remoteP2BundleInfo = bundleStream.max(Comparator.comparing(o -> new BundleVersion(o.getBundleVersion())));
        } else {
            remoteP2BundleInfo = bundleStream.min(Comparator.comparing(o -> new BundleVersion(o.getBundleVersion())));
        }
        return remoteP2BundleInfo;
    }
}
