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
package org.jkiss.tools.rcplaunchconfig.util;

public class Version implements Comparable<Version> {
    int major;
    int minor;
    int micro;
    String delta;

    public Version(String str) {
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
    public int compareTo(Version o) {
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
