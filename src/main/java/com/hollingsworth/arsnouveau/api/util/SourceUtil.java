package com.hollingsworth.arsnouveau.api.util;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.api.mana.IManaEquipment;
import com.hollingsworth.arsnouveau.common.armor.MagicArmor;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import com.hollingsworth.arsnouveau.common.capability.CapabilityRegistry;
import com.hollingsworth.arsnouveau.common.enchantment.EnchantmentRegistry;
import com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile;
import com.hollingsworth.arsnouveau.common.potions.ModPotions;
import com.hollingsworth.arsnouveau.setup.Config;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
//TODO: Change to SourceUtil and method names
public class SourceUtil {

    public static int getPlayerDiscounts(LivingEntity e){
        AtomicInteger discounts = new AtomicInteger();
        CuriosUtil.getAllWornItems(e).ifPresent(items ->{

            for(int i = 0; i < items.getSlots(); i++){
                Item item = items.getStackInSlot(i).getItem();
                if(item instanceof IManaEquipment)
                    discounts.addAndGet(((IManaEquipment) item).getManaDiscount(items.getStackInSlot(i)));
            }
        });
        return discounts.get();
    }


    public static int getMaxMana(Player e){
        IManaCap mana = CapabilityRegistry.getMana(e).orElse(null);
        if(mana == null)
            return 0;
        int max = Config.INIT_MAX_MANA.get();
        for(ItemStack i : e.getAllSlots()){
            if(i.getItem() instanceof IManaEquipment){
                max += (((IManaEquipment) i.getItem()).getMaxManaBoost(i));
            }
            max += ( Config.MANA_BOOST_BONUS.get() * EnchantmentHelper.getItemEnchantmentLevel(EnchantmentRegistry.MANA_BOOST_ENCHANTMENT, i));
        }

        IItemHandlerModifiable items = CuriosUtil.getAllWornItems(e).orElse(null);
        if(items != null){
            for(int i = 0; i < items.getSlots(); i++){
                Item item = items.getStackInSlot(i).getItem();
                if(item instanceof IManaEquipment)
                    max += (((IManaEquipment) item).getMaxManaBoost(items.getStackInSlot(i)));
            }
        }

        int tier = mana.getBookTier();
        int numGlyphs = mana.getGlyphBonus() > 5 ? mana.getGlyphBonus() - 5 : 0;
        max += numGlyphs * Config.GLYPH_MAX_BONUS.get();
        max += tier * Config.TIER_MAX_BONUS.get();

        MaxManaCalcEvent event = new MaxManaCalcEvent(e, max);
        MinecraftForge.EVENT_BUS.post(event);
        max = event.getMax();
        return max;
    }

    public static double getManaRegen(Player e) {
        IManaCap mana = CapabilityRegistry.getMana(e).orElse(null);
        if(mana == null)
            return 0;
        double regen = Config.INIT_MANA_REGEN.get();
        for(ItemStack i : e.getAllSlots()){
            if(i.getItem() instanceof MagicArmor){
                MagicArmor armor = ((MagicArmor) i.getItem());
                regen += armor.getManaRegenBonus(i);
            }
            regen += Config.MANA_REGEN_ENCHANT_BONUS.get() * EnchantmentHelper.getItemEnchantmentLevel(EnchantmentRegistry.MANA_REGEN_ENCHANTMENT, i);
        }
        IItemHandlerModifiable items = CuriosUtil.getAllWornItems(e).orElse(null);
        if(items != null){
            for(int i = 0; i < items.getSlots(); i++){
                Item item = items.getStackInSlot(i).getItem();
                if(item instanceof IManaEquipment)
                    regen += ((IManaEquipment) item).getManaRegenBonus(items.getStackInSlot(i));
            }
        }

        int tier = mana.getBookTier();
        double numGlyphs = mana.getGlyphBonus() > 5 ? mana.getGlyphBonus() - 5 : 0;
        regen += numGlyphs * Config.GLYPH_REGEN_BONUS.get();
        regen += tier;
        if(e.getEffect(ModPotions.MANA_REGEN_EFFECT) != null)
            regen += Config.MANA_REGEN_POTION.get() * (1 + e.getEffect(ModPotions.MANA_REGEN_EFFECT).getAmplifier());
        ManaRegenCalcEvent event = new ManaRegenCalcEvent(e, regen);
        MinecraftForge.EVENT_BUS.post(event);
        regen = event.getRegen();
        return regen;
    }

    /**
     * Searches for nearby mana jars that have enough mana.
     * Returns the position where the mana was taken, or null if none were found.
     */
    @Nullable
    public static BlockPos takeManaNearby(BlockPos pos, Level world, int range, int mana){
        Optional<BlockPos> loc = BlockPos.findClosestMatch(pos, range, range, (b) -> world.getBlockEntity(b) instanceof SourceJarTile && ((SourceJarTile) world.getBlockEntity(b)).getSource() >= mana);
        if(!loc.isPresent())
            return null;
        SourceJarTile tile = (SourceJarTile) world.getBlockEntity(loc.get());
        tile.removeSource(mana);
        return loc.get();
    }

    public static @Nullable BlockPos takeManaNearbyWithParticles(BlockPos pos, Level world, int range, int mana){
        BlockPos result = takeManaNearby(pos,world,range,mana);
        if(result != null){
            EntityFollowProjectile aoeProjectile = new EntityFollowProjectile(world, result, pos);
            world.addFreshEntity(aoeProjectile);
        }
        return result;
    }

    /**
     * Searches for nearby mana jars that have enough mana.
     * Returns the position where the mana was taken, or null if none were found.
     */
    public static boolean hasManaNearby(BlockPos pos, Level world, int range, int mana){
        Optional<BlockPos> loc = BlockPos.findClosestMatch(pos, range, range, (b) -> world.getBlockEntity(b) instanceof SourceJarTile && ((SourceJarTile) world.getBlockEntity(b)).getSource() >= mana);
        return loc.isPresent();
    }

    @Nullable
    public static BlockPos canGiveManaClosest(BlockPos pos, Level world, int range){
        Optional<BlockPos> loc = BlockPos.findClosestMatch(pos, range, range, (b) ->  world.getBlockEntity(b) instanceof SourceJarTile && ((SourceJarTile) world.getBlockEntity(b)).canAcceptSource());
        return loc.orElse(null);
    }

    public static List<BlockPos> canGiveManaAny(BlockPos pos, Level world, int range){
        List<BlockPos> posList = new ArrayList<>();
        BlockPos.withinManhattanStream(pos, range, range, range).forEach(b ->{
            if(world.getBlockEntity(b) instanceof SourceJarTile && ((SourceJarTile) world.getBlockEntity(b)).canAcceptSource())
                posList.add(b.immutable());
        });
        return posList;
    }
}
