/*
 * Copyright (C) 2026 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.proxy.config.BackendServerConfig;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.util.AddressUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A reload reconciles only the servers the configuration owns: plugins register servers through
 * {@code ProxyServer#registerServer} that no {@code velocity.toml} lists, and a reload must not
 * unregister them.
 */
class ReconcileServersTest {

  private static final Map<String, String> ADDRESSES = Map.of(
      "lobby", "127.0.0.1:30066",
      "removed", "127.0.0.1:30067",
      "plugin", "127.0.0.1:30068");

  private final VelocityServer server = new VelocityServer(new ProxyOptions(new String[0]));

  @Test
  void reloadKeepsServerRegisteredByPlugin() {
    register("lobby");
    register("plugin");

    server.reconcileServers(listing("lobby"), listing("lobby"));

    assertTrue(server.getServer("plugin").isPresent());
    assertTrue(server.getServer("lobby").isPresent());
  }

  @Test
  void reloadDropsServerRemovedFromConfiguration() {
    register("lobby");
    register("removed");

    server.reconcileServers(listing("lobby", "removed"), listing("lobby"));

    assertTrue(server.getServer("removed").isEmpty());
    assertTrue(server.getServer("lobby").isPresent());
  }

  private void register(String name) {
    server.registerServer(new ServerInfo(name, AddressUtil.parseAddress(ADDRESSES.get(name))));
  }

  private static VelocityConfiguration listing(String... names) {
    Map<String, BackendServerConfig> servers = new LinkedHashMap<>();
    for (String name : names) {
      servers.put(name, new BackendServerConfig(ADDRESSES.get(name)));
    }
    VelocityConfiguration configuration = mock(VelocityConfiguration.class);
    when(configuration.getBackendServers()).thenReturn(servers);
    return configuration;
  }
}
