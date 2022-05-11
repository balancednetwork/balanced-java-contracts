/*
 * Copyright (c) 2022-2022 Balanced.network.
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

package network.balanced.gradle.plugin

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

class ConfigurationExtension {

    private static final String EXTENSION_NAME = "deployBalancedContracts"

    private final NamedDomainObjectContainer<ConfigContainer> configContainer

    static String getExtName() {
        return EXTENSION_NAME
    }

    ConfigurationExtension(Project project) {

        configContainer = project.container(ConfigContainer.class, name -> new ConfigContainer(name, project.getObjects()))

        configContainer.all(container -> {
            String endpoint = container.getName()
            String capitalizedTarget = endpoint.substring(0, 1).toUpperCase() + endpoint.substring(1)
            String taskName = "deployContractsTo" + capitalizedTarget
            project.getTasks().register(taskName, DeployBalancedContracts.class, task -> {
                task.getKeystore().set(container.getKeystore())
                task.getPassword().set(container.getPassword())
                task.getEnv().set(container.getEnv())
                task.getConfigFile().set(container.getConfigFile())
            })
            var deployTask = project.getTasks().getByName(taskName)
            deployTask.setGroup("Configuration")
            deployTask.setDescription("Deploy Balanced Contracts to " + capitalizedTarget + ".")
        })
    }


    void envs(Action<? super NamedDomainObjectContainer<ConfigContainer>> action) {
        action.execute(configContainer)
    }
}
