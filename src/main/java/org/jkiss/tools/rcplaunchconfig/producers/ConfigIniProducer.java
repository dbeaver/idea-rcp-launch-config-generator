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
package org.jkiss.tools.rcplaunchconfig.producers;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.Params;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigIniProducer {

    public static final Logger log = LoggerFactory.getLogger(ConfigIniProducer.class);

    private static final String OSGI_FRAMEWORK_BUNDLE_NAME = "org.eclipse.osgi";

    public static Map<String, String> generateConfigIni(
        @Nullable Path osgiSplashPath,
        @Nonnull Collection<BundleInfo> bundles
    ) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (osgiSplashPath != null) {
            result.put("osgi.splashPath", getNormalizeFileReference(osgiSplashPath));
        }
        result.put("osgi.install.area", getNormalizeFileReference(PathsManager.INSTANCE.getEclipsePath()));
        result.put("osgi.bundles.defaultStartLevel", "4");
        result.put("osgi.configuration.cascaded", Boolean.FALSE.toString());
        result.put("osgi.bundles", getOsgiBundlesValue(bundles));
        result.put("osgi.framework", getNormalizeFileReference(getOsgiFrameworkValue()));
        return result;
    }

    private static @Nonnull String getOsgiBundlesValue(@Nonnull Collection<BundleInfo> bundles) {
        return bundles.stream()
            .map(ConfigIniProducer::getBundleReference)
            .collect(Collectors.joining(","));
    }

    private static @Nonnull String getBundleReference(@Nonnull BundleInfo bundleInfo) {
        var stringBuilder = new StringBuilder();
        stringBuilder.append("reference:");
        stringBuilder.append(getNormalizeFileReference(bundleInfo.getPath()));
        if (bundleInfo.getStartLevel() != null) {
            stringBuilder.append("@");
            stringBuilder.append(bundleInfo.getStartLevel());
            stringBuilder.append(":start");
        }
        return stringBuilder.toString();
    }

    private static @Nonnull String getBundleStartLevel(@Nonnull BundleInfo bundleInfo) {
        if (bundleInfo.getStartLevel() != null) {
            return "@" + bundleInfo.getStartLevel() + ":start";
        } else {
            return "@default:default";
        }
    }

    private static @Nonnull String getOsgiFrameworkValue() throws IOException {
        var eclipsePluginsPath = PathsManager.INSTANCE.getEclipsePluginsPath();
        var file = FileUtils.findFirstChildByPackageName(eclipsePluginsPath, OSGI_FRAMEWORK_BUNDLE_NAME);
        if (file == null) {
            log.error("Failed to find '{}' in '{}'", OSGI_FRAMEWORK_BUNDLE_NAME, eclipsePluginsPath);
            return "";
        }
        return file.getCanonicalPath();
    }

    private static @Nonnull String getNormalizeFileReference(@Nonnull Path path) {
        return getNormalizeFileReference(path.toString());
    }

    private static @Nonnull String getNormalizeFileReference(@Nonnull String path) {
        return "file:" + path.replace('\\', '/');
    }

    public static String generateProductLaunch(Params params, Result result) {
        StringBuilder lstr = new StringBuilder();
        lstr.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
            "<launchConfiguration type=\"org.eclipse.pde.ui.RuntimeWorkbench\">\n");

        lstr.append(
            "    <stringAttribute key=\"application\" value=\"" + result.getApplicationId() + "\"/>\n" +
                "    <stringAttribute key=\"configLocation\" value=\"${workspace_loc}/.metadata/.plugins/org.eclipse.pde.core/" + result.getProductName() + ".product\"/>\n" +
                "    <stringAttribute key=\"location\" value=\"${workspace_loc}/../runtime-" + result.getProductName() + ".product\"/>\n" +
                "    <stringAttribute key=\"product\" value=\"" + result.getProductId() + "\"/>\n");
        //"    <stringAttribute key=\"productFile\" value=\"\\" + params.productFilePath.getFileName().toString() + "\"/>")

        lstr.append(
            "    <booleanAttribute key=\"append.args\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"askclear\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"automaticAdd\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"automaticValidate\" value=\"true\"/>\n" +
                "    <stringAttribute key=\"bootstrap\" value=\"\"/>\n" +
                "    <stringAttribute key=\"checked\" value=\"[NONE]\"/>\n" +
                "    <booleanAttribute key=\"clearConfig\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"clearws\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"clearwslog\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"default\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"includeOptional\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.debug.core.ATTR_FORCE_SYSTEM_CONSOLE_ENCODING\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_ATTR_USE_ARGFILE\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_SHOW_CODEDETAILS_IN_EXCEPTION_MESSAGES\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_USE_START_ON_FIRST_THREAD\" value=\"true\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.JRE_CONTAINER\" value=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-17\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.PROGRAM_ARGUMENTS\" value=\"-os ${target.os} -ws ${target.ws} -arch ${target.arch} -nl ${target.nl} -consoleLog -showsplash\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.SOURCE_PATH_PROVIDER\" value=\"org.eclipse.pde.ui.workbenchClasspathProvider\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.VM_ARGUMENTS\" value=\"-XX:+IgnoreUnrecognizedVMOptions -Dfile.encoding=UTF-8 --add-modules=ALL-SYSTEM -Xms64m -Xmx1024m\"/>\n" +
                "    <stringAttribute key=\"pde.version\" value=\"3.3\"/>\n" +
                "    <booleanAttribute key=\"show_selected_only\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"tracing\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"useCustomFeatures\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"useDefaultConfig\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"useDefaultConfigArea\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"useProduct\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"usefeatures\" value=\"false\"/>\n");
        lstr.append("    <setAttribute key=\"selected_target_bundles\">\n");
        for (var bundleInfo : result.getBundlesByNames().values()) {
            if (!DevPropertiesProducer.isBundleAcceptable(bundleInfo.getBundleName())) {
                lstr.append("        <setEntry value=\"").append(bundleInfo.getBundleName()).append(getBundleStartLevel(bundleInfo)).append("\"/>\n");
            }
        }
        lstr.append("    </setAttribute>\n");
        lstr.append("    <setAttribute key=\"selected_workspace_bundles\">\n");
        for (var bundleInfo : result.getBundlesByNames().values()) {
            if (DevPropertiesProducer.isBundleAcceptable(bundleInfo.getBundleName())) {
                lstr.append("        <setEntry value=\"").append(bundleInfo.getBundleName()).append(getBundleStartLevel(bundleInfo)).append("\"/>\n");
            }
        }

        lstr.append("    </setAttribute>\n");
        lstr.append("</launchConfiguration>\n");
        return lstr.toString();
    }
}
