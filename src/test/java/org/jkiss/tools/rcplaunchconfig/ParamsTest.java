/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
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

import org.jkiss.code.NotNull;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamsTest {
    @Test
    void dependencyTreesAreDisabledByDefault() {
        assertFalse(parse().generateDependencyTree);
    }

    @Test
    void dependencyTreesCanBeEnabledExplicitly() {
        assertTrue(parse("-generateDependencyTree").generateDependencyTree);
    }

    @Test
    void legacyNoTreeOptionIsStillAccepted() {
        Params params = parse("-noTree");
        assertFalse(params.generateDependencyTree);
        assertTrue(params.notGenerateDependencyTree);
    }

    @NotNull
    private Params parse(@NotNull String... options) {
        Params params = new Params();
        params.init(Stream.concat(Stream.of(
            "-config", "osgi-app.properties",
            "-eclipse.version", "2026-09",
            "-projectsFolder", ".",
            "-output", "output"
        ), Stream.of(options)).toArray(String[]::new));
        return params;
    }
}
