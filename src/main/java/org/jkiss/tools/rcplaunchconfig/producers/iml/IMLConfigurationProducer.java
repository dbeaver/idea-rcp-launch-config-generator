package org.jkiss.tools.rcplaunchconfig.producers.iml;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class IMLConfigurationProducer {
    public static final IMLConfigurationProducer INSTANCE = new IMLConfigurationProducer();
    private final HashMap<String, Set<BundleInfo>> bundlePackageImports = new LinkedHashMap<>();
    public void generateIMLFiles(Result result) throws IOException {
        List<BundleInfo> modules = new ArrayList<>();
        for (BundleInfo bundleInfo : result.getBundlesByNames().values()) {

            if (DevPropertiesProducer.isBundleAcceptable(bundleInfo.getBundleName())) {
                String moduleConfig = generateIMLBundleConfig(bundleInfo, result);
                modules.add(bundleInfo);
                Path imlFilePath = getImlModulePath(bundleInfo);
                createConfigFile(imlFilePath, moduleConfig);
            }
        }
        List<Path> rootModules = generateRootModules(modules);
        String modulesConfig = generateModulesConfig(modules, rootModules);
        createConfigFile(getImplModuleConfigPath(), modulesConfig);
    }

    private List<Path> generateRootModules(List<BundleInfo> modules) throws IOException {
        Set<Path> presentModules = PathsManager.INSTANCE.getModulesRoots().stream().filter((it) -> modules.stream().anyMatch(moduleit -> moduleit.getPath().startsWith(it))).collect(Collectors.toSet());
        List<Path> rootModules = new ArrayList<>();
        Path imlModuleRoot = PathsManager.INSTANCE.getImlModules();
        for (Path presentModule : presentModules) {
            String rootModuleConfig = generateRootModule(presentModule);
            Path rootIml = imlModuleRoot.resolve(presentModule.getFileName() + ".iml");
            rootModules.add(rootIml);
            createConfigFile(rootIml, rootModuleConfig);
        }
        Path rootIml = imlModuleRoot.resolve(imlModuleRoot.getFileName() + ".iml");
        createConfigFile(rootIml, generateImlRepositoryRootModule(imlModuleRoot));
        rootModules.add(rootIml);
        return rootModules;
    }

    private String generateImlRepositoryRootModule(Path imlRoot) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<module type=\"WEB_MODULE\" version=\"4\">\n" +
                "  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n" +
                "    <exclude-output />\n" +
                "    <content url=\"file://$MODULE_DIR$/../" + imlRoot.getFileName() + "\" />\n" +
                "    <orderEntry type=\"inheritedJdk\" />\n" +
                "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
                "  </component>\n" +
                "</module>";
    }
    private String generateRootModule(Path presentModule) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
                "  <component name=\"NewModuleRootManager\">\n" +
                "    <output url=\"file://$MODULE_DIR$/target/classes\" />\n" +
                "    <output-test url=\"file://$MODULE_DIR$/target/classes\" />\n" +
                "    <content url =\"" + getFormattedRelativePath(presentModule, false) + "\">\n" +
                "    </content>\n" +
                "    <orderEntry type=\"inheritedJdk\" />\n" +
                "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
                "  </component>\n" +
                "</module>";
    }

    public void AddRequiredBundleforPackage(String packageName, BundleInfo bundleInfo) {
        bundlePackageImports.computeIfAbsent(packageName, it -> new HashSet<>()).add(bundleInfo);
    }

    private void createConfigFile(Path imlLibrariesPath, String libraryConfig) throws IOException {
        if (imlLibrariesPath.toFile().exists()) {
            imlLibrariesPath.toFile().delete();
        }
        boolean fileCreated = imlLibrariesPath.toFile().createNewFile();
        if (fileCreated) {
            try (PrintWriter out = new PrintWriter(imlLibrariesPath.toFile())) {
                out.print(libraryConfig);
            }
        }
    }

    private String generateModulesConfig(List<BundleInfo> modules, List<Path> rootModules) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<project version=\"4\">\n");
        builder.append("  <component name=\"ProjectModuleManager\">\n");
        builder.append("    <modules>\n");
        for (Path rootModule : rootModules) {
            builder.append("      <module fileurl=\"file://$PROJECT_DIR$/")
                    .append(rootModule.getFileName()).append("\" filepath=\"$PROJECT_DIR$/")
                    .append(rootModule.getFileName()).append("\"/>\n");
        }
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
    private void appendLibraryInfo(@NotNull StringBuilder builder, @NotNull BundleInfo bundleInfo, Result result, @NotNull Set<String> resolvedBundles) throws MalformedURLException {
        if (bundleInfo.getPath() == null || resolvedBundles.contains(bundleInfo.getBundleName())) {
            return;
        }
        resolvedBundles.add(bundleInfo.getBundleName());
        if (bundleInfo.getPath().toFile().isDirectory()) {
            List<String> classpathLibs = bundleInfo.getClasspathLibs();
            if (!classpathLibs.isEmpty()) {
                for (String classpathLib : classpathLibs) {
                    appendClasspathLib(builder, bundleInfo, classpathLib);
                }
            } else {
                builder.append("      <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false)).append("\"/>\n");
            }
        } else {
            builder.append("      <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), true)).append("\"/>\n");
        }
        for (BundleInfo fragment : bundleInfo.getFragments()) {
            appendLibraryInfo(builder, fragment, result, resolvedBundles);
        }
        if (!bundleInfo.getReexportedBundles().isEmpty()) {
            for (String reexportedBundle : bundleInfo.getReexportedBundles()) {
                appendLibraryInfo(builder, result.getBundleByName(reexportedBundle), result, resolvedBundles);
            }
            for (String importPackage : bundleInfo.getImportPackages()) {
                if (bundlePackageImports.get(importPackage) != null) {
                    for (BundleInfo info : bundlePackageImports.get(importPackage)) {
                        appendLibraryInfo(builder, info, result, resolvedBundles);
                    }
                }
            }
        }
    }

    private void appendClasspathLib(@NotNull StringBuilder builder, @NotNull BundleInfo bundleInfo, String classpathLib) {
        builder.append("      <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(classpathLib), true)).append("\"/>\n");
    }

    private String getFormattedRelativePath(@NotNull Path pathToFormat, boolean jar) {
        String type = jar ? "jar://" : "file://";
        String prefix = type + "$MODULE_DIR$/../../";
        return prefix + getRelativizedPath(pathToFormat).toString().replace("\\", "/") + (jar ? "!/" : "");
    }
    @NotNull
    private Path getRelativizedPath(@NotNull Path bundlePath) {
        return PathsManager.INSTANCE.getEclipsePath().getParent().getParent().relativize(bundlePath);
    }

    @Nullable
    private String generateIMLBundleConfig(@NotNull BundleInfo bundleInfo, Result result) throws IOException {
        if (bundleInfo.getPath() == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        builder.append(" <component name=\"NewModuleRootManager\">\n");
        Properties properties = readBuildConfiguration(bundleInfo.getPath());
        List<String> outputs = properties.get("output..") != null ? List.of(((String) properties.get("output..")).split(",")) : List.of();
        if (!outputs.isEmpty()) {
            builder.append("  <output url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(outputs.get(0)), false)).append("\"/>").append("\n");
        }
        builder.append("  <exclude-output/>").append("\n");
        builder.append("  <content url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false)).append("\">").append("\n");
        List<String> sources = properties.get("source..") != null ? List.of(((String) properties.get("source..")).split(",")) : List.of();
        for (String source : sources) {
            builder.append("   <sourceFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(source), false)).append("\"/>").append("\n");
        }
        for (String output : outputs) {
            builder.append("   <excludeFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(output), false)).append("\"/>").append("\n");
        }
        builder.append("  </content>").append("\n");
        builder.append("  <orderEntry type=\"inheritedJdk\" />").append("\n");
        builder.append("  <orderEntry type=\"sourceFolder\" forTests=\"false\" />").append("\n");
        HashSet<String> resolvedBundles = new HashSet<>();
        for (String requireBundle : bundleInfo.getRequireBundles()) {
            appendBundleInfo(bundleInfo, requireBundle, builder, result, resolvedBundles);
        }
        if (bundleInfo.getFragmentHost() != null) {
            appendBundleInfo(bundleInfo, bundleInfo.getFragmentHost(), builder, result, resolvedBundles);
        }
        for (String importPackage : bundleInfo.getImportPackages()) {
            if (bundlePackageImports.get(importPackage) != null) {
                for (BundleInfo info : bundlePackageImports.get(importPackage)) {
                    appendBundleInfo(bundleInfo, info.getBundleName(), builder, result, resolvedBundles);
                }
            }
        }
        if (!bundleInfo.getClasspathLibs().isEmpty()) {
            initLibraryEntry(builder, true);
            for (String classpathLib : bundleInfo.getClasspathLibs()) {
                appendClasspathLib(builder, bundleInfo, classpathLib);
            }
            endLibraryEntry(builder);
        }
        builder.append(" </component>").append("\n");
        builder.append("</module>");
        return builder.toString();
    }

    private Properties readBuildConfiguration(Path bundlePath) throws IOException {
        Path resolve = bundlePath.resolve("build.properties");
        return FileUtils.readPropertiesFile(resolve);
    }

    private void appendBundleInfo(@NotNull BundleInfo bundleInfo, String requireBundle, StringBuilder builder, Result result, Set<String> resolvedBundles) throws MalformedURLException {
        BundleInfo bundleByName = result.getBundleByName(requireBundle);
        if (bundleByName == null || resolvedBundles.contains(bundleByName.getBundleName())) {
            return;
        }
        boolean isExported = bundleInfo.getReexportedBundles().contains(requireBundle);
        if (DevPropertiesProducer.isBundleAcceptable(requireBundle)) {
            resolvedBundles.add(requireBundle);
            builder.append("  <orderEntry type = \"module\" module-name=\"").append(requireBundle).append(isExported ? "\" exported=\"\"" : "\"").append("/>").append("\n");
        } else {
            addModuleLibrary(requireBundle, builder, result, resolvedBundles, isExported);
        }
    }

    private void addModuleLibrary(
        String requiredLibrary,
        StringBuilder builder,
        Result result,
        Set<String> resolvedBundles,
        boolean isExported
    ) throws MalformedURLException {
        initLibraryEntry(builder, isExported);
        BundleInfo bundleByName = result.getBundleByName(requiredLibrary);
        appendLibraryInfo(builder, bundleByName, result, resolvedBundles);
        endLibraryEntry(builder);
    }

    private static void endLibraryEntry(StringBuilder builder) {
        builder.append("     </CLASSES>\n");
        builder.append("     <JAVADOC />\n");
        builder.append("     <SOURCES />\n");
        builder.append("   </library>\n");
        builder.append("  </orderEntry>\n");
    }

    private static void initLibraryEntry(StringBuilder builder, boolean isExported) {
        builder.append("  <orderEntry type = \"module-library\"").append(isExported ? " exported=\"\">" : ">").append("\n");
        builder.append("   <library>\n");
        builder.append("     <CLASSES>\n");
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
