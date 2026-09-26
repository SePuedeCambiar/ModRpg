package com.example.modrpg;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.magic.modular.ModEntities;
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

        // 1. Registro de Entidades (Proyectiles Mágicos)
        ModEntities.register(modEventBus);

        // 2. Inicializamos todas las habilidades del registro
        SkillRegistry.init();

        // 3. Registramos el canal de red
        ModMessages.register();

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info(">>> ModRpg inicializado con éxito con Soporte de Magia Modular! <<<");
    }
}