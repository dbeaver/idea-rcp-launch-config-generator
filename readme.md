# Eclipse RCP launcher generator for IDEA

DBeaver build instructions: https://github.com/dbeaver/dbeaver/wiki/Develop-in-IDEA

# Prerequisites
Before proceeding with building DBeaver, ensure that your system meets the following prerequisites:

* JDK 17 or higher: DBeaver uses OpenJDK 17 as the default Java Development Kit. Make sure you have JDK 17 or a newer version installed.
* Apache Maven 2.9.5 or higher: This is required for managing the project's build. You can download it from the Apache Maven website.
* Git: You need to install Git to clone the DBeaver repository. If you have not already, visit the official Git website for download and installation instructions.
* Internet Access: Required for downloading dependencies and other necessary components during the build process.

## How to generate IDEA workspace
- Clone repositories in the same folder
  - https://github.com/dbeaver/dbeaver-common
  - https://github.com/dbeaver/dbeaver
  - https://github.com/dbeaver/osgi-commons
  - https://github.com/dbeaver/cloudbeaver (only if you need to make changes in Cloudbeaver)
  - https://github.com/dbeaver/idea-rcp-launch-config-generator
- Execute the `generate_workspace` script in either the dbeaver or cloudbeaver repo, depending on the workspace you need.
Once dependencies are downloaded and IDEA configs created, the folder `dbeaver-workspace` will appear. The IDEA configuration, by default, will be generated in `dbeaver-workspace/dbeaver-ce` or `dbeaver-workspace/dbeaver/cloudbeaver-ce`, which can be opened in IntelliJ IDEA.
- Launch IDEA and open the configuration. It should contain all launch configurations

Now, you can debug code in IDEA, modify Java classes on the fly, etc.

## Checkout branches attached to a GitHub ticket

Use `checkout-ticket-branches.sh` to switch all sibling repository clones to branches attached to a GitHub issue. The script reads linked GitHub branches and pull requests from the ticket, finds matching cloned repositories in the parent folder, fetches missing local branches from `origin`, and runs `git checkout`.

```sh
export GITHUB_TOKEN=<token>
./checkout-ticket-branches.sh https://github.com/dbeaver/pro/issues/12345
```

On Windows, use `checkout-ticket-branches.cmd`:

```cmd
set GITHUB_TOKEN=<token>
checkout-ticket-branches.cmd https://github.com/dbeaver/pro/issues/12345
```

The repositories root defaults to the parent folder of this repository. It can be overridden with the second argument:

```sh
./checkout-ticket-branches.sh https://github.com/dbeaver/pro/issues/12345 /path/to/repositories
```

If `GITHUB_TOKEN` or `GH_TOKEN` is not set, the script tries to read a token from `gh auth token`.

## Manual launch Configuration (Optional information)

This tool generates the `dev.properties` and `config.ini` files needed to run the project in the dev environment, additionally it creates IDEA configuration in `(output folder)/idea-configuration`.

Accepts the following required parameters:

| Parameter        | Description                                                                                                                                                                                              |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| -config          | Path to file with configuration                                                                                                                                                                          |
| -eclipse.version | Version of eclipse(use ${eclipse-version} for maven version)                                                                                                                                             |
| -projectsFolder  | Path to projects folder                                                                                                                                                                                  |
| -eclipse         | Path to the folder with eclipse and other dependencies should be the same. The same as ECLIPSE_PATH in IDEA preferences(optional, `${projectsFolder}/../dbeaver-workspace` will be used if not specifed) |
| -output          | Place for result files                                                                                                                                                                                   |
| -singleCoreMode  | (Debug) Uses only one core for the project resolution                                                                                                                                                    |
| -debug           | Provides more detailed output                                                                                                                                                                            |
| -noTree          | Disable dependency tree generation for each product                                                                                                                                                      |

Configuration file example:
```properties
workspaceName=dbeaver-ce
featuresPaths=\
  dbeaver/features;
bundlesPaths=\
  dbeaver-common/modules;\
  dbeaver/plugins
repositories=\
  https://repo.dbeaver.net/p2/ce/;\
  https://download.eclipse.org/releases/${eclipse-version}/;
testLibraries=\
  org.junit;\
  org.mockito.mockito-core;\
  junit-jupiter-api;\
  org.opentest4j;\
  org.hamcrest.core
productsPaths=\
  dbeaver/product/community/DBeaver.product;
ideaConfigurationFilesPaths=\
  dbeaver/.ide/.idea
testBundlePaths=\
  dbeaver/test;
optionalFeatureRepositories=\
  dbeaver/product/repositories
```
| Parameter                   | Description                                                                                                               |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| workspaceName               | Name of the generated workspace folder                                                                                    |
| featuresPaths               | List of paths to Eclipse features folders                                                                                 |
| bundlesPaths                | List of paths to Eclipse bundles folders                                                                                  |
| productsPaths               | List of paths to Eclipse products, working directory can be provided after ':' for IDEA launch configs                    |
| repositories                | List of repositories used to download third-party bundles from                                                            |
| testBundlePaths             | Unit tests bundles(optional)                                                                                              |
| testLibraries               | Libraries used for bundles unit tests(optional)                                                                           |
| ideaConfigurationFilesPaths | Files of IDEA configuration to be copied(optional)                                                                        |
| additionalModuleRoots       | Additional root IDEA modules can be generated if required(optional)                                                       |
| optionalFeatureRepositories | Repositories containing information about optional features which are not included in product launch by default(optional) |
| excludeOutputs              | Additional folders to exclude from root module indexing                                                                   |

## Product specific properties
For each product, can be specified additional properties, such as VM arguments, program arguments, environment variables by using 

| Parameter               | Description                                                                                                                                                      |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| overrideDataFolder      | Override default data folder location (default is workspace location)  in the format product:path                                                                |                                 |
| runBeforeProductsLaunch | Comma-separated list of IDEA configs to be launched before the main product in the format product:path                                                           |                              |
| associateEnvProperties  | Comma-separated list of properties with environment variables to be associated with the product in the format product=propertyNames                              |
| associateVMProperties   | Comma-separated list of properties with VM parameters to be associated with the product in the format product=propertyNames                                      |
Additional parameters can be specified in additionalProperties.json
in the following format:
```json
[
  {
    "uids": [""], // can be empty or contain list of uids of products to which this property should be applied
    "type": "env", // can be "ENV" or "CLI"
    "name": "property_unique_name",
    "properties": {
        "propertyName": "propertyValue",
        "anotherPropertyName": "anotherPropertyValue"
    }
  }
]
```
Note: Technically this tool should work with any Eclipse RCP, not just dbeaver or cloudbeaver.
