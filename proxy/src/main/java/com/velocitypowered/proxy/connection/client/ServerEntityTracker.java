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

package com.velocitypowered.proxy.connection.client;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.PlayPacketIds;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Tracks the entities the current backend has spawned on the client, so a switch that keeps the
 * client's world can take them away again.
 *
 * <p>A client that keeps its world keeps everything in it. Nothing else removes what the previous
 * server spawned -- the destination has never heard of those entities -- so its holograms, NPCs and
 * item displays would hang in the new world, frozen, until something happened to reuse their IDs.
 * The proxy is the only party that saw both sides, so the bookkeeping belongs here.</p>
 *
 * <p>Only the entity ID is read, off packets the proxy forwards undecoded, and the buffer is left
 * exactly as it was.</p>
 */
final class ServerEntityTracker {

  private final IntSet spawned = new IntOpenHashSet();

  /**
   * Notes an entity the backend spawns on, or removes from, the client.
   *
   * @param packetId the packet's ID, already read
   * @param packet   the rest of the packet, which the caller rewinds
   * @param version  the client's protocol version
   */
  void observe(int packetId, ByteBuf packet, ProtocolVersion version) {
    final PlayPacketIds ids = PlayPacketIds.of(version);
    if (ids == null) {
      return;
    }
    if (packetId == ids.spawnEntity()) {
      spawned.add(ProtocolUtils.readVarInt(packet));
    } else if (packetId == ids.removeEntities()) {
      final int count = ProtocolUtils.readVarInt(packet);
      for (int i = 0; i < count; i++) {
        spawned.remove(ProtocolUtils.readVarInt(packet));
      }
    }
  }

  /**
   * Builds the one packet that removes everything tracked from the client, and forgets it.
   *
   * @param alloc   the allocator for the packet
   * @param version the client's protocol version
   * @return the removal packet, or {@code null} when there is nothing to remove
   */
  @Nullable ByteBuf drainRemovals(ByteBufAllocator alloc, ProtocolVersion version) {
    final PlayPacketIds ids = PlayPacketIds.of(version);
    if (spawned.isEmpty() || ids == null) {
      spawned.clear();
      return null;
    }
    final ByteBuf removal = alloc.buffer();
    ProtocolUtils.writeVarInt(removal, ids.removeEntities());
    ProtocolUtils.writeVarInt(removal, spawned.size());
    for (IntIterator entityIds = spawned.iterator(); entityIds.hasNext(); ) {
      ProtocolUtils.writeVarInt(removal, entityIds.nextInt());
    }
    spawned.clear();
    return removal;
  }

  /**
   * Forgets everything tracked, for when the client's world was torn down some other way.
   */
  void clear() {
    spawned.clear();
  }
}
