package com.islandium.regions.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.SwitchActiveSlotEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.islandium.regions.RegionsPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;

public class SwitchActiveSlotEventSystem extends EntityEventSystem<EntityStore, SwitchActiveSlotEvent> {

    public SwitchActiveSlotEventSystem() {
        super(SwitchActiveSlotEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull SwitchActiveSlotEvent event) {
        RegionsPlugin plugin = RegionsPlugin.get();
        if (plugin != null) plugin.log(Level.INFO, "[SwitchActiveSlot] declenche !");
    }

    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }

    @Nonnull @Override
    public Set<Dependency<EntityStore>> getDependencies() { return Collections.singleton(RootDependency.first()); }
}
