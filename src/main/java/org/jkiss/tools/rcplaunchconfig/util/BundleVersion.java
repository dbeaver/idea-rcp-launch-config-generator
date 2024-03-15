package org.jkiss.tools.rcplaunchconfig.util;

public class BundleVersion implements Comparable<BundleVersion> {
    int major;
    int minor;
    int micro;
    String delta;

    public BundleVersion(String str) {
        int divPos1 = str.indexOf('.');
        if (divPos1 == -1) {
            major = Integer.parseInt(str);
        } else {
            major = Integer.parseInt(str.substring(0, divPos1));
            int divPos2 = str.indexOf('.', divPos1 + 1);
            if (divPos2 == -1) {
                minor = Integer.parseInt(str.substring(divPos1 + 1));
            } else {
                minor = Integer.parseInt(str.substring(divPos1 + 1, divPos2));
                int divPos3 = str.indexOf('.', divPos2 + 1);
                if (divPos3 == -1) {
                    micro = Integer.parseInt(str.substring(divPos2 + 1));
                } else {
                    micro = Integer.parseInt(str.substring(divPos2 + 1, divPos3));
                    delta = str.substring(divPos3 + 1);
                }
            }
        }
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + micro + (delta == null ? "" : "." + delta);
    }

    @Override
    public int compareTo(BundleVersion o) {
        int dif = major - o.major;
        if (dif != 0) return dif;
        dif = minor - o.minor;
        if (dif != 0) return dif;
        dif = micro - o.micro;
        if (dif != 0) return dif;
        // Delta?
        return 0;
    }
}
