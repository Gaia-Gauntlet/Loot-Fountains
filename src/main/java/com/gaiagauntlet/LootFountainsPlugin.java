package com.gaiagauntlet;

import com.creditor.Creditor;
import com.gaiagauntlet.interactions.LootFountainInteraction;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.logging.Level;

public class LootFountainsPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public LootFountainsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("Starting Loot Fountains!");
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("Setting up Loot Fountains!");
        Creditor.setup(this);

        getCodecRegistry(Interaction.CODEC).register(LootFountainInteraction.ID,
            LootFountainInteraction.class, LootFountainInteraction.CODEC);
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("Shutting down Loot Fountains!");
    }
}
