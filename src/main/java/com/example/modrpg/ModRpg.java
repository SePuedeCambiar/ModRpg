package com.example.modrpg;

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

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info(">>> ModRpg inicializado con éxito! <<<");
    }
}