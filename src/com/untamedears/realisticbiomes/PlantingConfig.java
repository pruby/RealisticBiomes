package com.untamedears.realisticbiomes;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public class PlantingConfig {
		private Material seedType;
		private Material plantType;
		private Material soilType;
		private int attemptsPerSeed;
		private int maxFlightTime;
		private double gravity;
		private double airDrag;
		private double randomisation;
		
		public Material getSeedType() {
			return seedType;
		}

		public Material getPlantType() {
			return plantType;
		}

		public int getAttemptsPerSeed() {
			return attemptsPerSeed;
		}

		public int getMaxFlightTime() {
			return maxFlightTime;
		}

		public double getGravity() {
			return gravity;
		}

		public double getAirDrag() {
			return airDrag;
		}

		public double getRandomisation() {
			return randomisation;
		}

		public Material getSoilType() {
			return soilType;
		}

		public PlantingConfig(Material seedType,
				ConfigurationSection plantConfig) {
			this.seedType = seedType;
			this.plantType = Material.valueOf(plantConfig.getString("plantType"));
			this.soilType = Material.valueOf(plantConfig.getString("soilType", "FARMLAND"));
			this.attemptsPerSeed = plantConfig.getInt("attemptsPerSeed", 1);
			this.maxFlightTime = plantConfig.getInt("maxFlightTime", 100);
			this.randomisation = plantConfig.getDouble("randomisation", 1.0);
			this.airDrag = plantConfig.getDouble("airDrag", 0.05);
			this.gravity = plantConfig.getDouble("gravity", 0.05);
		}
		
	}
