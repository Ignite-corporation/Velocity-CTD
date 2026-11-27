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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ServerEntityTrackerTest {

  private static final ProtocolVersion V26_2 = ProtocolVersion.MINECRAFT_26_2;
  private static final int SPAWN = 0x01;
  private static final int REMOVE = 0x4D;

  @Test
  void removesWhatTheServerSpawnedAndDidNotRemove() {
    final ServerEntityTracker tracker = new ServerEntityTracker();
    spawn(tracker, 5);
    spawn(tracker, 9);
    spawn(tracker, 12);
    remove(tracker, 9); // the server took this one away itself

    assertEquals(Set.of(5, 12), removedBy(tracker));
    assertNull(tracker.drainRemovals(UnpooledByteBufAllocator.DEFAULT, V26_2),
        "draining forgets what was removed");
  }

  @Test
  void worldRebuiltSomeOtherWayLeavesNothingToRemove() {
    final ServerEntityTracker tracker = new ServerEntityTracker();
    spawn(tracker, 5);
    tracker.clear();
    assertNull(tracker.drainRemovals(UnpooledByteBufAllocator.DEFAULT, V26_2));
  }

  @Test
  void tracksNothingOnProtocolsWhoseIdsItDoesNotKnow() {
    final ServerEntityTracker tracker = new ServerEntityTracker();
    tracker.observe(SPAWN, varint(5), ProtocolVersion.MINECRAFT_1_21_11);
    assertNull(tracker.drainRemovals(UnpooledByteBufAllocator.DEFAULT,
        ProtocolVersion.MINECRAFT_1_21_11));
  }

  @Test
  void tracksEachProtocolByItsOwnIds() {
    // 26.3 moved the removal one ID up; 0x4D is another packet there.
    final ProtocolVersion v263 = ProtocolVersion.MINECRAFT_26_3;
    final ServerEntityTracker tracker = new ServerEntityTracker();
    tracker.observe(SPAWN, varint(5), v263);
    tracker.observe(SPAWN, varint(9), v263);
    final ByteBuf oldRemoval = varint(1);
    ProtocolUtils.writeVarInt(oldRemoval, 5);
    tracker.observe(REMOVE, oldRemoval, v263);
    final ByteBuf removal = varint(1);
    ProtocolUtils.writeVarInt(removal, 9);
    tracker.observe(0x4E, removal, v263);

    final ByteBuf drained = tracker.drainRemovals(UnpooledByteBufAllocator.DEFAULT, v263);
    assertEquals(0x4E, ProtocolUtils.readVarInt(drained));
    assertEquals(1, ProtocolUtils.readVarInt(drained));
    assertEquals(5, ProtocolUtils.readVarInt(drained));
    drained.release();
  }

  private static void spawn(ServerEntityTracker tracker, int entityId) {
    // Whatever follows the ID in a spawn packet is none of the tracker's business.
    final ByteBuf packet = varint(entityId);
    packet.writeLong(1L);
    packet.writeLong(2L);
    tracker.observe(SPAWN, packet, V26_2);
  }

  private static void remove(ServerEntityTracker tracker, int... entityIds) {
    final ByteBuf packet = varint(entityIds.length);
    for (int entityId : entityIds) {
      ProtocolUtils.writeVarInt(packet, entityId);
    }
    tracker.observe(REMOVE, packet, V26_2);
  }

  /** Decodes the one removal packet back into the set of entity IDs it names. */
  private static Set<Integer> removedBy(ServerEntityTracker tracker) {
    final ByteBuf removal = tracker.drainRemovals(UnpooledByteBufAllocator.DEFAULT, V26_2);
    assertEquals(REMOVE, ProtocolUtils.readVarInt(removal));
    final int count = ProtocolUtils.readVarInt(removal);
    final List<Integer> ids = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      ids.add(ProtocolUtils.readVarInt(removal));
    }
    assertEquals(0, removal.readableBytes(), "one packet carries them all");
    removal.release();
    return ids.stream().collect(Collectors.toSet());
  }

  private static ByteBuf varint(int value) {
    final ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buf, value);
    return buf;
  }
}
