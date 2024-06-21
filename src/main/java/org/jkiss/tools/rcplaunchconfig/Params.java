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
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;

import java.nio.file.Path;

public class Params {

    @Option(names = "-config", description = "Path to configuration properties file", required = true)
    public Path configFilePath;

    @Option(names = "-eclipse.version", description = "Eclipse version", required = true)
    public String eclipseVersion;

    @Option(names = "-projectsFolder", description = "Path to projects folder", required = true)
    public Path projectsFolderPath;

    @Option(names = "-eclipse", description = "Path to folder with 3-rd party dependencies")
    public Path eclipsePath;

    @Option(names = "-output", description = "Place for result files", required = true)
    public Path resultFilesPath;

    public @Nonnull ParseResult init(String[] args) {
        return new CommandLine(this)
            .parseArgs(args);
    }
}
