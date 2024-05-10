package org.jkiss.tools.rcplaunchconfig.producers.iml;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.*;

public class IMLConfigurationProducer {
    public static final IMLConfigurationProducer INSTANCE = new IMLConfigurationProducer();
    private final HashMap<String, Set<BundleInfo>> bundlePackageImports = new LinkedHashMap<>();
    public void generateIMLFiles(Result result) throws IOException {
        List<BundleInfo> modules = new ArrayList<>();
        for (BundleInfo bundleInfo : result.getBundlesByNames().values()) {
            if (getImlModulePath(bundleInfo).toFile().exists()) {
                return;
            }
            if (DevPropertiesProducer.isBundleAcceptable(bundleInfo.getBundleName())) {
                String moduleConfig = generateIMLBundleConfig(bundleInfo);
                modules.add(bundleInfo);
                Path imlFilePath = getImlModulePath(bundleInfo);
                createConfigFile(imlFilePath, moduleConfig);
            } else {
                Path imlLibrariesPath = getLibrariesPath(bundleInfo);
                String libraryConfig = generateIMlLibraryConfig(bundleInfo, result);
                createConfigFile(imlLibrariesPath, libraryConfig);
            }
        }

        String modulesConfig = generateModulesConfig(modules);
        createConfigFile(getImplModuleConfigPath(), modulesConfig);
    }

    public void AddRequiredBundleforPackage(String packageName, BundleInfo bundleInfo) {
        bundlePackageImports.computeIfAbsent(packageName, it -> new HashSet<>()).add(bundleInfo);
    }

    private static void createConfigFile(Path imlLibrariesPath, String libraryConfig) throws IOException {
        boolean fileCreated = imlLibrariesPath.toFile().createNewFile();
        if (fileCreated) {
            try (PrintWriter out = new PrintWriter(imlLibrariesPath.toFile())) {
                out.print(libraryConfig);
            }
        }
    }

    private String generateModulesConfig(List<BundleInfo> modules) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<project version=\"4\">\n");
        builder.append("  <component name=\"ProjectModuleManager\">\n");
        builder.append("    <modules>\n");
        for (BundleInfo module : modules) {
            builder.append("      <module fileurl=\"file://$PROJECT_DIR$/")
                .append(module.getBundleName()).append(".iml\"").append(" filepath=\"$PROJECT_DIR$/")
                .append(module.getBundleName()).append(".iml").append("\"/>\n");
        }
        builder.append("    </modules>\n");
        builder.append("  </component>\n");
        builder.append("</project>");
        return builder.toString();
    }

    @Nullable
    private String generateIMlLibraryConfig(@NotNull BundleInfo bundleInfo, Result result) throws MalformedURLException {
        if (bundleInfo.getPath() == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<component name=\"libraryTable\">").append("\n");
        builder.append(" <library name=\"").append(bundleInfo.getBundleName()).append("\">\n");
        builder.append("   <CLASSES>").append("\n");
        appendLibraryInfo(builder, bundleInfo, result);
        builder.append("  </CLASSES>").append("\n");
        Collection<RemoteP2BundleInfo> remoteBundlesByName = P2RepositoryManager.INSTANCE.getLookupCache().getRemoteBundlesByName(bundleInfo.getBundleName() + ".source");
        builder.append("   <SOURCES>").append("\n");
        if (!remoteBundlesByName.isEmpty()) {
            RemoteP2BundleInfo remoteP2BundleInfo = remoteBundlesByName.stream().findFirst().get();
            appendLibraryInfo(builder, remoteP2BundleInfo, result);
        }
        builder.append("   </SOURCES>").append("\n");
        builder.append("   <jarDirectory url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve("lib"), true, false)).append("\" ").append("recursive=\"false\"/>\n");
        builder.append(" </library>\n");
        builder.append("</component>");
        return builder.toString();
    }

    private void appendLibraryInfo(@NotNull StringBuilder builder, @NotNull BundleInfo bundleInfo, Result result) throws MalformedURLException {
        if (bundleInfo.getPath() == null) {
            return;
        }
        if (bundleInfo.getPath().toFile().isDirectory()) {
            List<String> classpathLibs = bundleInfo.getClasspathLibs();
            for (String classpathLib : classpathLibs) {
                builder.append("    <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(classpathLib), true, true)).append("\"/>\n");
            }
        } else {
            builder.append("    <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), true, true)).append("\"/>\n");
        }
        for (BundleInfo fragment : bundleInfo.getFragments()) {
            appendLibraryInfo(builder, fragment, result);
        }
        if (!bundleInfo.getReexportedBundles().isEmpty()) {
            for (String reexportedBundle : bundleInfo.getReexportedBundles()) {
                appendLibraryInfo(builder, result.getBundleByName(reexportedBundle), result);
            }
            for (String importPackage : bundleInfo.getImportPackages()) {
                if (bundlePackageImports.get(importPackage) != null) {
                    for (BundleInfo info : bundlePackageImports.get(importPackage)) {
                        appendLibraryInfo(builder, info, result);
                    }
                }
            }
        }

    }

    private String getFormattedRelativePath(@NotNull Path bundlePath, boolean library, boolean jar) {
        String type = jar ? "jar://" : "file://";
        String prefix = library ? type + "$PROJECT_DIR$/../" : type + "$MODULE_DIR$/../";
        return prefix + getRelativizedPath(bundlePath).toString().replace("\\", "/") + (jar ? "!/" : "");
    }
    @NotNull
    private Path getRelativizedPath(@NotNull Path bundlePath) {
        return PathsManager.INSTANCE.getEclipsePath().getParent().getParent().relativize(bundlePath);
    }

    @Nullable
    private String generateIMLBundleConfig(@NotNull BundleInfo bundleInfo) throws IOException {
        if (bundleInfo.getPath() == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        builder.append(" <component name=\"NewModuleRootManager\">\n");
        Properties properties = readBuildConfiguration(bundleInfo.getPath());
        List<String> outputs = properties.get("output..") != null ? List.of(((String) properties.get("output..")).split(",")) : List.of();
        builder.append("  <output url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(outputs.get(0)), false, false)).append("\"/>").append("\n");
        builder.append("  <exclude-output/>").append("\n");
        builder.append("  <content url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false, false)).append("\">").append("\n");
        List<String> sources = properties.get("source..") != null ? List.of(((String) properties.get("source..")).split(",")) : List.of();
        for (String source : sources) {
            builder.append("   <sourceFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(source), false, false)).append("\"/>").append("\n");
        }
        for (String output : outputs) {
            builder.append("   <excludeFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(output), false, false)).append("\"/>").append("\n");
        }
        builder.append("  </content>").append("\n");
        builder.append("  <orderEntry type=\"inheritedJdk\" />").append("\n");
        builder.append("  <orderEntry type=\"sourceFolder\" forTests=\"false\" />").append("\n");
        for (String requireBundle : bundleInfo.getRequireBundles()) {
            appendBundleInfo(bundleInfo, requireBundle, builder);
        }
        if (bundleInfo.getFragmentHost() != null) {
            appendBundleInfo(bundleInfo, bundleInfo.getFragmentHost(), builder);
        }
        for (String importPackage : bundleInfo.getImportPackages()) {
            if (bundlePackageImports.get(importPackage) != null) {
                for (BundleInfo info : bundlePackageImports.get(importPackage)) {
                    appendBundleInfo(bundleInfo, info.getBundleName(), builder);
                }
            }
        }
        builder.append(" </component>").append("\n");
        builder.append("</module>");
        return builder.toString();
    }

    private Properties readBuildConfiguration(Path bundlePath) throws IOException {
        Path resolve = bundlePath.resolve("build.properties");
        return FileUtils.readPropertiesFile(resolve);
    }

    private static void appendBundleInfo(@NotNull BundleInfo bundleInfo, String requireBundle, StringBuilder builder) {
        boolean isExported = bundleInfo.getReexportedBundles().contains(requireBundle);
        if (DevPropertiesProducer.isBundleAcceptable(requireBundle)) {
            builder.append("  <orderEntry type = \"module\" module-name=\"").append(requireBundle).append(isExported ? "\" exported=\"\"" : "\"").append("/>").append("\n");
        } else {
            builder.append("  <orderEntry type = \"library\" name=\"").append(requireBundle).append(isExported ? "\" exported=\"\" " : "\" ").append("level=\"project\"/>").append("\n");
        }
    }

    private Path getImlModulePath(BundleInfo bundleInfo) {
        return PathsManager.INSTANCE.getImlModules().resolve(bundleInfo.getBundleName() + ".iml");
    }

    private Path getLibrariesPath(BundleInfo bundleInfo) {
        return PathsManager.INSTANCE.getImlLibraries().resolve(bundleInfo.getBundleName() + ".xml");
    }

    private Path getImplModuleConfigPath() {
        return PathsManager.INSTANCE.getImlModules().resolve(".idea/modules.xml");
    }

    private IMLConfigurationProducer() {
    }
}
