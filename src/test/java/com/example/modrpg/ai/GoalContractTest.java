package com.example.modrpg.ai;

import com.example.modrpg.ai.goals.TacticalBoundingGoal;
import com.example.modrpg.ai.goals.TacticalFlankGoal;
import com.example.modrpg.ai.goals.TacticalPeelGoal;
import com.example.modrpg.ai.goals.director.AmbushAssaultGoal;
import com.example.modrpg.ai.goals.director.StalkerLurkGoal;
import com.example.modrpg.ai.nemesis.goals.NemesisEscapeGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalContractTest {

    static Stream<Class<? extends Goal>> provideGoalClasses() {
        return Stream.of(
                AmbushAssaultGoal.class,
                StalkerLurkGoal.class,
                TacticalBoundingGoal.class,
                TacticalFlankGoal.class,
                TacticalPeelGoal.class,
                TacticalCasterGoal.class,
                NemesisEscapeGoal.class
        );
    }

    @ParameterizedTest(name = "La meta {0} debe sobrescribir requiresUpdateEveryTick()")
    @MethodSource("provideGoalClasses")
    @DisplayName("A1: Todas las metas tácticas deben sobrescribir requiresUpdateEveryTick() retornando true")
    void testGoalsMustRequireUpdateEveryTick(Class<? extends Goal> goalClass) throws Exception {
        Method method = goalClass.getMethod("requiresUpdateEveryTick");

        // Comprueba si la clase concreta sobrescribe el método de Goal.class
        boolean isOverridden = method.getDeclaringClass().equals(goalClass);

        assertTrue(
                isOverridden,
                () -> "FALLO CRÍTICO (A1): La clase " + goalClass.getSimpleName() +
                        " no sobrescribe requiresUpdateEveryTick(). " +
                        "En Minecraft 1.20.1 hereda 'false' de Goal.class, lo que hace que tick() " +
                        "solo se ejecute cada 2 ticks, duplicando la duración real de timers, cargas y aturdimientos."
        );
    }
}