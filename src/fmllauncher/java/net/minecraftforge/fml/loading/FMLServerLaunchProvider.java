/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ITransformingClassLoader;
import net.minecraftforge.api.distmarker.Dist;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class FMLServerLaunchProvider extends FMLCommonLaunchHandler implements ILaunchHandlerService
{
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public String name()
    {
        return "fmlserver";
    }

    @Override
    public Callable<Void> launchService(String[] arguments, ITransformingClassLoader launchClassLoader)
    {
        return () -> {
            super.beforeStart(launchClassLoader);
            launchClassLoader.addTargetPackageFilter(getPackagePredicate());
            Class.forName("net.minecraft.server.Main", true, launchClassLoader.getInstance()).getMethod("main", String[].class).invoke(null, (Object)arguments);
            return null;
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setup(final IEnvironment environment, final Map<String, ?> arguments) {
        final List<String> mavenRoots = new ArrayList<>((List<String>) arguments.get("mavenRoots"));
        final List<String> mods = new ArrayList<>((List<String>) arguments.get("mods"));
        mavenRoots.add(LibraryFinder.findLibsPath().toString());
        final String forgeVersion = (String) arguments.get("forgeVersion");
        final String mcVersion = (String) arguments.get("mcVersion");
        final String forgeGroup = (String) arguments.get("forgeGroup");
        mods.add(forgeGroup+":mohist:universal:"+mcVersion+"-"+forgeVersion);
        // generics are gross yea?
        ((Map)arguments).put("mavenRoots", mavenRoots);
        ((Map)arguments).put("mods", mods);
    }

    @Override
    public Dist getDist()
    {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    protected String getNaming() {
        return "srg";
    }

    @Override
    public Path[] getPaths() {
        return FMLLoader.getMCPaths();
    }

    @Override
    public boolean isProduction() {
        return true;
    }
}
