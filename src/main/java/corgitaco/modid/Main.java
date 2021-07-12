package corgitaco.modid;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import corgitaco.modid.path.WorldStructureAwareWarpedPathGenerator;
import net.minecraft.util.Util;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.HashSet;

@Mod(Main.MOD_ID)
public class Main {
    public static final String MOD_ID = "modid";
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Path CONFIG_PATH = new File(String.valueOf(FMLPaths.CONFIGDIR.get().resolve(MOD_ID))).toPath();
//    public static final HashSet<String> CITY_NAMES = Util.make(new HashSet<>(), (set) -> {
//        Path resolve = CONFIG_PATH.resolve("yeet.json");
//
//        try {
//            JsonParser jsonReader = new JsonParser();
//
//            JsonElement parse = jsonReader.parse(new FileReader(resolve.toFile()));
//
//            for (JsonElement jsonElement : parse.getAsJsonArray()) {
//                String name = jsonElement.getAsJsonObject().get("name").getAsString();
//                set.add(name);
//            }
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//    });

    public Main() {
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class WorldGenRegistries {
        @SubscribeEvent
        public static void registerFeatures(RegistryEvent.Register<Feature<?>> event) {
            Feature<NoFeatureConfig> path3 = WorldStructureAwareWarpedPathGenerator.PATH;
            event.getRegistry().registerAll(path3);
        }
    }
}
