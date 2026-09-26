package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.litematica.util.EntityUtils;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import me.aleksilassila.litematica.printer.mixin.printer.litematica.EasyPlaceUtilsAccessor;
import me.aleksilassila.litematica.printer.mixin.printer.litematica.InventoryUtilsAccessor;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialRequest;
import me.aleksilassila.litematica.printer.integration.litematica.LitematicaPickSlotAdapter;
import me.aleksilassila.litematica.printer.utils.minecraft.PlayerUtils;
import me.aleksilassila.litematica.printer.utils.minecraft.ToolSelectionUtils;
import me.aleksilassila.litematica.printer.utils.mods.QuickShulkerBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;

import static fi.dy.masa.malilib.util.InventoryUtils.*;

@SuppressWarnings({"DataFlowIssue", "SpellCheckingInspection", "GrazieInspection"})
public class InventoryUtils {
    private static final Minecraft client = Minecraft.getInstance();
    private static final int OFFHAND_SLOT_INDEX = 40;
    public static int getSelectedSlot(Inventory inventory) {
        //#if MC > 12104
        return inventory.getSelectedSlot();
        //#else
        //$$ return inventory.selected;
        //#endif
    }

    public static void setSelectedSlot(Inventory inventory, int slot) {
        //#if MC > 12101
        inventory.setSelectedSlot(slot);
        //#else
        //$$ inventory.selected = slot;
        //#endif
    }

    public static NonNullList<ItemStack> getMainStacks(Inventory inventory) {
        //#if MC > 12104
        return inventory.getNonEquipmentItems();
        //#else
        //$$ return inventory.items;
        //#endif
    }

    public static boolean playerHasAccessToItem(LocalPlayer playerEntity, Item item) {
        return playerHasAccessToItems(playerEntity, item);
    }

    public static boolean playerHasItemInInventory(LocalPlayer playerEntity, Item item) {
        if (playerEntity == null || item == null) {
            return false;
        }
        if (PlayerUtils.getAbilities(playerEntity).instabuild) {
            return true;
        }
        Inventory inventory = playerEntity.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasAccessToItems(LocalPlayer playerEntity, Item... items) {
        if (items == null || items.length == 0) return true;
        if (playerEntity == null) return false;
        if (PlayerUtils.getAbilities(playerEntity).instabuild) return true;
        if (!playerEntity.containerMenu.equals(playerEntity.inventoryMenu)) return false;
        Inventory inventory = playerEntity.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            Item inventoryItem = inventory.getItem(i).getItem();
            for (Item item : items) {
                if (inventoryItem == item) {
                    return true;
                }
            }
        }
        QuickShulkerBridge.requestItems(items, MaterialRequest.Source.PRINT);
        return false;
    }

    public static boolean setPickedItemToHand(ItemStack stack, Minecraft mc) {
        if (mc.player == null) return false;
        int slotNum = mc.player.getInventory().findSlotMatchingItem(stack);
        return setPickedItemToHand(slotNum, stack, mc);
    }

    public static void setHotbarSlot(int slot, Inventory inventory) {
        setSelectedSlot(inventory, slot);
        syncSelectedHotbarSlot();
    }

    public static void syncSelectedHotbarSlot() {
        LocalPlayer player = client.player;
        ClientPacketListener connection = client.getConnection();
        if (player == null || connection == null) {
            return;
        }
        connection.send(new ServerboundSetCarriedItemPacket(getSelectedSlot(player.getInventory())));
    }

    public static PickResult checkPickSlotAvailable(int sourceSlot, Minecraft mc) {
        if (mc.player == null) return PickResult.FAIL;
        Player player = mc.player;
        Inventory inventory = player.getInventory();
        if (Inventory.isHotbarSlot(sourceSlot)) return PickResult.SUCCESS;
        if (InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().isEmpty()) {
            return PickResult.FAIL_NO_PICK_SLOTS_CONFIGURED;
        }
        int hotbarSlot = sourceSlot;
        if (sourceSlot == -1 || !Inventory.isHotbarSlot(sourceSlot)) {
            hotbarSlot = InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(inventory);
        }
        if (hotbarSlot == -1) {
            hotbarSlot = LitematicaPickSlotAdapter.selectNextAvailable(player);
        }
        return hotbarSlot != -1 ? PickResult.SUCCESS : PickResult.FAIL_NO_SUITABLE_SLOT_FOUND;
    }

    public static boolean setPickedItemToHand(int sourceSlot, ItemStack stack, Minecraft mc) {
        if (mc.player == null) return false;
        Player player = mc.player;
        if (CarriedItemUtils.hasCarriedItem(player)) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (Inventory.isHotbarSlot(sourceSlot)) {
            setHotbarSlot(sourceSlot, inventory);
            return true;
        }
        if (InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().isEmpty()) {
            showMessageWithCooldown(Message.MessageType.WARNING, "litematica.message.warn.pickblock.no_valid_slots_configured");
            return false;
        }
        int hotbarSlot = sourceSlot;
        if (sourceSlot == -1 || !Inventory.isHotbarSlot(sourceSlot)) {
            hotbarSlot = InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(inventory);
        }
        if (hotbarSlot == -1) {
            hotbarSlot = LitematicaPickSlotAdapter.selectNextAvailable(player);
        }
        if (hotbarSlot != -1) {
            setHotbarSlot(hotbarSlot, inventory);
            if (EntityUtils.isCreativeMode(player)) {
                getMainStacks(inventory).set(hotbarSlot, stack.copy());
                client.gameMode.handleCreativeModeItemAdd(client.player.getMainHandItem(), 36 + hotbarSlot);
                return true;
            }
            EasyPlaceUtilsAccessor.callSetEasyPlaceLastPickBlockTime();
            return swapItemToMainHand(stack.copy(), mc);
        } else {
            showMessageWithCooldown(Message.MessageType.WARNING, "litematica.message.warn.pickblock.no_suitable_slot_found");
            return false;
        }
    }

    public static boolean swapItemToMainHand(ItemStack stackReference, Minecraft mc) {
        Player player = mc.player;
        if (player == null || CarriedItemUtils.hasCarriedItem(player)) return false;

        //#if MC > 12004
        boolean b = areStacksEqualIgnoreNbt(stackReference, player.getMainHandItem());
        //#else
        //$$ boolean b = areStacksEqual(stackReference, player.getMainHandItem());
        //#endif
        if (b) {
            return false;
        }

        int slot = findSlotWithItem(player.inventoryMenu, stackReference, true);
        if (slot != -1) {
            if (client.gameMode == null) {
                return false;
            }
            int currentHotbarSlot = getSelectedSlot(player.getInventory());
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, currentHotbarSlot, ContainerInput.SWAP, player);
            return !CarriedItemUtils.hasCarriedItem(player);
        }
        return false;
    }

    public static ItemStack getOffhandStack(Player player) {
        return player.getInventory().getItem(OFFHAND_SLOT_INDEX);
    }

    public static boolean setItemToOffhand(ItemStack stack, Minecraft mc) {
        if (mc.player == null) return false;
        Player player = mc.player;

        boolean isAlreadyInOffhand = areStacksEqual(stack, getOffhandStack(player));
        if (isAlreadyInOffhand) {
            return true;
        }

        if (EntityUtils.isCreativeMode(player)) {
            player.getInventory().setItem(OFFHAND_SLOT_INDEX, stack.copy());
            client.gameMode.handleCreativeModeItemAdd(getOffhandStack(player), OFFHAND_SLOT_INDEX);
            return true;
        }

        int sourceSlot = findSlotWithItem(player.inventoryMenu, stack, true);
        if (sourceSlot == -1) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "litematica.message.warn.pickblock.no_suitable_slot_found");
            return false;
        }

        if (client.gameMode == null) {
            return false;
        }
        client.gameMode.handleContainerInput(
                player.inventoryMenu.containerId,
                sourceSlot,
                OFFHAND_SLOT_INDEX,
                ContainerInput.SWAP,
                player
        );

        return true;
    }

    private static void showMessageWithCooldown(Message.MessageType type, String messageKey) {
        long currentTime = System.currentTimeMillis();
        if (!RuntimeAccess.get().inventoryMessageCooldown().shouldSend(messageKey, currentTime)) {
            return;
        }
        InfoUtils.showGuiOrInGameMessage(type, messageKey);
    }

    public static boolean switchToBestTool(LocalPlayer player, BlockState blockState) {
        ClientLevel level = client.level;
        BlockPos pos = player == null ? null : player.blockPosition();
        return ToolInventorySelector.switchToBestTool(client, player, blockState, level, pos);
    }

    public static boolean switchToBestTool(LocalPlayer player, BlockState blockState, BlockPos pos) {
        return ToolInventorySelector.switchToBestTool(client, player, blockState, client.level, pos);
    }

    public static boolean hasUsableSilkTouchTool(LocalPlayer player) {
        if (player == null || PlayerUtils.getAbilities(player).instabuild) {
            return false;
        }
        for (ItemStack stack : getMainStacks(player.getInventory())) {
            if (!stack.isEmpty() && ToolSelectionUtils.hasSilkTouch(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean switchToItems(LocalPlayer player, Item[] items) {
        return switchToItems(player, items, -1);
    }

    public static boolean switchToItemsWithReserve(LocalPlayer player, Item[] items, int reserveCount) {
        return switchToItems(player, items, Math.max(0, reserveCount));
    }

    private static boolean switchToItems(LocalPlayer player, Item[] items, int reserveCount) {
        return MaterialSelector.switchToItems(player, items, reserveCount);
    }

    public static boolean playerHasAccessToMatchingStack(
            LocalPlayer playerEntity,
            ItemStack creativeFallback,
            Predicate<ItemStack> predicate
    ) {
        if (playerEntity == null || predicate == null) {
            return false;
        }
        if (PlayerUtils.getAbilities(playerEntity).instabuild) {
            return creativeFallback != null && predicate.test(creativeFallback);
        }
        if (!playerEntity.containerMenu.equals(playerEntity.inventoryMenu)) {
            return false;
        }
        Inventory inventory = playerEntity.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHoldingAnyItem(LocalPlayer player, Item[] items) {
        if (items == null || items.length == 0) {
            return true;
        }
        Item heldItem = player.getMainHandItem().getItem();
        for (Item item : items) {
            if (heldItem.equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean switchToMatchingStack(
            LocalPlayer player,
            Predicate<ItemStack> predicate,
            ItemStack creativeFallback
    ) {
        return switchToMatchingStack(player, predicate, creativeFallback, -1);
    }

    public static boolean switchToMatchingStackWithReserve(
            LocalPlayer player,
            Predicate<ItemStack> predicate,
            ItemStack creativeFallback,
            int reserveCount
    ) {
        return switchToMatchingStack(player, predicate, creativeFallback, Math.max(0, reserveCount));
    }

    private static boolean switchToMatchingStack(
            LocalPlayer player,
            Predicate<ItemStack> predicate,
            ItemStack creativeFallback,
            int reserveCount
    ) {
        return MaterialSelector.switchToMatchingStack(player, predicate, creativeFallback, reserveCount);
    }

    public static int getConsumableSurplus(
            LocalPlayer player,
            ItemStack stack,
            @Nullable Predicate<ItemStack> requiredStackPredicate,
            int reserveCount
    ) {
        return MaterialSelector.getConsumableSurplus(player, stack, requiredStackPredicate, reserveCount);
    }

    public static ItemStack findReserveBlockedStack(
            LocalPlayer player,
            Item[] items,
            @Nullable Predicate<ItemStack> requiredStackPredicate,
            int reserveCount
    ) {
        return MaterialSelector.findReserveBlockedStack(player, items, requiredStackPredicate, reserveCount);
    }

    public enum PickResult {
        SUCCESS,
        FAIL,
        FAIL_NO_PICK_SLOTS_CONFIGURED,
        FAIL_NO_SUITABLE_SLOT_FOUND;

        public boolean isNoPickSlotsConfigured() {
            return this == FAIL_NO_PICK_SLOTS_CONFIGURED;
        }

        public boolean isNoSuitableSlotFound() {
            return this == FAIL_NO_SUITABLE_SLOT_FOUND;
        }

        public boolean isNoAvailableSlot() {
            return isNoPickSlotsConfigured() || isNoSuitableSlotFound();
        }

        public boolean isAvailable() {
            return this == SUCCESS;
        }
    }
}
