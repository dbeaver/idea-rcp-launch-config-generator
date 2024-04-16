# Eclipse RCP launcher generator for IDEA

## Instructions
- Clone repositories
  - https://github.com/dbeaver/dbeaver-common
  - https://github.com/dbeaver/dbeaver
  - https://github.com/dbeaver/cloudbeaver (only if you need web based Beaver too)
  - https://github.com/dbeaver/idea-workspace-dbeaver
  - https://github.com/dbeaver/idea-rcp-launch-config-generator
- Launch IDEA
- Open IDEA project (e.g. `idea-workspace-dbeaver`)
- Build project (CTRL+F9)
- Execute run configuration `Generate DBeaver CE dev props` (it will generate RCP config files)
- Execute run configuration `Run Eclipse (CE)` (it will launch DBeaver CE)
- Set variable `ECLIPSE_PATH` to the path where you want to keep external dependencies

Now you can debug code in IDEA, modify Java classes on fly, etc.

## Configuration

This tool generates the `dev.properties` and `config.ini` files needed to run the project in the dev environment.

Accepts the following required parameters:

Parameter | Description
------|----
-config | Path to file with configuration
-eclipse.version | Version of eclipse(use ${eclipse-version} for maven version)
-productFile | Path to .product file
-projectsFolder | Path to projects folder
-eclipse | Path to folder with eclipse and other dependencies, should be the same The same as ECLIPSE_PATH in IDEA preferences(optional, `${projectsFolder}/../dbeaver-eclipse-workspace` will be used if not specifed)
-output | Place for result files
-testBundles | Bundles required for launching unit tests

For example, the command to create files for CB CE:

```
./eclipse-rcp-launcher -productFile $PROJECTS_DIR$/cloudbeaver/server/product/web-server/CloudbeaverServer.product -projectsFolder $PROJECTS_DIR$ -eclipse $ECLIPSE_PATH$ -output $PROJECT_DIR$/../eclipse/workspace/.metadata/.plugins/org.eclipse.pde.core/CloudbeaverServer.product'
```

Configuration file:
```properties
featuresPaths=dbeaver/features;
bundlesPaths=\
  dbeaver-common/modules;\
  dbeaver/plugins;
repositories=\
  https://p2.dev.dbeaver.com/eclipse-repo/;\
  https://download.eclipse.org/elk/updates/releases/${elk-version}/;
```
featuresPaths: list of paths to Eclipse features folders  
bundlesPaths: list of paths to Eclipse bundles folders
repositories: list of repositories used to download third-party bundles from

Preconfigured file `rcp-gen.properties` reside in repository [idea-workspace-dbeaver](https://github.com/dbeaver/idea-workspace-dbeaver)

Note: Technically this tool should work with any Eclipse RCP, not just dbeaver or cloudbeaver.
