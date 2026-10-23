package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** A read-only payment plan until placement succeeds; exact micro-volume refunds. */
public final class MaterialPayment {
    private record Material(Block block, int color) {
        static Material of(Tile tile) { return new Material(tile.material().getBlock(), tile.color()); }
        Tile tile(MicroBox box) { return new Tile(block.defaultBlockState(), box, color, true); }
    }
    private record Debit(Container container, int slot, ItemStack expected) {}
    private final List<Debit> debits;
    private final List<ItemStack> refunds;

    private MaterialPayment(List<Debit> debits, List<ItemStack> refunds) {
        this.debits = List.copyOf(debits); this.refunds = List.copyOf(refunds);
    }

    public static Optional<MaterialPayment> plan(ServerPlayer player, List<Tile> blueprint) {
        if (!Tiles.valid(blueprint) || blueprint.isEmpty()) return Optional.empty();
        return planTiles(player, blueprint);
    }

    public static Optional<MaterialPayment> plan(ServerPlayer player, Blueprint blueprint) {
        return blueprint.valid() ? planTiles(player, blueprint.tiles()) : Optional.empty();
    }

    private static Optional<MaterialPayment> planTiles(ServerPlayer player, List<Tile> blueprint) {
        Map<Material, Long> needed = new HashMap<>();
        blueprint.forEach(t -> needed.merge(Material.of(t), t.box().volume(), Long::sum));
        List<Debit> debits = new ArrayList<>();
        Map<Material, Long> refunds = new LinkedHashMap<>();
        Inventory inventory = player.getInventory();
        // Do not inspect armor slots or turn blueprints/tools containing tile data into materials.
        for (int slot = 0; slot < inventory.getContainerSize() && !needed.isEmpty(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BagItem) {
                var bag = BagItem.contents(stack);
                for (int nested = 0; nested < bag.getContainerSize() && !needed.isEmpty(); nested++)
                    debit(bag, nested, needed, refunds, debits);
            } else debit(inventory, slot, needed, refunds, debits);
        }
        if (!needed.isEmpty()) return Optional.empty();
        List<ItemStack> returned = new ArrayList<>();
        refunds.forEach((material, volume) -> returned.addAll(stacks(material, volume)));
        return Optional.of(new MaterialPayment(debits, returned));
    }

    private static void debit(Container container, int slot, Map<Material, Long> needed,
                              Map<Material, Long> refunds, List<Debit> debits) {
        ItemStack stack = container.getItem(slot);
        Map<Material, Long> stock = new LinkedHashMap<>();
        if (stack.is(LittleTilesContent.TILES)) {
            Tiles.read(stack).ifPresent(tiles -> tiles.forEach(t -> stock.merge(Material.of(t),
                    t.box().volume() * stack.getCount(), Long::sum)));
        } else if (stack.getItem() instanceof BlockItem block && Tile.validMaterial(block.getBlock().defaultBlockState())) {
            stock.put(new Material(block.getBlock(), 0xFFFFFF), 4096L * stack.getCount());
        }
        if (stock.keySet().stream().noneMatch(needed::containsKey)) return;
        stock.replaceAll((material, available) -> {
            long cost = Math.min(needed.getOrDefault(material, 0L), available);
            needed.computeIfPresent(material, (key, amount) -> amount == cost ? null : amount - cost);
            return available - cost;
        });
        stock.forEach((material, remaining) -> { if (remaining > 0) refunds.merge(material, remaining, Long::sum); });
        debits.add(new Debit(container, slot, stack.copy()));
    }

    public boolean stillValid() {
        return debits.stream().allMatch(d -> ItemStack.matches(d.expected, d.container.getItem(d.slot)));
    }

    public void apply(ServerPlayer player) {
        if (!stillValid()) throw new IllegalStateException("Material inventory changed during placement");
        debits.forEach(d -> { d.container.setItem(d.slot, ItemStack.EMPTY); d.container.setChanged(); });
        for (ItemStack refund : refunds) {
            ItemStack returned = refund.copy();
            if (!player.getInventory().add(returned)) player.drop(returned, false);
        }
        player.getInventory().setChanged();
    }

    public static List<ItemStack> refund(Block block, int volume) {
        if (volume<0 || volume>4096) throw new IllegalArgumentException("Refund volume");
        return stacks(new Material(block,0xFFFFFF),volume);
    }

    private static List<ItemStack> stacks(Material material, long volume) {
        List<ItemStack> result = new ArrayList<>();
        long full = volume / 4096;
        while (full > 0) {
            int count = (int) Math.min(full, 64);
            ItemStack stack = new ItemStack(LittleTilesContent.TILES, count);
            Tiles.write(stack, List.of(material.tile(new MicroBox(0, 0, 0, 16, 16, 16))));
            result.add(stack); full -= count;
        }
        int rest = (int) (volume % 4096);
        if (rest > 0) {
            List<Tile> pieces = new ArrayList<>(3);
            int x = rest / 256, y = (rest % 256) / 16, z = rest % 16;
            if (x > 0) pieces.add(material.tile(new MicroBox(0, 0, 0, x, 16, 16)));
            if (y > 0) pieces.add(material.tile(new MicroBox(x, 0, 0, x + 1, y, 16)));
            if (z > 0) pieces.add(material.tile(new MicroBox(x, y, 0, x + 1, y + 1, z)));
            ItemStack stack = new ItemStack(LittleTilesContent.TILES);
            Tiles.write(stack, pieces); result.add(stack);
        }
        return result;
    }
}
