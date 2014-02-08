package com.untamedears.realisticbiomes.listener;

import java.util.Map;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Dispenser;
import org.bukkit.util.Vector;

import com.untamedears.realisticbiomes.GrowthConfig;
import com.untamedears.realisticbiomes.PlantingConfig;
import com.untamedears.realisticbiomes.RealisticBiomes;

public class PlantListener implements Listener {
	private RealisticBiomes plugin;
	private Map<Material, PlantingConfig> plantConfig;

	public PlantListener(RealisticBiomes plugin, Map<Material, PlantingConfig> plantConfig) {
		this.plugin = plugin;
		this.plantConfig =  plantConfig;
	}
	
	@EventHandler(ignoreCancelled = true)
	public void handleDispense(BlockDispenseEvent ev) {
		if (ev.getBlock().getType().equals(Material.DISPENSER)) {
			Dispenser dispenser = (Dispenser) ev.getBlock().getState().getData();

			ItemStack item = ev.getItem();
			if (plantConfig.containsKey(item.getType())) {
				PlantingConfig config = plantConfig.get(item.getType());

				plantTrajectory(ev.getBlock(), dispenser, config);
			}
		}
	}

	private void plantTrajectory(Block dispenserBlock, Dispenser dispenser, PlantingConfig config) {
		Material plantType = config.getPlantType();
		int maxFlightTime = config.getMaxFlightTime();
		int attemptsPerSeed = config.getAttemptsPerSeed();

		double randomAmount = config.getRandomisation();
		double gravity = config.getGravity();
		double airDrag = config.getAirDrag();

		BlockFace facing = dispenser.getFacing();

		World world = dispenserBlock.getWorld();
		Vector gravityVector = new Vector(0, 0 - gravity, 0);
		Random random = new Random();

		for (int i = 0; i < attemptsPerSeed; ++i) {
			Vector velocity = new Vector(facing.getModX(), facing.getModY(), facing.getModZ());
			Vector position = dispenserBlock.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5)).add(velocity.multiply(0.5));

			// Add random component in non-facing axes
			double randomX = (random.nextFloat() * randomAmount * 2 - randomAmount) * (1.0 - Math.abs(velocity.getX()));
			double randomY = (random.nextFloat() * randomAmount * 2 - randomAmount) * (1.0 - Math.abs(velocity.getY()));
			double randomZ = (random.nextFloat() * randomAmount * 2 - randomAmount) * (1.0 - Math.abs(velocity.getZ()));
			velocity = velocity.add(new Vector(randomX, randomY, randomZ));

			for (int j = 0; j < maxFlightTime; ++j) {
				position = position.add(velocity);
				velocity = velocity.multiply(1.0 - airDrag).add(gravityVector);
				if (position.getY() < 1 || position.getY() > 255) {
					break;
				}
				Block blockHere = position.toLocation(world).getBlock();
				if (blockHere.isLiquid()) {
					// Landing in water or lava - fail
					break;
				} else if (blockHere.getType().isSolid()) {
					tryPlantAtPosition(world, position, config);
					break;
				}
			}
		}
	}

	private void tryPlantAtPosition(World world, Vector position, PlantingConfig config) {
		Block blockAtPosition = position.toLocation(world).getBlock();
		Block blockAbove = position.add(new Vector(0, 1, 0)).toLocation(world).getBlock();
		if (blockAtPosition.getType() == config.getSoilType() && (blockAbove == null || blockAbove.getTypeId() == 0)) {
			blockAbove.setType(config.getPlantType());
			
			GrowthConfig growthConfig = plugin.getGrowthConfigs().get(blockAbove.getType());
			if (plugin.persistConfig.enabled && growthConfig != null && growthConfig.isPersistent()) {
				plugin.growAndPersistBlock(blockAbove, growthConfig, false);
			}
		}
	}
}
