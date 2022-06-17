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

package network.balanced.gradle.plugin;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class ConfigContainer {

    private final String name;
    private final Property<String> keystore;
    private final Property<String> password;
    private final Property<String> env;
    private final Property<String> configFile;

    public ConfigContainer(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.keystore = objectFactory.property(String.class).convention("./.keystores/godwallet.json");
        this.password = objectFactory.property(String.class).convention("gochain");
        this.env = objectFactory.property(String.class).convention("local");
        this.configFile = objectFactory.property(String.class).convention("contracts-sample.json");
    }

    public String getName() {
        return name;
    }

    public Property<String> getKeystore() {
        return keystore;
    }

    public Property<String> getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password.set(password);
    }

    public void setKeystore(String keystore) {
        this.keystore.set(keystore);
    }

    public Property<String> getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env.set(env);
    }

    public Property<String> getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile.set(configFile);
    }

}
