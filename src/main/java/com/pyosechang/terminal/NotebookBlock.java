package com.pyosechang.terminal;

import com.pyosechang.terminal.client.TerminalScreen;
import com.pyosechang.terminal.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class NotebookBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // Base (keyboard): 1,0,4 -> 15,2,15
    private static final VoxelShape BASE = Block.box(1, 0, 4, 15, 2, 15);
    // Screen: 1,2,2 -> 15,11,4
    private static final VoxelShape SCREEN = Block.box(1, 2, 2, 15, 11, 4);
    private static final VoxelShape SHAPE_NORTH = Shapes.or(BASE, SCREEN);

    // Rotated shapes
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(1, 0, 1, 15, 2, 12),
            Block.box(1, 2, 12, 15, 11, 14)
    );
    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(4, 0, 1, 15, 2, 15),
            Block.box(2, 2, 1, 4, 11, 15)
    );
    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(1, 0, 1, 12, 2, 15),
            Block.box(12, 2, 1, 14, 11, 15)
    );

    public NotebookBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide && FMLEnvironment.dist == Dist.CLIENT) {
            openTerminal();
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void openTerminal() {
        TerminalSessionManager.getOrCreate();
        Minecraft.getInstance().setScreen(new TerminalScreen(false));
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 7; // Screen glow
    }
}
