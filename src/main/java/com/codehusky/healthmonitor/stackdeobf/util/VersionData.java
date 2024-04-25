/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.stackdeobf.util;
// Created by booky10 in StackDeobfuscator (19:41 06.07.23)

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class VersionData {

    private static final Gson GSON = new Gson();

    private final String id;
    private final String name;
    private final int worldVersion;
    private final int protocolVersion;
    private final OffsetDateTime buildTime;

    private VersionData(String id, String name, int worldVersion, int protocolVersion, OffsetDateTime buildTime) {
        this.id = id;
        this.name = name;
        this.worldVersion = worldVersion;
        this.protocolVersion = protocolVersion;
        this.buildTime = buildTime;
    }

    public static VersionData fromClasspath() {
        WorldVersion version = DetectedVersion.tryDetectVersion();
        return new VersionData(
                version.getId(),
                version.getName(),
                version.getDataVersion().getVersion(),
                SharedConstants.getProtocolVersion(),
                LocalDate.ofInstant(version.getBuildTime().toInstant(), ZoneId.systemDefault()).atTime(0,0).atOffset(ZoneOffset.UTC)
        );
//        JsonObject object;
//        try (InputStream input = VersionData.class.getResourceAsStream("/version.json");
//             Reader reader = new InputStreamReader(Objects.requireNonNull(input, "version.json not found"))) {
//            object = GSON.fromJson(reader, JsonObject.class);
//        } catch (Throwable throwable) {
//            throw new RuntimeException("Error while reading version data from classpath", throwable);
//        }
//        return fromJson(object);
    }

    public static VersionData fromJson(Path path) throws IOException {
        JsonObject object;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            object = GSON.fromJson(reader, JsonObject.class);
        }
        return fromJson(object);
    }

    public static VersionData fromJson(JsonObject object) {
        String id = object.get("id").getAsString();
        String name = object.get("name").getAsString();
        int worldVersion = object.get("world_version").getAsInt();
        int protocolVersion = object.get("protocol_version").getAsInt();

        String buildTimeStr = object.get("build_time").getAsString();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        OffsetDateTime buildTime = OffsetDateTime.parse(buildTimeStr, formatter);

        return new VersionData(id, name, worldVersion, protocolVersion, buildTime);
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getWorldVersion() {
        return this.worldVersion;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public OffsetDateTime getBuildTime() {
        return this.buildTime;
    }

    @Override
    public String toString() {
        return "VersionData{id='" + this.id + '\'' + ", name='" + this.name + '\'' + ", worldVersion=" + this.worldVersion + ", protocolVersion=" + this.protocolVersion + ", buildTime=" + this.buildTime + '}';
    }
}
