package xyz.marroq.flagwarconnecteds;

import co.aikar.commands.PaperCommandManager;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.DeleteNationEvent;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.object.*;
import dev.glowie.townySpacePorts.utils.SpaceUtils;
import io.github.townyadvanced.flagwar.events.CellAttackEvent;
import io.github.townyadvanced.flagwar.events.CellWonEvent;
import io.github.townyadvanced.flagwar.objects.CellUnderAttack;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class FlagwarConnecteds extends JavaPlugin implements Listener {

    public static FlagwarConnecteds instance;
    public static boolean enabled;
    public static boolean captureHomeblockTransfersConnectedChunks;
    public static boolean captureOutpostTransfersConnectedChunks;
    public static boolean captureHomeblockTransfersAllChunks;
    public static boolean captureCapitalHomeblockTransfersOtherTowns;
    public static boolean captureCapitalHomeblockDisbandsNation;
    public static TownyAPI townyAPI;
    private static Map<TownBlock, Collection<Position>> outposts;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadCfg();

        PaperCommandManager manager = new PaperCommandManager(instance);
        manager.registerCommand(new FlagwarConnectedsCommand());

        Bukkit.getPluginManager().registerEvents(instance, instance);

        townyAPI = TownyAPI.getInstance();
        outposts = new HashMap<>();

        getLogger().info("Enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled!");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadCfg();
    }

    public void loadCfg() {
        FileConfiguration cfg = getConfig();
        enabled = cfg.getBoolean("enabled", true);
        captureHomeblockTransfersConnectedChunks = cfg.getBoolean("capture_homeblock_transfers_connected_chunks", true);
        captureOutpostTransfersConnectedChunks = cfg.getBoolean("capture_outpost_transfers_connected_chunks", true);
        captureHomeblockTransfersAllChunks = cfg.getBoolean("capture_homeblock_transfers_all_chunks", false);
        captureCapitalHomeblockTransfersOtherTowns = cfg.getBoolean("capture_capital_homeblock_transfers_other_towns", false);
        captureCapitalHomeblockDisbandsNation = cfg.getBoolean("capture_capital_homeblock_disbands_nation", false);
    }

    // https://github.com/Gl0W1E/FlagWarUtilities/blob/main/src/main/java/dev/glowie/flagWarUtilities/utils/ChunkUtils.java#L62
    private void addChunksToList(TownBlock initialTB, List<TownBlock> tbList) {
        if (initialTB == null) return;

        initialTB.getWorldCoord().getCardinallyAdjacentWorldCoords(false).stream().filter((wc) -> wc.hasTownBlock() && !tbList.contains(wc.getTownBlockOrNull()) && wc.getTownOrNull() == initialTB.getTownOrNull()).forEach(wc -> {
            tbList.add(wc.getTownBlockOrNull());
            addChunksToList(wc.getTownBlockOrNull(), tbList);
        });
    }

    private void transferChunks(Town toTransfer, Town transferTo, TownBlock centerBlock, boolean isHomeblock, CellWonEvent event) {
        event.setCancelled(true);
        if (captureHomeblockTransfersAllChunks && centerBlock != null && isHomeblock) {
            toTransfer.getTownBlocks().forEach((tb) -> {
                tb.setTown(transferTo);
                tb.save();
            });
            centerBlock.setOutpost(true);
            centerBlock.save();
            if (outposts.containsKey(centerBlock)) for (Position position : outposts.get(centerBlock)) {
                if (position == null) continue;
                transferTo.addOutpostSpawn(position);
                transferTo.save();
            }
            if (toTransfer.spawnPosition() != null)
                transferTo.addOutpostSpawn(toTransfer.spawnPosition());
            transferTo.save();
            outposts.remove(centerBlock);
            return;
        }

        if (centerBlock == null) {
            centerBlock = toTransfer.getHomeBlockOrNull();
            if (centerBlock == null && toTransfer.hasOutpostSpawn())
                centerBlock = townyAPI.getTownBlock(toTransfer.getAllOutpostSpawns().getFirst());
        }

        if (centerBlock == null) return;

        List<TownBlock> tbList = new ArrayList<>();
        addChunksToList(centerBlock, tbList);
        tbList.forEach((tb) -> {
            tb.setTown(transferTo);
            tb.save();
            if(Bukkit.getPluginManager().isPluginEnabled("TownySpacePorts"))
                SpaceUtils.transferOtherPorts(tb, transferTo);
        });

        centerBlock.setTown(transferTo);
        centerBlock.setOutpost(true);
        centerBlock.save();
        if (outposts.containsKey(centerBlock)) for (Position position : outposts.get(centerBlock))
            if (position != null && position.worldCoord().equals(centerBlock.getWorldCoord())) {
                transferTo.addOutpostSpawn(position);
                transferTo.save();
                return;
            }
        outposts.remove(centerBlock);
    }

    @EventHandler
    public void onCellAttack(CellAttackEvent event) {
        if (!enabled) return;
        TownBlock townBlock = townyAPI.getTownBlock(event.getFlagBlock().getLocation());
        if (townBlock == null || (!townBlock.isOutpost() && !townBlock.isHomeBlock()) || townBlock.getTownOrNull() == null)
            return;
        outposts.put(townBlock, new ArrayList<>(townBlock.getTownOrNull().getOutpostSpawns()));
    }

    @EventHandler
    public void onCellWon(CellWonEvent event) {
        if (!enabled || event.isCancelled()) return;

        CellUnderAttack cellUnderAttack = event.getCellUnderAttack();

        Resident resident = townyAPI.getResident(cellUnderAttack.getNameOfFlagOwner());

        if (resident == null) return;

        Town attackingTown = resident.getTownOrNull();

        if (attackingTown == null) return;

        TownBlock cellTownBlock = townyAPI.getTownBlock(cellUnderAttack.getFlagBaseBlock().getLocation());
        if (cellTownBlock == null) return;

        Town defendingTown = cellTownBlock.getTownOrNull();
        if (defendingTown == null) return;

        Nation attackingNation = attackingTown.getNationOrNull();
        if (attackingNation == null) return;


        boolean isHomeblock = false;

        // we can't use cellTownBlock.isHomeBlock() bc erm we just cant ok it always returns false even if its true but it works fine in CellAttackEvent idk man
        if (defendingTown.getSpawnOrNull() != null && townyAPI.getTownBlock(defendingTown.getSpawnOrNull()) != null)
            isHomeblock = townyAPI.getTownBlock(defendingTown.getSpawnOrNull()).equals(cellTownBlock);

        if ((captureHomeblockTransfersConnectedChunks && cellTownBlock.isHomeBlock()) || (captureOutpostTransfersConnectedChunks && cellTownBlock.isOutpost()))
            transferChunks(defendingTown, attackingTown, cellTownBlock, isHomeblock, event);
        if ((captureCapitalHomeblockTransfersOtherTowns || captureCapitalHomeblockDisbandsNation) && isHomeblock && defendingTown.hasNation() && defendingTown.isCapital() && defendingTown.getNationOrNull() != null) {
            Nation defendingNation = defendingTown.getNationOrNull();
            if (captureCapitalHomeblockTransfersOtherTowns)
                for (Town town : defendingTown.getNationOrNull().getTowns().stream().filter((town) -> town.getUUID() != defendingTown.getUUID()).toList())
                    try {
                        town.setNation(null);
                        if (captureCapitalHomeblockTransfersOtherTowns) {
                            town.setNation(attackingNation);
                        }
                        town.save();
                        attackingNation.save();
                    } catch (AlreadyRegisteredException e) {
                        throw new RuntimeException(e);
                    }
            townyAPI.getDataSource().removeNation(defendingNation, DeleteNationEvent.Cause.ADMIN_COMMAND, Bukkit.getServer().getConsoleSender());
        }

    }
}
