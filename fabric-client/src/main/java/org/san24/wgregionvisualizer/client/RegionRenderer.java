package org.san24.wgregionvisualizer.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class RegionRenderer implements AutoCloseable {
    private static final RegionRenderer INSTANCE = new RegionRenderer();
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("wg_region_visualizer", "pipeline/filled_through_walls"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build()
    );
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1F, 1F, 1F, 1F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    private RegionRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(INSTANCE::renderAndDraw);
    }

    public static RegionRenderer instance() {
        return INSTANCE;
    }

    private void renderAndDraw(WorldRenderContext context) {
        if (!renderRegions(context)) {
            return;
        }
        drawFilledThroughWalls(Minecraft.getInstance(), FILLED_THROUGH_WALLS);
    }

    private boolean renderRegions(WorldRenderContext context) {
        Map<String, VisualizedRegion> regions = RegionStore.regions();
        Minecraft minecraft = Minecraft.getInstance();
        if (regions.isEmpty() || minecraft.level == null) {
            return false;
        }

        PoseStack matrices = context.matrices();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        String currentDimension = minecraft.level.dimension().identifier().toString();
        boolean renderedAny = false;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        if (buffer == null) {
            buffer = new BufferBuilder(ALLOCATOR, FILLED_THROUGH_WALLS.getVertexFormatMode(), FILLED_THROUGH_WALLS.getVertexFormat());
        }

        Matrix4fc matrix = matrices.last().pose();
        for (VisualizedRegion region : regions.values()) {
            if (!isCurrentWorld(region, currentDimension)) {
                continue;
            }
            renderRegion(matrix, region);
            renderedAny = true;
        }

        matrices.popPose();
        if (!renderedAny) {
            buffer = null;
        }
        return renderedAny;
    }

    private static boolean isCurrentWorld(VisualizedRegion region, String currentDimension) {
        if (!region.dimension().isBlank()) {
            return region.dimension().equals(currentDimension);
        }

        String world = region.world();
        return world.isBlank()
                || world.equals(currentDimension)
                || world.equals("minecraft:" + currentDimension)
                || ("minecraft:overworld".equals(currentDimension) && "world".equals(world))
                || ("minecraft:the_nether".equals(currentDimension) && (world.equals("world_nether") || world.endsWith("_nether")))
                || ("minecraft:the_end".equals(currentDimension) && (world.equals("world_the_end") || world.endsWith("_the_end")));
    }

    private void renderRegion(Matrix4fc matrix, VisualizedRegion region) {
        if (VisualizerConfig.SHOW_TRANSLUCENT_FACES) {
            renderFilledBox(matrix, region.minX(), region.minY(), region.minZ(), region.maxX() + 1.0F, region.maxY() + 1.0F, region.maxZ() + 1.0F,
                    VisualizerConfig.RED, VisualizerConfig.GREEN, VisualizerConfig.BLUE, VisualizerConfig.FACE_ALPHA);
        }

        if (region.shape() == VisualizedRegion.Shape.POLYGON) {
            renderPolygon(matrix, region);
        } else {
            renderCuboid(matrix, region);
        }
        // TODO: Add optional region ID labels near each region center.
    }

    private void renderCuboid(Matrix4fc matrix, VisualizedRegion region) {
        float minX = region.minX();
        float minY = region.minY();
        float minZ = region.minZ();
        float maxX = region.maxX() + 1.0F;
        float maxY = region.maxY() + 1.0F;
        float maxZ = region.maxZ() + 1.0F;

        edge(matrix, minX, minY, minZ, maxX, minY, minZ);
        edge(matrix, minX, minY, maxZ, maxX, minY, maxZ);
        edge(matrix, minX, maxY, minZ, maxX, maxY, minZ);
        edge(matrix, minX, maxY, maxZ, maxX, maxY, maxZ);

        edge(matrix, minX, minY, minZ, minX, minY, maxZ);
        edge(matrix, maxX, minY, minZ, maxX, minY, maxZ);
        edge(matrix, minX, maxY, minZ, minX, maxY, maxZ);
        edge(matrix, maxX, maxY, minZ, maxX, maxY, maxZ);

        edge(matrix, minX, minY, minZ, minX, maxY, minZ);
        edge(matrix, maxX, minY, minZ, maxX, maxY, minZ);
        edge(matrix, minX, minY, maxZ, minX, maxY, maxZ);
        edge(matrix, maxX, minY, maxZ, maxX, maxY, maxZ);
    }

    private void renderPolygon(Matrix4fc matrix, VisualizedRegion region) {
        int size = region.points().size();
        float minY = region.minY();
        float maxY = region.maxY() + 1.0F;
        for (int index = 0; index < size; index++) {
            VisualizedRegion.PointXZ start = region.points().get(index);
            VisualizedRegion.PointXZ end = region.points().get((index + 1) % size);
            edge(matrix, start.x(), minY, start.z(), end.x(), minY, end.z());
            edge(matrix, start.x(), maxY, start.z(), end.x(), maxY, end.z());
            edge(matrix, start.x(), minY, start.z(), start.x(), maxY, start.z());
        }
    }

    private void edge(Matrix4fc matrix, float x1, float y1, float z1, float x2, float y2, float z2) {
        float half = VisualizerConfig.LINE_THICKNESS / 2.0F;
        float minX = Math.min(x1, x2);
        float minY = Math.min(y1, y2);
        float minZ = Math.min(z1, z2);
        float maxX = Math.max(x1, x2);
        float maxY = Math.max(y1, y2);
        float maxZ = Math.max(z1, z2);

        if (minX == maxX) {
            minX -= half;
            maxX += half;
        }
        if (minY == maxY) {
            minY -= half;
            maxY += half;
        }
        if (minZ == maxZ) {
            minZ -= half;
            maxZ += half;
        }

        renderFilledBox(matrix, minX, minY, minZ, maxX, maxY, maxZ,
                VisualizerConfig.RED, VisualizerConfig.GREEN, VisualizerConfig.BLUE, VisualizerConfig.ALPHA);
    }

    private void renderFilledBox(Matrix4fc positionMatrix, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float red, float green, float blue, float alpha) {
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    private void drawFilledThroughWalls(Minecraft client, RenderPipeline pipeline) {
        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();
        GpuBuffer vertices = upload(drawParameters, format, builtBuffer);

        draw(client, pipeline, builtBuffer, drawParameters, vertices, format);
        vertexBuffer.rotate();
        buffer = null;
    }

    private GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = new MappableRingBuffer(() -> "wg_region_visualizer region renderer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }
        return vertexBuffer.currentBuffer();
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer, MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "wg_region_visualizer region renderer", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
    }

    @Override
    public void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
