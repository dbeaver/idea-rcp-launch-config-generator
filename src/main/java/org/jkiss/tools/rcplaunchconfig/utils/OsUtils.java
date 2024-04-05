package org.jkiss.tools.rcplaunchconfig.utils;

import org.jkiss.tools.rcplaunchconfig.BundleInfo;

public class OsUtils {
    public static boolean matchesDeclaredOS(String ws, String os, String arch) {
        if (ws != null && !ws.equals(BundleInfo.currentWS)) {
            return true;
        }
        if (os != null && !os.equals(BundleInfo.currentOS)) {
            return true;
        }
        if (arch != null && !arch.equals(BundleInfo.currentArch)) {
            return true;
        }
        return false;
    }
}
