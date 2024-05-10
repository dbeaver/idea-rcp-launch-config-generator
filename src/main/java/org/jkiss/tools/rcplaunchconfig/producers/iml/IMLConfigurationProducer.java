package org.jkiss.tools.rcplaunchconfig.producers.iml;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.*;

public class IMLConfigurationProducer {
    public static final IMLConfigurationProducer INSTANCE = new IMLConfigurationProducer();
    private final HashMap<BundleInfo, Set<String>> bundleImports = new LinkedHashMap<>();
    public void generateIMLFiles() throws IOException {
        List<BundleInfo> modules = new ArrayList<>();
        for (BundleInfo bundleInfo : bundleImports.keySet()) {
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
                String libraryConfig = generateIMlLibraryConfig(bundleInfo);
                createConfigFile(imlLibrariesPath, libraryConfig);
            }
        }

        String modulesConfig = generateModulesConfig(modules);
        createConfigFile(getImplModuleConfigPath(), modulesConfig);
    }

    public void addRequiredBundle(BundleInfo bundleInfo, String importBundleName) {
        bundleImports.computeIfAbsent(bundleInfo, x -> new LinkedHashSet<>()).add(importBundleName);
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
    private String generateIMlLibraryConfig(@NotNull BundleInfo bundleInfo) throws MalformedURLException {
        if (bundleInfo.getPath() == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<component name=\"libraryTable\">").append("\n");
        builder.append(" <library name=\"").append(bundleInfo.getBundleName()).append("\">\n");
        builder.append("   <CLASSES>").append("\n");
        appendLibraryInfo(builder, bundleInfo);
        builder.append("  </CLASSES>").append("\n");
        Collection<RemoteP2BundleInfo> remoteBundlesByName = P2RepositoryManager.INSTANCE.getLookupCache().getRemoteBundlesByName(bundleInfo.getBundleName() + ".source");
        builder.append("   <SOURCES>").append("\n");
        if (!remoteBundlesByName.isEmpty()) {
            RemoteP2BundleInfo remoteP2BundleInfo = remoteBundlesByName.stream().findFirst().get();
            appendLibraryInfo(builder, remoteP2BundleInfo);
        }
        builder.append("   </SOURCES>").append("\n");
        builder.append("   <jarDirectory url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve("lib"), true, false)).append("\" ").append("recursive=\"false\"/>\n");
        builder.append(" </library>\n");
        builder.append("</component>");
        return builder.toString();
    }

    private static void appendLibraryInfo(@NotNull StringBuilder builder, @NotNull BundleInfo bundleInfo) throws MalformedURLException {
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
    }

    private static String getFormattedRelativePath(@NotNull Path bundlePath, boolean library, boolean jar) {
        String type = jar ? "jar://" : "file://";
        String prefix = library ? type + "$PROJECT_DIR$/../" : type + "$MODULE_DIR$/../";
        return prefix + getRelativizedPath(bundlePath).toString().replace("\\", "/") + (jar ? "!/" : "");
    }
    @NotNull
    private static Path getRelativizedPath(@NotNull Path bundlePath) {
        return PathsManager.INSTANCE.getEclipsePath().getParent().getParent().relativize(bundlePath);
    }

    @Nullable
    private String generateIMLBundleConfig(@NotNull BundleInfo bundleInfo) {
        if (bundleInfo.getPath() == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        builder.append(" <component name=\"NewModuleRootManager\">\n");
        builder.append("  <output url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve("target"), false, false)).append("\"/>").append("\n");
        builder.append("  <exclude-output/>").append("\n");
        builder.append("  <content url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false, false)).append("\">").append("\n");
        builder.append("   <sourceFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve("src"), false, false)).append("\"/>").append("\n");
        builder.append("   <excludeFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve("target"), false, false)).append("\"/>").append("\n");
        builder.append("  </content>").append("\n");
        builder.append("  <orderEntry type=\"inheritedJdk\" />").append("\n");
        builder.append("  <orderEntry type=\"sourceFolder\" forTests=\"false\" />").append("\n");
        for (String requireBundle : bundleInfo.getRequireBundles()) {
            boolean isExported = bundleInfo.getReexportedBundles().contains(requireBundle);
            if (DevPropertiesProducer.isBundleAcceptable(requireBundle)) {
                builder.append("  <orderEntry type = \"module\" module-name=\"").append(requireBundle).append(isExported ? "\" exported=\"\"" : "\"").append("/>").append("\n");
            } else {
                builder.append("  <orderEntry type = \"library\" name=\"").append(requireBundle).append(isExported ? "\" exported=\"\" " : "\" ").append("level=\"project\"/>").append("\n");
            }
        }
        builder.append(" </component>").append("\n");
        builder.append("</module>");
        return builder.toString();
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
