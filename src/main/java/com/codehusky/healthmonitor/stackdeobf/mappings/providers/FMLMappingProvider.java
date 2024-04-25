/*
Sourcecode based on MojangMappingProvider sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Original code was licensed under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING.LESSER
 */
package com.codehusky.healthmonitor.stackdeobf.mappings.providers;

import com.codehusky.healthmonitor.HealthMonitor;
import com.codehusky.healthmonitor.stackdeobf.http.VerifiableUrl;
import com.codehusky.healthmonitor.stackdeobf.mappings.MappingCacheVisitor;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
import com.codehusky.healthmonitor.stackdeobf.http.HttpUtil;
import com.codehusky.healthmonitor.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FMLMappingProvider extends AbstractMappingProvider {


    private Path path;
    private Path mojangPath;
    private MemoryMappingTree mappings;
    private MemoryMappingTree mojangMappings;

    // the production/intermediary mappings need to be mapped back to their
    // obfuscated form, because mojang mappings are obfuscated -> named,
    // without the intermediary mappings inbetween
    private final IntermediaryMappingProvider intermediary;

    private final String environment;
    public FMLMappingProvider(VersionData versionData, String environment) {
        super(versionData, "forge");
        this.intermediary = new IntermediaryMappingProvider(versionData);
        this.environment = environment;
    }

    public MemoryMappingTree getMappings() {
        return mappings;
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        String version = this.versionData.getId();

        CompletableFuture<Void> intermediaryFuture = this.intermediary.downloadMappings0(cacheDir, executor);
        this.mojangPath = cacheDir.resolve("mojang_" + version + ".gz");
        this.path = cacheDir.resolve("forge_" + version + ".gz");
        if (Files.exists(this.mojangPath)) {
            return intermediaryFuture;
        }

        return intermediaryFuture.thenCompose($ -> this.fetchMojangMappingsUri(version, executor)
                .thenCompose(verifiableUrl -> verifiableUrl.get(executor))
                .thenAccept(resp -> {
                    try (OutputStream fileOutput = Files.newOutputStream(this.mojangPath);
                         GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
                        gzipOutput.write(resp.getBody());
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                }));
//        return intermediaryFuture.thenCompose($ -> HttpUtil.getAsync(URI.create("https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/" + version + "/joined.tsrg"),executor,0)
//                .thenAccept(resp -> {
//                    try (OutputStream fileOutput = Files.newOutputStream(this.path);
//                         GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
//                        gzipOutput.write(resp.getBody());
//                    } catch (IOException exception) {
//                        throw new RuntimeException(exception);
//                    }
//                }));
    }
    private static final Gson GSON = new Gson();
    private CompletableFuture<VerifiableUrl> fetchMojangMappingsUri(String mcVersion, Executor executor) {
        // some versions are not present in official version manifest, so use these hard-coded links
        VerifiableUrl staticUrl = null;
        if (staticUrl != null) {
            HealthMonitor.getLogger().warn("Static url found for {}, using {} for downloading", mcVersion, staticUrl.getUrl());
            return staticUrl.get(executor).thenApply(resp -> {
                JsonObject infoObj = null;
                try (ByteArrayInputStream input = new ByteArrayInputStream(resp.getBody());
                     ZipInputStream zipInput = new ZipInputStream(input)) {
                    ZipEntry entry;
                    while ((entry = zipInput.getNextEntry()) != null) {
                        if (!entry.getName().endsWith(".json")) {
                            continue;
                        }

                        try (Reader reader = new InputStreamReader(zipInput)) {
                            infoObj = GSON.fromJson(reader, JsonObject.class);
                        }
                        break;
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }

                if (infoObj == null) {
                    throw new IllegalStateException("No version metadata found in static profile for " + mcVersion);
                }

                JsonObject mappingsData = infoObj
                        .getAsJsonObject("downloads")
                        .getAsJsonObject(this.environment + "_mappings");
                URI mappingsUrl = URI.create(mappingsData.get("url").getAsString());
                String mappingsSha1 = mappingsData.get("sha1").getAsString();
                return new VerifiableUrl(mappingsUrl, VerifiableUrl.HashType.SHA1, mappingsSha1);
            });
        }

        URI manifestUri = URI.create(System.getProperty("com.codehusky.spikelogger.stackdeobf.stackdeobf.manifest-uri",
                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));

        // the server sends a "content-md5" header containing a md5 hash encoded as a base64-string,
        // but only if the http method is "HEAD"
        return VerifiableUrl.resolveByMd5Header(manifestUri, executor)
                .thenCompose(verifiableUrl -> verifiableUrl.get(executor))
                .thenCompose(manifestResp -> {
                    JsonObject manifestObj;
                    try (ByteArrayInputStream input = new ByteArrayInputStream(manifestResp.getBody());
                         Reader reader = new InputStreamReader(input)) {
                        manifestObj = GSON.fromJson(reader, JsonObject.class);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }

                    for (JsonElement element : manifestObj.getAsJsonArray("versions")) {
                        JsonObject elementObj = element.getAsJsonObject();
                        if (!mcVersion.equals(elementObj.get("id").getAsString())) {
                            continue;
                        }

                        URI infoUrl = URI.create(elementObj.get("url").getAsString());
                        String infoSha1 = elementObj.get("sha1").getAsString();
                        VerifiableUrl verifiableInfoUrl = new VerifiableUrl(infoUrl, VerifiableUrl.HashType.SHA1, infoSha1);

                        return verifiableInfoUrl.get(executor).thenApply(infoResp -> {
                            JsonObject infoObj;
                            try (ByteArrayInputStream input = new ByteArrayInputStream(infoResp.getBody());
                                 Reader reader = new InputStreamReader(input)) {
                                infoObj = GSON.fromJson(reader, JsonObject.class);
                            } catch (IOException exception) {
                                throw new RuntimeException(exception);
                            }

                            JsonObject mappingsData = infoObj
                                    .getAsJsonObject("downloads")
                                    .getAsJsonObject(this.environment + "_mappings");
                            URI mappingsUrl = URI.create(mappingsData.get("url").getAsString());
                            String mappingsSha1 = mappingsData.get("sha1").getAsString();
                            return new VerifiableUrl(mappingsUrl, VerifiableUrl.HashType.SHA1, mappingsSha1);
                        });
                    }

                    throw new IllegalStateException("Invalid minecraft version: " + mcVersion
                            + " (not found in mojang version manifest)");
                });
    }





    @Override
    protected CompletableFuture<Void> parseMappings0(Executor executor) {
        return this.intermediary.parseMappings0(executor).thenRun(() -> {
            try {
                MemoryMappingTree rawMappings = new MemoryMappingTree();

                try (InputStream fileInput = Files.newInputStream(this.mojangPath);
                     GZIPInputStream gzipInput = new GZIPInputStream(fileInput);
                     Reader reader = new InputStreamReader(gzipInput)) {
                    MappingReader.read(reader, MappingFormat.PROGUARD_FILE, rawMappings);
                }

                rawMappings.setSrcNamespace("named");
                rawMappings.setDstNamespaces(List.of("official"));

                // mappings provided by mojang are named -> obfuscated
                // this needs to be switched for the remapping to work properly

                MemoryMappingTree switchedMappings = new MemoryMappingTree();
                rawMappings.accept(new MappingSourceNsSwitch(switchedMappings, "official"));
                this.mojangMappings = switchedMappings;
//                this.mappings = switchedMappings;

                MemoryMappingTree rawFMLMappings = new MemoryMappingTree();

                try (InputStream fileInput = HealthMonitor.class.getClassLoader().getResourceAsStream("output.tsrg");
//                     GZIPInputStream gzipInput = new GZIPInputStream(fileInput);
                     Reader reader = new InputStreamReader(fileInput)) {
                    MappingReader.read(reader, MappingFormat.TSRG_2_FILE, rawFMLMappings);
                }
                this.mappings = rawFMLMappings;
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    @Override
    protected CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.mojangMappings.accept(new Visitor(visitor));
                if(visitor instanceof MappingCacheVisitor mappingCacheVisitor){
//                    mappingCacheVisitor.getMethods().forEach((mappingId, name) -> {
//                        HealthMonitor.getLogger().info("Existing Method Mapping: " + mappingId + " -> " + name);
//                    });
                    HealthMonitor.getLogger().debug("Scanning all classes : " + this.mappings.getClasses().size());
                    this.mappings.getClasses().forEach((classMapping) -> {
                        //MappingTree.ClassMapping classMapping = this.getMappings().getClass(name.replaceAll("\\.","/"));
                        //if(classMapping != null) {
                        String className = classMapping.getDstName(0);
                        MappingTree.ClassMapping mojangClass = this.mojangMappings.getClass(classMapping.getSrcName(), 0);
                        if(mojangClass == null){
                            HealthMonitor.getLogger().debug("No mojang class found " + className + "/" + classMapping.getSrcName());
                            return;
                        }

                        MappingTree.ClassMapping intermediateClass = this.intermediary.getMappings().getClass(mojangClass.getSrcName());
                        if(intermediateClass == null){
                            HealthMonitor.getLogger().debug("No intermediate class found " + mojangClass.getDstName(0) + " / " + mojangClass.getSrcName());
                            return;
                        }
                        classMapping.getMethods().forEach(methodMapping -> {
                            MappingTree.MethodMapping mojangMethod = mojangClass.getMethod(methodMapping.getSrcName(), methodMapping.getSrcDesc(), 0);
                            if(mojangMethod == null){
                                HealthMonitor.getLogger().debug("No mojang method found " + mojangClass.getDstName(0) + " / " + mojangClass.getSrcName() + " ? " + methodMapping.getSrcName());
                                return;
                            }
                            MappingTree.MethodMapping intermediateMethod = intermediateClass.getMethod(mojangMethod.getSrcName(), mojangMethod.getSrcDesc());
                            if(intermediateMethod == null){
                                HealthMonitor.getLogger().debug("No intermediate method found " + mojangClass.getDstName(0) + " / " + mojangClass.getSrcName() + " -> " + intermediateClass.getDstName(0) + " / " + intermediateClass.getSrcName() + " ? " + mojangMethod.getSrcName());
                                return;
                            }

                            String srcName = methodMapping.getSrcName();
                            String dstName = methodMapping.getDstName(0); // m_IDNUMBER_
                            String oldName = dstName;//intermediateMethod.getDstName(0);
                            if(oldName != null && oldName.startsWith("m_")) {
                                int methodId = Integer.parseInt(oldName.substring("m_".length(), oldName.length() - 1));
                                mappingCacheVisitor.getMethods().put(methodId, srcName);
                                HealthMonitor.getLogger().debug("Mapping " + className + "#" + dstName + " to " + srcName + " (" + methodId + ")");
                            }else{
                                HealthMonitor.getLogger().debug("Failed oldName: " + oldName + " / " + srcName + " -> " + dstName);
                            }
                        });

//                        }else{
//                            HealthMonitor.getLogger().warn("fucked up " + name + " / " + mappingId);
//                        }

                    });
                }else{
                    HealthMonitor.getLogger().error("Failed to perform FML mappings cause class name invalid? " + visitor.getClass().getName());
                }
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return null;
        }, executor);
    }

    private final class Visitor extends ForwardingMappingVisitor {

        private MappingTree.ClassMapping clazz;

        private Visitor(MappingVisitor next) {
            super(next);
        }

        @Override
        public boolean visitClass(String srcName) throws IOException {
            this.clazz = FMLMappingProvider.this.intermediary.getMappings().getClass(srcName);
            if (this.clazz == null) {
                return false;
            }
            return super.visitClass(this.clazz.getDstName(0));
        }

        @Override
        public boolean visitMethod(String srcName, String srcDesc) throws IOException {
            MappingTree.MethodMapping mapping = this.clazz.getMethod(srcName, srcDesc);
            if (mapping == null) {
                return false;
            }
//            }else{
//                MappingTree.MethodMapping fmlMapping = FMLMappingProvider.this.mappings.getMethod(this.clazz.getDstName(0), mapping.getDstName(0), mapping.getDstDesc(0));
//                if(fmlMapping != null){
//                    mapping = fmlMapping;
//                }else{
//                    HealthMonitor.getLogger().warn("Failed to lookup " + this.clazz.getDstName(0) + " / " + mapping.getDstName(0) + " / " + mapping.getDstDesc(0));
//                    HealthMonitor.getLogger().warn("     SRC: " +  this.clazz.getSrcName() + " / " + mapping.getSrcName() + " / " + mapping.getSrcDesc());
//                }
//            }
            return super.visitMethod(mapping.getDstName(0), mapping.getDstDesc(0));
        }

        @Override
        public boolean visitField(String srcName, String srcDesc) throws IOException {
            MappingTree.FieldMapping mapping = this.clazz.getField(srcName, srcDesc);
            if (mapping == null) {
                return false;
            }
//            }else{
//                MappingTree.FieldMapping fmlMapping = FMLMappingProvider.this.mappings.getField(this.clazz.getDstName(0), mapping.getDstName(0), mapping.getDstDesc(0));
//                if(fmlMapping != null){
//                    mapping = fmlMapping;
//                }
//            }
            return super.visitField(mapping.getDstName(0), mapping.getDstDesc(0));
        }

    }
}
