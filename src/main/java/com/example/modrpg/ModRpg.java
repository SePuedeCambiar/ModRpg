package com.example.modrpg;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.skills.data.SkillRegistry;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ModRpg.MODID)
public class ModRpg {
    public static final String MODID = "modrpg";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModRpg() {
        // 1. Inicializamos todas las habilidades del registro
        SkillRegistry.init();

        // 2. Registramos el canal de red
        ModMessages.register();

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info(">>> ModRpg inicializado con éxito! <<<");
    }
}