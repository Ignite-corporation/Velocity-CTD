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

package com.velocitypowered.proxy.connection;

import com.velocitypowered.api.network.ProtocolVersion;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The IDs of the clientbound play packets the proxy reads or writes without decoding them, for a
 * client it keeps in play across a server switch.
 *
 * <p>These packets are not in {@code StateRegistry} because the proxy never needs their bodies,
 * only an entity ID or a name off their start. Their IDs still move with every protocol that adds a
 * packet before them, so each protocol the network lets in has its own row here. A protocol without
 * a row is not tracked at all: nothing is removed on a switch and tags are not sent in play, which
 * costs the client a reconfiguration and never a wrong packet.</p>
 *
 * @param spawnEntity    {@code add_entity}
 * @param removeEntities {@code remove_entities}
 * @param objective      {@code set_objective}
 * @param team           {@code set_player_team}
 * @param tags           {@code update_tags}
 */
public record PlayPacketIds(int spawnEntity, int removeEntities, int objective, int team,
                            int tags) {

  // Each row is read off that version's packet report (the vanilla server's --reports output). The
  // bodies the proxy touches are the same in all of them: a VarInt entity ID, a VarInt-counted ID
  // list, a name followed by a method byte, and the tags of the configuration state.
  private static final PlayPacketIds V26_1 = new PlayPacketIds(0x01, 0x4D, 0x6A, 0x6D, 0x86);
  private static final PlayPacketIds V26_3 = new PlayPacketIds(0x01, 0x4E, 0x6C, 0x6F, 0x89);

  /**
   * Returns the IDs for a client's protocol.
   *
   * @param version the client's protocol version
   * @return the IDs, or {@code null} for a protocol that has no row
   */
  public static @Nullable PlayPacketIds of(ProtocolVersion version) {
    return switch (version) {
      case MINECRAFT_26_1, MINECRAFT_26_2 -> V26_1;
      case MINECRAFT_26_3 -> V26_3;
      default -> null;
    };
  }
}
