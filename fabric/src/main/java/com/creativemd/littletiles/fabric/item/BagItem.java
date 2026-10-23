package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.tile.Tile;
import java.util.Comparator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/** Portable material inventory. Vanilla menu synchronization is used on both sides. */
public final class BagItem extends Item {
    public static final int SIZE = 54;
    public BagItem(Properties properties) { super(properties); }
    @Override public boolean canFitInsideContainerItems() { return false; }

    public static boolean accepts(ItemStack stack) {
        return stack.is(LittleTilesContent.TILES) || (stack.getItem() instanceof BlockItem block
                && Tile.validMaterial(block.getBlock().defaultBlockState()));
    }

    public static SimpleContainer contents(ItemStack bag) {
        var container = new SimpleContainer(SIZE) {
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return accepts(stack); }
        };
        bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(container.getItems());
        container.addListener(changed -> bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(container.getItems())));
        return container;
    }

    @Override public InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack bag = player.getItemInHand(hand);
        var contents = contents(bag);
        if (player.isShiftKeyDown()) {
            contents.getItems().sort(Comparator.comparing((ItemStack stack) -> stack.isEmpty() ? "~"
                    : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).thenComparing(ItemStack::toString));
            contents.setChanged();
        } else {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new BagMenu(id, inventory, contents, bag, slot),
                    getName(bag)));
        }
        return InteractionResult.SUCCESS;
    }

    /** Protect the bound bag against pickup, hotbar/offhand swaps and shift-click. */
    public static final class BagMenu extends ChestMenu {
        private final ItemStack bag;
        private final int boundSlot;
        private final Inventory inventory;
        public BagMenu(int id, Inventory inventory, SimpleContainer contents, ItemStack bag, int boundSlot) {
            super(MenuType.GENERIC_9x6, id, inventory, contents, 6);
            this.inventory = inventory; this.bag = bag; this.boundSlot = boundSlot;
            for (int index = 0; index < slots.size(); index++) {
                Slot old = slots.get(index);
                boolean locked = old.container == inventory && old.getContainerSlot() == boundSlot;
                boolean storage = index < SIZE;
                Slot replacement = new Slot(old.container, old.getContainerSlot(), old.x, old.y) {
                    @Override public boolean mayPickup(Player player) { return !locked && super.mayPickup(player); }
                    @Override public boolean mayPlace(ItemStack stack) {
                        return !locked && stack != bag && (!storage || accepts(stack)) && super.mayPlace(stack);
                    }
                };
                replacement.index = index;
                slots.set(index, replacement);
            }
        }
        @Override public boolean stillValid(Player player) {
            return inventory.getItem(boundSlot) == bag && super.stillValid(player);
        }
        @Override public void clicked(int slot, int button, ClickType type, Player player) {
            if (!stillValid(player) || (type == ClickType.SWAP && button == boundSlot)) return;
            super.clicked(slot, button, type, player);
        }
        @Override public ItemStack quickMoveStack(Player player, int slot) {
            if (!stillValid(player) || slot < 0 || slot >= slots.size()) return ItemStack.EMPTY;
            Slot source = slots.get(slot);
            if (!source.mayPickup(player) || (slot >= SIZE && !accepts(source.getItem()))) return ItemStack.EMPTY;
            return super.quickMoveStack(player, slot);
        }
    }
}
