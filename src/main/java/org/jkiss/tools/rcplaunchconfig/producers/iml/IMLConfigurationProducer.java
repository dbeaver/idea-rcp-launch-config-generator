package org.jkiss.tools.rcplaunchconfig.producers.iml;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public class IMLConfigurationProducer {
    public static final Logger log = LoggerFactory.getLogger(IMLConfigurationProducer.class);

    public static final IMLConfigurationProducer INSTANCE = new IMLConfigurationProducer();
    private final HashMap<String, Set<BundleInfo>> bundlePackageImports = new LinkedHashMap<>();

    private final Set<String> generatedLibraries = new HashSet<>();

    /**
     * Generates IML configuration from the result
     *
     * @param result result of dependency resolving
     * @throws IOException file access error
     */
    public void generateIMLFiles(@NotNull Result result) throws IOException {
        log.info("Generating IML configuration in " + PathsManager.INSTANCE.getImlModulesPath());
        List<BundleInfo> modules = new ArrayList<>();
        for (BundleInfo bundleInfo : result.getBundlesByNames().values()) {

            if (DevPropertiesProducer.isBundleAcceptable(bundleInfo.getBundleName())) {
                String moduleConfig = generateIMLBundleConfig(bundleInfo, result);
                modules.add(bundleInfo);
                Path imlFilePath = getImlModulePath(bundleInfo);
                createConfigFile(imlFilePath, moduleConfig);
            }
        }
        log.info(modules.size() + " module IML config generated");
        List<Path> rootModules = generateRootModules(modules);
        String modulesConfig = generateModulesConfig(modules, rootModules);
        createConfigFile(getImplModuleConfigPath(), modulesConfig);
    }

    private List<Path> generateRootModules(@NotNull List<BundleInfo> modules) throws IOException {
        Set<Path> presentModules = PathsManager.INSTANCE.getModulesRoots().stream()
            .filter((it) -> modules.stream().anyMatch(module -> module.getPath().startsWith(it))).collect(Collectors.toSet());
        List<Path> rootModules = new ArrayList<>();
        Path imlModuleRoot = PathsManager.INSTANCE.getImlModulesPath();
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

    private String generateImlRepositoryRootModule(@NotNull Path imlRoot) {
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

    private String generateRootModule(@NotNull Path presentModule) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
            "  <component name=\"NewModuleRootManager\">\n" +
            "    <output url=\"file://$MODULE_DIR$/target/classes\" />\n" +
            "    <output-test url=\"file://$MODULE_DIR$/target/classes\" />\n" +
            "    <content url =\"" + getFormattedRelativePath(presentModule, false, false) + "\">\n" +
            "    </content>\n" +
            "    <orderEntry type=\"inheritedJdk\" />\n" +
            "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
            "  </component>\n" +
            "</module>";
    }

    /**
     * Add imported by package bundle to the list
     */
    public void addRequiredBundleforPackage(@NotNull String packageName, @NotNull BundleInfo bundleInfo) {
        bundlePackageImports.computeIfAbsent(packageName, it -> new HashSet<>()).add(bundleInfo);
    }

    private void createConfigFile(@NotNull Path configPath, @NotNull String libraryConfig) throws IOException {
        Files.deleteIfExists(configPath);
        Files.createDirectories(configPath.getParent());
        Files.createFile(configPath);
        if (Files.exists(configPath)) {
            try (PrintWriter out = new PrintWriter(configPath.toFile())) {
                out.print(libraryConfig);
            }
        }
    }

    private String generateModulesConfig(@NotNull List<BundleInfo> modules, @NotNull List<Path> rootModules) {
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

    private void appendLibraryInfo(
        @NotNull StringBuilder builder,
        @NotNull BundleInfo bundleInfo,
        @NotNull Result result,
        @NotNull Set<String> resolvedBundles,
        boolean isLibrary) {
        resolvedBundles.add(bundleInfo.getBundleName());
        if (bundleInfo.getPath().toFile().isDirectory()) {
            List<String> classpathLibs = bundleInfo.getClasspathLibs();
            if (!classpathLibs.isEmpty()) {
                for (String classpathLib : classpathLibs) {
                    appendClasspathLib(builder, bundleInfo, classpathLib, isLibrary);
                }
            } else {
                builder.append("      <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false, isLibrary)).append("\"/>\n");
            }
        } else {
            builder.append("      <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), true, isLibrary)).append("\"/>\n");
        }
        for (BundleInfo fragment : bundleInfo.getFragments()) {
            appendLibraryInfo(builder, fragment, result, resolvedBundles, isLibrary);
        }
        if (!bundleInfo.getReexportedBundles().isEmpty()) {
            for (String reexportedBundle : bundleInfo.getReexportedBundles()) {
                BundleInfo bundleByName = result.getBundleByName(reexportedBundle);
                if (bundleByName == null) {
                    log.warn("Missing reexported bundle " + reexportedBundle);
                    continue;
                }
                appendLibraryInfo(builder, bundleByName, result, resolvedBundles, isLibrary);
            }
            for (String importPackage : bundleInfo.getImportPackages()) {
                if (bundlePackageImports.get(importPackage) != null) {
                    for (BundleInfo info : bundlePackageImports.get(importPackage)) {
                        appendLibraryInfo(builder, info, result, resolvedBundles, isLibrary);
                    }
                }
            }
        }
    }

    private void appendClasspathLib(@NotNull StringBuilder builder, @NotNull BundleInfo bundleInfo, @NotNull String classpathLib, boolean isLibrary) {
        builder.append("      <root url=\"")
            .append(getFormattedRelativePath(bundleInfo.getPath().resolve(classpathLib), true, isLibrary))
            .append("\"/>\n");
    }

    private String getFormattedRelativePath(@NotNull Path pathToFormat, boolean jar, boolean isLibrary) {
        String type = jar ? "jar://" : "file://";
        String prefix = type + (isLibrary ? "$PROJECT_DIR$/../../" : "$MODULE_DIR$/../../");
        return prefix + getRelativizedPath(pathToFormat).toString().replace("\\", "/") + (jar ? "!/" : "");
    }

    @NotNull
    private Path getRelativizedPath(@NotNull Path bundlePath) {
        return PathsManager.INSTANCE.getEclipsePath().getParent().getParent().relativize(bundlePath);
    }

    @Nullable
    private String generateIMLBundleConfig(@NotNull BundleInfo bundleInfo, @NotNull Result result) throws IOException {
        if (bundleInfo.getPath() == null) {
            log.warn("Bundle doesn't contain any data");
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        builder.append(" <component name=\"NewModuleRootManager\">\n");
        Properties properties = readBuildConfiguration(bundleInfo.getPath());
        List<String> outputs = properties.get("output..") != null ? List.of(((String) properties.get("output..")).split(",")) : List.of();
        if (!outputs.isEmpty()) {
            builder.append("  <output url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(outputs.get(0)), false, false))
                .append("\"/>").append("\n");
        }
        builder.append("  <exclude-output/>").append("\n");
        builder.append("  <content url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false, false)).append("\">").append("\n");
        List<String> sources = properties.get("source..") != null ? List.of(((String) properties.get("source..")).split(",")) : List.of();
        for (String source : sources) {
            builder.append("   <sourceFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(source), false, false))
                .append("\"/>").append("\n");
        }
        for (String output : outputs) {
            builder.append("   <excludeFolder url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(output), false, false))
                .append("\"/>").append("\n");
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
            addLibraryEntry(bundleInfo, builder, true, false);
            for (String classpathLib : bundleInfo.getClasspathLibs()) {
                appendClasspathLib(builder, bundleInfo, classpathLib, false);
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

    private void appendBundleInfo(
            @NotNull BundleInfo bundleInfo,
            @NotNull String requireBundle,
            @NotNull StringBuilder builder,
            @NotNull Result result,
            @NotNull Set<String> resolvedBundles
    ) throws IOException {
        BundleInfo bundleByName = result.getBundleByName(requireBundle);
        if (bundleByName == null || resolvedBundles.contains(bundleByName.getBundleName())) {
            if (bundleInfo.getPath() == null) {
                log.warn("Bundle doesn't contain any data");
            }
            return;
        }
        boolean isExported = bundleInfo.getReexportedBundles().contains(requireBundle);
        if (DevPropertiesProducer.isBundleAcceptable(requireBundle)) {
            resolvedBundles.add(requireBundle);
            builder.append("  <orderEntry type = \"module\" module-name=\"").append(requireBundle)
                .append(isExported ? "\" exported=\"\"" : "\"").append("/>").append("\n");
        } else {
            addModuleLibrary(requireBundle, builder, result, resolvedBundles, isExported);
        }
    }

    private void addModuleLibrary(
        @NotNull String requiredLibrary,
        @NotNull StringBuilder builder,
        @NotNull Result result,
        @NotNull Set<String> resolvedBundles,
        boolean isExported
    ) throws IOException {
        BundleInfo bundleByName = result.getBundleByName(requiredLibrary);
        if (bundleByName == null || bundleByName.getPath() == null) {
            log.warn("Missing reexported bundle " + requiredLibrary);
            return;
        }
        boolean directoryBundle = bundleByName.getPath().toFile().isDirectory();
        addLibraryEntry(bundleByName, builder, isExported, directoryBundle);
        if (!directoryBundle) {
            appendLibraryInfo(builder, bundleByName, result, resolvedBundles, false);
            endLibraryEntry(builder);
        } else {
            if (!generatedLibraries.contains(bundleByName.getBundleName())) {
                String libraryConfig = generateXMLLibraryConfig(bundleByName, result);
                if (libraryConfig != null) {
                    createConfigFile(getIMLLibraryConfigPath().resolve(bundleByName.getBundleName() + ".xml"), libraryConfig);
                }
                generatedLibraries.add(bundleByName.getBundleName());
            }
        }
    }

    @Nullable
    private String generateXMLLibraryConfig(@NotNull BundleInfo bundleInfo, Result result)  {
        if (bundleInfo.getPath() == null) {
            log.error("Bundle " + bundleInfo.getBundleName() + " path not found");
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<component name=\"libraryTable\">").append("\n");
        builder.append(" <library name=\"").append(bundleInfo.getBundleName()).append("\">\n");
        builder.append("   <CLASSES>").append("\n");
        HashSet<String> libraryObjects = new HashSet<>();
        appendLibraryInfo(builder, bundleInfo, result, libraryObjects, true);
        builder.append("  </CLASSES>").append("\n");
        builder.append("   <SOURCES>").append("\n");
        builder.append("   </SOURCES>").append("\n");
        builder.append("   <jarDirectory url=\"")
                .append(getFormattedRelativePath(bundleInfo.getPath().resolve("lib"), true, true))
                .append("\" ").append("recursive=\"false\"/>\n");
        builder.append(" </library>\n");
        builder.append("</component>");
        return builder.toString();
    }

    private static void endLibraryEntry(@NotNull StringBuilder builder) {
        builder.append("     </CLASSES>\n");
        builder.append("     <JAVADOC />\n");
        //TODO add SOURCES link
        builder.append("     <SOURCES />\n");
        builder.append("   </library>\n");
        builder.append("  </orderEntry>\n");
    }

    private static void addLibraryEntry(BundleInfo bundleByName, @NotNull StringBuilder builder, boolean isExported, boolean directoryBundle) {
        if (!directoryBundle) {
            builder.append("  <orderEntry type = \"module-library\"").append(isExported ? " exported=\"\">" : ">").append("\n");
            builder.append("   <library>\n");
            builder.append("     <CLASSES>\n");
        } else {
            builder.append("  <orderEntry type = \"library\" level=\"project\" name=\"")
                    .append(bundleByName.getBundleName()).append("\"").append(isExported ? " exported=\"\"/>" : "/>")
                    .append("\n");
        }
    }

    private Path getImlModulePath(@NotNull BundleInfo bundleInfo) {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(bundleInfo.getBundleName() + ".iml");
    }

    private Path getImplModuleConfigPath() {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(".idea/modules.xml");
    }

    private Path getIMLLibraryConfigPath() {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(".idea/libraries/");
    }

    private IMLConfigurationProducer() {
    }
}
