package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.RemoteP2Feature;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.utils.Pair;

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
    public static Optional<RemoteP2BundleInfo> getMaxVersionRemoteBundle(@NotNull Pair<String, VersionRange> bundleName, P2BundleLookupCache cache) {
        boolean max = !FileUtils.preferOlderBundles.contains(bundleName.toString());
        Stream<RemoteP2BundleInfo> bundleStream = cache.getRemoteBundlesByName(bundleName.getFirst()).stream().filter(it -> VersionRange.isVersionsCompatible(bundleName.getSecond(), new Version(it.getBundleVersion())));
        Optional<RemoteP2BundleInfo> remoteP2BundleInfo;
        if (max) {
            remoteP2BundleInfo = bundleStream.max(Comparator.comparing(o -> new Version(o.getBundleVersion())));
        } else {
            remoteP2BundleInfo = bundleStream.min(Comparator.comparing(o -> new Version(o.getBundleVersion())));
        }
        return remoteP2BundleInfo;
    }

    @NotNull
    public static Optional<RemoteP2Feature> getMaxVersionRemoteFeature(@NotNull String bundleName, P2BundleLookupCache cache) {
        boolean max = !FileUtils.preferOlderBundles.contains(bundleName);
        Stream<RemoteP2Feature> bundleStream = cache.getRemoteFeaturesByName(bundleName).stream();
        Optional<RemoteP2Feature> remoteP2BundleInfo;
        if (max) {
            remoteP2BundleInfo = bundleStream.max(Comparator.comparing(o -> new Version(o.getVersion())));
        } else {
            remoteP2BundleInfo = bundleStream.min(Comparator.comparing(o -> new Version(o.getVersion())));
        }
        return remoteP2BundleInfo;
    }

    public static boolean isRemoteBundleVersionGreater(RemoteP2BundleInfo maxVersionRemoteBundle, BundleInfo bundleInfo) {
        int i = new Version(maxVersionRemoteBundle.getBundleVersion()).compareTo(new Version(bundleInfo.getBundleVersion()));
        return i > 0;
    }

    public static boolean isRemoteFeatureVersionGreater(RemoteP2Feature maxVersionRemoteBundle, Version featureVersion) {
        int i = new Version(maxVersionRemoteBundle.getVersion()).compareTo(featureVersion);
        return i > 0;
    }
}
