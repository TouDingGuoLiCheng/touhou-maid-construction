package com.touhoumaid.construction.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.logging.LogUtils;
import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.BlueprintRegion;
import com.touhoumaid.construction.blueprint.RawBlockState;
import com.touhoumaid.construction.build.StartLayerPolicy;
import com.touhoumaid.construction.placement.BlockStateResolve;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import com.touhoumaid.construction.placement.PlacementRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block-model ghost kept in a GPU buffer relative to the anchor.
 * Camera movement only resubmits that buffer; conflict red recolors vertices.
 */
final class GhostPreviewMesh {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BAKE_BUFFER_BYTES = 1 << 20;

    private final List<Layer> layers = new ArrayList<>();
    private Blueprint blueprint;
    private GhostMeshRefresh.Geometry geometry;
    private GhostMeshRefresh.Tint tint;
    private Blueprint failedBlueprint;
    private GhostMeshRefresh.Geometry failed;
    private boolean ready;
    private boolean tintUploaded;
    private BitSet conflictBits = new BitSet();
    private int appliedAlphaMilli = Integer.MIN_VALUE;

    boolean hasMesh() {
        return ready;
    }

    Blueprint blueprint() {
        return blueprint;
    }

    GhostMeshRefresh.Geometry geometry() {
        return geometry;
    }

    GhostMeshRefresh.Tint tint() {
        return tint;
    }

    boolean shouldSkip(Blueprint nextBlueprint, GhostMeshRefresh.Geometry next) {
        return failedBlueprint == nextBlueprint && failed != null && failed.equals(next);
    }

    void rebuild(
            Minecraft mc,
            Blueprint nextBlueprint,
            BlockPos origin,
            int yaw,
            int startLayer,
            String highlight,
            boolean building,
            boolean cover,
            GhostMeshRefresh.Geometry nextGeometry,
            GhostMeshRefresh.Tint nextTint) {
        RenderSystem.assertOnRenderThread();
        closeLayers();
        ready = false;
        tintUploaded = false;
        appliedAlphaMilli = Integer.MIN_VALUE;
        conflictBits = new BitSet();
        Capture capture = new Capture();
        Map<RenderType, List<Span>> spans = new LinkedHashMap<>();
        try {
            bake(mc, nextBlueprint, origin, yaw, startLayer, highlight, building, capture, spans);
            for (Map.Entry<RenderType, BufferBuilder> entry : capture.builders.entrySet()) {
                RenderType type = entry.getKey();
                BufferBuilder builder = entry.getValue();
                BufferBuilder.RenderedBuffer rendered = finish(builder);
                if (rendered == null) {
                    continue;
                }
                ByteBuffer original = copyBytes(rendered.vertexBuffer());
                rendered.release();
                List<Span> layerSpans = spans.getOrDefault(type, List.of());
                layers.add(new Layer(type, original, layerSpans));
            }
            this.blueprint = nextBlueprint;
            this.geometry = nextGeometry;
            this.failed = null;
            this.failedBlueprint = null;
            this.ready = true;
            retint(mc.level, origin, cover, nextTint);
        } catch (RuntimeException ex) {
            discardBuilding(capture);
            closeLayers();
            throw ex;
        }
    }

    void retint(BlockGetter level, BlockPos origin, boolean cover, GhostMeshRefresh.Tint nextTint) {
        RenderSystem.assertOnRenderThread();
        BitSet next = new BitSet();
        if (nextTint.applyConflict() && level != null && origin != null) {
            int bit = 0;
            for (Layer layer : layers) {
                for (Span span : layer.spans) {
                    if (span.highlight) {
                        continue;
                    }
                    BlockPos world = origin.offset(span.lx, span.ly, span.lz);
                    if (PlacementRules.isConflict(level.getBlockState(world), span.planned, cover)) {
                        next.set(bit);
                    }
                    bit++;
                }
            }
        }
        boolean changed = !tintUploaded
                || appliedAlphaMilli != nextTint.alphaMilli()
                || !next.equals(conflictBits);
        this.tint = nextTint;
        this.conflictBits = next;
        this.appliedAlphaMilli = nextTint.alphaMilli();
        if (!changed || layers.isEmpty()) {
            if (layers.isEmpty()) {
                tintUploaded = true;
            }
            return;
        }
        applyTint(nextTint, next);
        tintUploaded = true;
    }

    void draw(PoseStack pose, BlockPos origin) {
        if (!ready || layers.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        pose.pushPose();
        pose.translate(origin.getX(), origin.getY(), origin.getZ());
        Matrix4f modelView = new Matrix4f(pose.last().pose());
        pose.popPose();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // Never inherit whichever shader the previous stage happened to leave behind: under a
        // shader pack that program does not own this BLOCK-format buffer, and the ghost turns
        // into streaked garbage. Name the block program and the atlas explicitly, then restore.
        ShaderInstance previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        RenderSystem.setShader(GameRenderer::getRendertypeTranslucentShader);
        try {
            for (Layer layer : layers) {
                layer.type.setupRenderState();
                ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                    layer.gpu.bind();
                    layer.gpu.drawWithShader(modelView, projection, shader);
                    VertexBufferUnbind.unbind();
                }
                layer.type.clearRenderState();
            }
        } finally {
            VertexBufferUnbind.unbind();
            RenderSystem.setShaderTexture(0, previousTexture);
            if (previousShader != null) {
                RenderSystem.setShader(() -> previousShader);
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    void fail(Blueprint nextBlueprint, GhostMeshRefresh.Geometry next, RuntimeException ex) {
        LOGGER.warn("Failed to bake schematic ghost preview", ex);
        closeLayers();
        ready = false;
        blueprint = null;
        geometry = null;
        tint = null;
        failedBlueprint = nextBlueprint;
        failed = next;
    }

    void close() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        closeLayers();
        ready = false;
        blueprint = null;
        geometry = null;
        tint = null;
        failedBlueprint = null;
        failed = null;
        conflictBits = new BitSet();
        appliedAlphaMilli = Integer.MIN_VALUE;
        tintUploaded = false;
    }

    private void applyTint(GhostMeshRefresh.Tint nextTint, BitSet conflicts) {
        float alpha = nextTint.alphaMilli() / 1000.0F;
        float conflictAlpha = Math.min(0.85F, alpha + 0.15F);
        float highlightAlpha = Math.min(0.95F, alpha + 0.25F);
        int bit = 0;
        for (Layer layer : layers) {
            copyOnto(layer.original, layer.tinted);
            for (Span span : layer.spans) {
                float green = 1.0F;
                float blue = 1.0F;
                float spanAlpha = alpha;
                if (span.highlight) {
                    green = 0.15F;
                    blue = 0.12F;
                    spanAlpha = highlightAlpha;
                } else {
                    if (conflicts.get(bit)) {
                        green = 0.25F;
                        blue = 0.2F;
                        spanAlpha = conflictAlpha;
                    }
                    bit++;
                }
                tintSpan(layer, span, green, blue, spanAlpha);
            }
            upload(layer);
        }
    }

    private static void bake(
            Minecraft mc,
            Blueprint blueprint,
            BlockPos origin,
            int yaw,
            int startLayer,
            String highlight,
            boolean building,
            Capture capture,
            Map<RenderType, List<Span>> spans) {
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
        int clampedStart = StartLayerPolicy.clamp(startLayer, footprint.sizeY());
        boolean hasHighlight = highlight != null && !highlight.isEmpty();
        PoseStack pose = new PoseStack();
        for (BlueprintRegion region : blueprint.regions().values()) {
            Vec3i size = region.size();
            for (int y = 0; y < size.getY(); y++) {
                int schematicY = BlueprintPlacement.schematicY(blueprint, region, y);
                if (!StartLayerPolicy.includesLocalY(schematicY, clampedStart)) {
                    continue;
                }
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        RawBlockState raw = region.blockAt(x, y, z);
                        boolean highlighted = hasHighlight && highlight.equals(raw.name());
                        if (hasHighlight && !highlighted) {
                            continue;
                        }
                        BlockState planned = BlockStateResolve.resolveRotated(raw, yaw);
                        if (planned.isAir() && !highlighted) {
                            continue;
                        }
                        if (planned.isAir() && highlighted) {
                            planned = Blocks.RED_STAINED_GLASS.defaultBlockState();
                        }
                        BlockPos local = BlueprintPlacement.worldPos(
                                BlockPos.ZERO, blueprint, region, footprint, x, y, z, yaw);
                        if (building && SitePickController.placedCells().contains(origin.offset(local))) {
                            continue;
                        }
                        Map<RenderType, Integer> before = new IdentityHashMap<>();
                        for (RenderType type : capture.consumers.keySet()) {
                            before.put(type, capture.consumers.get(type).vertices);
                        }
                        pose.pushPose();
                        pose.translate(local.getX() + 0.02, local.getY() + 0.02, local.getZ() + 0.02);
                        pose.scale(0.96F, 0.96F, 0.96F);
                        try {
                            dispatcher.renderSingleBlock(
                                    planned,
                                    pose,
                                    capture,
                                    0xF000F0,
                                    OverlayTexture.NO_OVERLAY,
                                    ModelData.EMPTY,
                                    RenderType.translucent());
                        } catch (Exception ignored) {
                            // Missing models / exotic states — skip
                        }
                        pose.popPose();
                        for (Map.Entry<RenderType, CountingConsumer> entry : capture.consumers.entrySet()) {
                            int start = before.getOrDefault(entry.getKey(), 0);
                            int count = entry.getValue().vertices - start;
                            if (count <= 0) {
                                continue;
                            }
                            spans.computeIfAbsent(entry.getKey(), type -> new ArrayList<>())
                                    .add(new Span(
                                            start,
                                            count,
                                            local.getX(),
                                            local.getY(),
                                            local.getZ(),
                                            planned,
                                            highlighted));
                        }
                    }
                }
            }
        }
    }

    private static void tintSpan(Layer layer, Span span, float green, float blue, float alpha) {
        if (layer.colorOffset < 0 || layer.stride <= 0) {
            return;
        }
        ByteBuffer original = layer.original;
        ByteBuffer tinted = layer.tinted;
        int base = span.firstVertex * layer.stride + layer.colorOffset;
        for (int vertex = 0; vertex < span.vertexCount; vertex++) {
            int offset = base + vertex * layer.stride;
            int red = original.get(offset) & 255;
            int greenByte = original.get(offset + 1) & 255;
            int blueByte = original.get(offset + 2) & 255;
            int alphaByte = original.get(offset + 3) & 255;
            tinted.put(offset, (byte) red);
            tinted.put(offset + 1, (byte) Math.min(255, Math.round(greenByte * green)));
            tinted.put(offset + 2, (byte) Math.min(255, Math.round(blueByte * blue)));
            tinted.put(offset + 3, (byte) Math.min(255, Math.round(alphaByte * alpha)));
        }
    }

    private static void upload(Layer layer) {
        ByteBuffer src = layer.tinted;
        src.position(0);
        BufferBuilder builder = new BufferBuilder(Math.max(256, src.limit()));
        try {
            builder.begin(layer.mode, layer.format);
            builder.putBulkData(src);
            src.position(0);
            BufferBuilder.RenderedBuffer rendered = builder.end();
            layer.gpu.bind();
            layer.gpu.upload(rendered);
            VertexBufferUnbind.unbind();
        } catch (RuntimeException ex) {
            if (builder.building()) {
                builder.discard();
            }
            src.position(0);
            throw ex;
        }
    }

    private void closeLayers() {
        for (Layer layer : layers) {
            layer.close();
        }
        layers.clear();
    }

    private static void discardBuilding(Capture capture) {
        for (BufferBuilder builder : capture.builders.values()) {
            if (builder.building()) {
                builder.discard();
            }
        }
    }

    private static BufferBuilder.RenderedBuffer finish(BufferBuilder builder) {
        if (!builder.building()) {
            return null;
        }
        BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        if (rendered == null || rendered.isEmpty()) {
            if (rendered != null) {
                rendered.release();
            }
            return null;
        }
        return rendered;
    }

    private static ByteBuffer copyBytes(ByteBuffer view) {
        ByteBuffer src = view.duplicate();
        ByteBuffer dst = MemoryUtil.memAlloc(src.remaining());
        dst.put(src);
        dst.flip();
        return dst;
    }

    private static void copyOnto(ByteBuffer src, ByteBuffer dst) {
        src.position(0);
        dst.clear();
        dst.put(src);
        dst.flip();
        src.position(0);
    }

    private static int colorOffset(VertexFormat format) {
        List<VertexFormatElement> elements = format.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).getUsage() == VertexFormatElement.Usage.COLOR) {
                return format.getOffset(i);
            }
        }
        return -1;
    }

    private static final class Span {
        private final int firstVertex;
        private final int vertexCount;
        private final int lx;
        private final int ly;
        private final int lz;
        private final BlockState planned;
        private final boolean highlight;

        private Span(
                int firstVertex,
                int vertexCount,
                int lx,
                int ly,
                int lz,
                BlockState planned,
                boolean highlight) {
            this.firstVertex = firstVertex;
            this.vertexCount = vertexCount;
            this.lx = lx;
            this.ly = ly;
            this.lz = lz;
            this.planned = planned;
            this.highlight = highlight;
        }
    }

    private static final class Layer {
        private final RenderType type;
        private final VertexFormat format;
        private final VertexFormat.Mode mode;
        private final com.mojang.blaze3d.vertex.VertexBuffer gpu;
        private final ByteBuffer original;
        private final ByteBuffer tinted;
        private final int stride;
        private final int colorOffset;
        private final List<Span> spans;

        private Layer(RenderType type, ByteBuffer original, List<Span> spans) {
            this.type = type;
            this.format = type.format();
            this.mode = type.mode();
            this.gpu = new com.mojang.blaze3d.vertex.VertexBuffer(
                    com.mojang.blaze3d.vertex.VertexBuffer.Usage.DYNAMIC);
            this.original = original;
            this.tinted = MemoryUtil.memAlloc(original.capacity());
            this.stride = format.getVertexSize();
            this.colorOffset = colorOffset(format);
            this.spans = spans;
        }

        private void close() {
            gpu.close();
            MemoryUtil.memFree(original);
            MemoryUtil.memFree(tinted);
        }
    }

    private static final class Capture implements MultiBufferSource {
        private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        private final Map<RenderType, CountingConsumer> consumers = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            CountingConsumer consumer = consumers.get(type);
            if (consumer == null) {
                BufferBuilder builder = new BufferBuilder(BAKE_BUFFER_BYTES);
                builder.begin(type.mode(), type.format());
                consumer = new CountingConsumer(builder);
                builders.put(type, builder);
                consumers.put(type, consumer);
            }
            return consumer;
        }
    }

    private static final class CountingConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private int vertices;

        private CountingConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
            vertices++;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    /** Named apart from {@link com.mojang.blaze3d.vertex.VertexBuffer} so the layer field stays readable. */
    private static final class VertexBufferUnbind {
        private VertexBufferUnbind() {
        }

        private static void unbind() {
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        }
    }
}
