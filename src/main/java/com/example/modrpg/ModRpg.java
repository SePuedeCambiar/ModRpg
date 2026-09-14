package com.example.modrpg;

import com.example.modrpg.networking.ModMessages;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ModRpg.MODID)
public class ModRpg {
    public static final String MODID = "modrpg";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModRpg() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ⚔️ Registramos el canal de red cliente <-> servidor
        ModMessages.register();

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info(">>> ModRpg inicializado con éxito! <<<");
    }
}