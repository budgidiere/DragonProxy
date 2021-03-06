/*
 * DragonProxy
 * Copyright (C) 2016-2020 Dragonet Foundation
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
 * You can view the LICENSE file for more details.
 *
 * https://github.com/DragonetMC/DragonProxy
 */
package org.dragonet.proxy.util;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.property.PropertyException;
import com.github.steveice10.mc.auth.service.SessionService;
import com.nukkitx.protocol.bedrock.data.ImageData;
import com.nukkitx.protocol.bedrock.data.SerializedSkin;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.dragonet.proxy.DragonProxy;
import org.dragonet.proxy.network.session.ProxySession;
import org.dragonet.proxy.network.session.cache.PlayerListCache;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

@Log4j2
public class SkinUtils {
    private static final String NORMAL_RESOURCE_PATCH = "ewogICAiZ2VvbWV0cnkiIDogewogICAgICAiZGVmYXVsdCIgOiAiZ2VvbWV0cnkuaHVtYW5vaWQuY3VzdG9tIgogICB9Cn0K";
    private static final String SLIM_RESOURCE_PATCH = "ewogICAiZ2VvbWV0cnkiIDogewogICAgICAiZGVmYXVsdCIgOiAiZ2VvbWV0cnkuaHVtYW5vaWQuY3VzdG9tU2xpbSIKICAgfQp9";

    private static final SessionService service = new SessionService();

    public static ImageData STEVE_SKIN;

    static {
        try {
            STEVE_SKIN = parseBufferedImage(ImageIO.read(DragonProxy.class.getClassLoader().getResource("skin_steve.png")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SerializedSkin createSkinEntry(ImageData skinImage, GameProfile.TextureModel model, ImageData capeImage) {
        // Skin Geometry is hard coded otherwise players will turn invisible if joining with custom models
        String skinResourcePatch = NORMAL_RESOURCE_PATCH;
        if(model == GameProfile.TextureModel.SLIM) {
            skinResourcePatch = SLIM_RESOURCE_PATCH;
        }

        String randomId = UUID.randomUUID().toString();
        return SerializedSkin.of(
            randomId,
            new String(Base64.getDecoder().decode(skinResourcePatch)),
            skinImage,
            Collections.emptyList(),
            capeImage,
            "",
            "",
            false,
            false,
            false,
            "",
            randomId);
    }

    /**
     * Fetches a skin from the Mojang session server
     */
    public static ImageData fetchSkin(ProxySession session, GameProfile profile) {
        // TODO: HANDLE RATE LIMITING
        PlayerListCache playerListCache = session.getPlayerListCache();

        // Check if the skin is already cached
        if(playerListCache.getRemoteSkinCache().containsKey(profile.getId())) {
            //log.warn("Retrieving from cache: " + profile.getName());
            return playerListCache.getRemoteSkinCache().get(profile.getId());
        }

        try {
            service.fillProfileTextures(profile, false);
        } catch (PropertyException e) {
            log.warn("Failed to fill profile with textures: " + e.getMessage());
            return null;
        }

        GameProfile.Texture texture = profile.getTexture(GameProfile.TextureType.SKIN);
        if(texture != null) {
            try {
                ImageData skin = parseBufferedImage(ImageIO.read(new URL(texture.getURL())));
                playerListCache.getRemoteSkinCache().put(profile.getId(), skin); // Cache the skin
                return skin;
            } catch (IOException e) {
                log.warn("Failed to fetch skin for player " + profile.getName() + ": " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Checks if a player has an unofficial cape and if so downloads it from
     * their servers
     */
    public static ImageData fetchUnofficialCape(GameProfile profile) {
        for(CapeServers server : CapeServers.values()) {
            try {
                URL url = new URL(server.getUrl(profile));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                if (connection.getResponseCode() == 404) {
                    return null;
                }

                return parseBufferedImage(ImageIO.read(connection.getInputStream()));
            } catch (IOException e) {}
        }
        return null;
    }

    @RequiredArgsConstructor
    private enum CapeServers {
        OPTIFINE("http://s.optifine.net/capes/%s.png", CapeUrlType.USERNAME),
        MINECRAFTCAPES("https://minecraftcapes.co.uk/getCape/%s", CapeUrlType.UUID);

        private final String url;
        private final CapeUrlType type;

        private String getUrl(GameProfile profile) {
            switch(type) {
                case UUID:
                    return String.format(url, profile.getId().toString().replace("-", ""));
                case USERNAME:
                    return String.format(url, profile.getName());
            }
            return null;
        }
    }

    private enum CapeUrlType {
        UUID,
        USERNAME
    }

    private static ImageData parseBufferedImage(BufferedImage image) {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();

        for(int y = 0; y < image.getHeight(); ++y) {
            for(int x = 0; x < image.getWidth(); ++x) {
                Color color = new Color(image.getRGB(x, y), true);
                outputStream.write(color.getRed());
                outputStream.write(color.getGreen());
                outputStream.write(color.getBlue());
                outputStream.write(color.getAlpha());
            }
        }

        image.flush();
        return ImageData.of(image.getWidth(), image.getHeight(), outputStream.array);
    }
}
