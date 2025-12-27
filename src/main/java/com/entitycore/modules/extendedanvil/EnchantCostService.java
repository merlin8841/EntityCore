package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public final class EnchantCostService {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;

    public EnchantCostService(JavaPlugin plugin, ExtendedAnvilConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public static final class ApplyPreview {
        public final boolean canApply;
        public final int levelCost;
        public final ItemStack result;
        public final boolean isVanillaExact;

        ApplyPreview(boolean canApply, int levelCost, ItemStack result, boolean isVanillaExact) {
            this.canApply = canApply;
            this.levelCost = levelCost;
            this.result = result;
            this.isVanillaExact = isVanillaExact;
        }
    }

    public ApplyPreview previewApply(Player viewer, ItemStack item, ItemStack book) {
        if (viewer == null) return new ApplyPreview(false, 0, null, false);

        if (item == null || item.getType() == Material.AIR) return new ApplyPreview(false, 0, null, false);
        if (book == null || book.getType() != Material.ENCHANTED_BOOK) return new ApplyPreview(false, 0, null, false);
        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) return new ApplyPreview(false, 0, null, false);

        VanillaSim.Result sim = VanillaSim.trySimulateAnvilApply(viewer, item, book);
        if (sim == null || sim.result == null || sim.result.getType() == Material.AIR) {
            return new ApplyPreview(false, 0, null, false);
        }

        // If no effective change, deny
        if (sameEnchants(item, sim.result)) {
            return new ApplyPreview(false, 0, null, true);
        }

        ItemStack capped = enforceCaps(sim.result);

        int cost = Math.max(0, sim.cost);
        // multiplier exists but default 1.0
        cost = (int) Math.ceil(cost * config.getApplyMultiplier());
        if (cost < 1) cost = 1;

        return new ApplyPreview(true, cost, capped, true);
    }

    private boolean sameEnchants(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        return Objects.equals(a.getEnchantments(), b.getEnchantments());
    }

    private ItemStack enforceCaps(ItemStack result) {
        if (result == null || result.getType() == Material.AIR) return result;

        ItemStack out = result.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta == null) return out;

        Map<org.bukkit.enchantments.Enchantment, Integer> ench = new HashMap<>(out.getEnchantments());
        boolean changed = false;

        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : ench.entrySet()) {
            org.bukkit.enchantments.Enchantment en = e.getKey();
            int lvl = e.getValue() == null ? 0 : e.getValue();
            if (en == null || lvl <= 0) continue;

            int cap = config.getCapFor(en.getKey().toString(), en.getMaxLevel());
            if (cap == 0) {
                meta.removeEnchant(en);
                changed = true;
                continue;
            }

            if (lvl > cap) {
                meta.removeEnchant(en);
                meta.addEnchant(en, cap, false);
                changed = true;
            }
        }

        if (changed) out.setItemMeta(meta);
        return out;
    }

    private static final class VanillaSim {

        static final class Result {
            final int cost;
            final ItemStack result;
            Result(int cost, ItemStack result) {
                this.cost = cost;
                this.result = result;
            }
        }

        static Result trySimulateAnvilApply(Player viewer, ItemStack left, ItemStack right) {
            try {
                String craftPkg = Bukkit.getServer().getClass().getPackage().getName();
                Class<?> craftPlayerClz = Class.forName(craftPkg + ".entity.CraftPlayer");
                Object craftPlayer = craftPlayerClz.cast(viewer);

                Method getHandle = craftPlayerClz.getMethod("getHandle");
                Object serverPlayer = getHandle.invoke(craftPlayer);

                Method getInventory = serverPlayer.getClass().getMethod("getInventory");
                Object nmsInv = getInventory.invoke(serverPlayer);

                Class<?> claClz = Class.forName("net.minecraft.world.inventory.ContainerLevelAccess");
                Object accessNull = null;
                try {
                    Field fNull = claClz.getField("NULL");
                    accessNull = fNull.get(null);
                } catch (NoSuchFieldException ignore) {
                    for (Field f : claClz.getDeclaredFields()) {
                        if (f.getType().equals(claClz)) {
                            f.setAccessible(true);
                            accessNull = f.get(null);
                            break;
                        }
                    }
                }
                if (accessNull == null) return null;

                Class<?> anvilMenuClz = Class.forName("net.minecraft.world.inventory.AnvilMenu");
                Object menu = constructAnvilMenu(anvilMenuClz, nmsInv, accessNull);
                if (menu == null) return null;

                Object nmsLeft = asNmsCopy(left);
                Object nmsRight = asNmsCopy(right);
                if (nmsLeft == null || nmsRight == null) return null;

                Object inputSlots = getField(menu, "inputSlots");
                if (inputSlots == null) inputSlots = findFirstFieldAssignable(menu, Class.forName("net.minecraft.world.Container"));
                if (inputSlots == null) return null;

                Class<?> nmsItemStackClz = Class.forName("net.minecraft.world.item.ItemStack");
                Method setItem = inputSlots.getClass().getMethod("setItem", int.class, nmsItemStackClz);
                setItem.invoke(inputSlots, 0, nmsLeft);
                setItem.invoke(inputSlots, 1, nmsRight);

                Method createResult = findMethod(menu.getClass(), "createResult");
                if (createResult != null) createResult.invoke(menu);

                Object resultSlots = getField(menu, "resultSlots");
                if (resultSlots == null) resultSlots = findFirstFieldAssignable(menu, Class.forName("net.minecraft.world.Container"));
                if (resultSlots == null) return null;

                Method getItem = resultSlots.getClass().getMethod("getItem", int.class);
                Object nmsOut = getItem.invoke(resultSlots, 0);

                ItemStack bukkitOut = asBukkitCopy(nmsOut);
                if (bukkitOut == null) return null;

                Integer cost = readAnvilCost(menu);
                if (cost == null) cost = 0;

                return new Result(cost, bukkitOut);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object constructAnvilMenu(Class<?> anvilMenuClz, Object nmsInv, Object accessNull) {
            try {
                for (Constructor<?> c : anvilMenuClz.getConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length == 3 && p[0] == int.class) {
                        return c.newInstance(0, nmsInv, accessNull);
                    }
                    if (p.length == 2 && p[0] == int.class) {
                        return c.newInstance(0, nmsInv);
                    }
                }
            } catch (Throwable ignored) {}
            return null;
        }

        private static Integer readAnvilCost(Object menu) {
            try {
                Method m = findMethod(menu.getClass(), "getCost");
                if (m != null) {
                    Object v = m.invoke(menu);
                    if (v instanceof Integer) return (Integer) v;
                }
            } catch (Throwable ignored) {}

            try {
                Object costField = getField(menu, "cost");
                if (costField instanceof Integer) return (Integer) costField;

                if (costField != null) {
                    Method get = findMethod(costField.getClass(), "get");
                    if (get != null) {
                        Object v = get.invoke(costField);
                        if (v instanceof Integer) return (Integer) v;
                    }
                }
            } catch (Throwable ignored) {}

            return null;
        }

        private static Object asNmsCopy(ItemStack bukkit) {
            try {
                String craftPkg = Bukkit.getServer().getClass().getPackage().getName();
                Class<?> cis = Class.forName(craftPkg + ".inventory.CraftItemStack");
                Method m = cis.getMethod("asNMSCopy", ItemStack.class);
                return m.invoke(null, bukkit);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static ItemStack asBukkitCopy(Object nms) {
            try {
                String craftPkg = Bukkit.getServer().getClass().getPackage().getName();
                Class<?> cis = Class.forName(craftPkg + ".inventory.CraftItemStack");
                Class<?> nmsItemStackClz = Class.forName("net.minecraft.world.item.ItemStack");
                Method m = cis.getMethod("asBukkitCopy", nmsItemStackClz);
                return (ItemStack) m.invoke(null, nms);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object getField(Object obj, String name) {
            try {
                Field f = obj.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object findFirstFieldAssignable(Object obj, Class<?> assignableTo) {
            try {
                for (Field f : obj.getClass().getDeclaredFields()) {
                    if (assignableTo.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return f.get(obj);
                    }
                }
            } catch (Throwable ignored) {}
            return null;
        }

        private static Method findMethod(Class<?> clz, String name) {
            try {
                Method m = clz.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {}
            try {
                Method m = clz.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
