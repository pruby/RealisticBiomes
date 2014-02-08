package com.untamedears.realisticbiomes;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.untamedears.realisticbiomes.listener.GrowListener;
import com.untamedears.realisticbiomes.listener.PlantListener;
import com.untamedears.realisticbiomes.listener.PlayerListener;
import com.untamedears.realisticbiomes.listener.SpawnListener;
import com.untamedears.realisticbiomes.persist.BlockGrower;
import com.untamedears.realisticbiomes.persist.Coords;
import com.untamedears.realisticbiomes.persist.Plant;
import com.untamedears.realisticbiomes.persist.PlantManager;
import com.untamedears.realisticbiomes.persist.WorldID;

public class RealisticBiomes extends JavaPlugin implements Listener {

	public static Logger LOG = null;
	public static Level minLogLevel = Level.INFO;
	
	public HashMap<Object, GrowthConfig> materialGrowth;
	public BlockGrower blockGrower;
	public PersistConfig persistConfig;
	private PlantManager plantManager;
	private HashMap<Material, PlantingConfig> plantingConfigs;
	
	public void onEnable() {		
		
		
		RealisticBiomes.LOG = this.getLogger();
		LOG.info("name of logger is: " + LOG.getName());
		this.getLogger().setLevel(Level.FINEST);
		
		WorldID.init(this);
		
		// perform check for config file, if it doesn't exist, then create it using the default config file
		if (!this.getConfig().isSet("realistic_biomes")) {
			this.saveDefaultConfig();
			this.getLogger().warning("Config did not exist or was invalid, default config saved.");
		}
		this.reloadConfig();
		
		ConfigurationSection config = this.getConfig().getConfigurationSection("realistic_biomes");
		
		loadPersistConfig(config);
		loadGrowthConfigs(config);
		loadPlantingConfigs(config.getConfigurationSection("planting"));
		
		// load the max log level for our logging hack
		// if not defined then its just initalized at INFO
		String tmp = config.getString("minLogLevel");
		if (tmp != null) {
			Level newLevel = Level.INFO;
			try {
				newLevel = Level.parse(tmp);
			} catch (Exception e) {
				
				// passed in config value is invalid..just set it to info
				newLevel = Level.INFO;
			}
			minLogLevel = newLevel;
		}
		
		RealisticBiomes.LOG.info("Logging hack, log level is set to: " + RealisticBiomes.minLogLevel.toString());
		
		registerEvents();
		
		if (persistConfig.enabled) {
			plantManager = new PlantManager(this, persistConfig);
			blockGrower = new BlockGrower(plantManager);
		}
		
		getServer().getPluginManager().registerEvents(this, this);
		
		LOG.info("is now enabled.");
	}
	
	/**
	 * Hack to get around the shitty java logging api, where -Djava.util.logging.config=logging.properties
	 * doesn't seem to work, or i'm not specifying the right levels for the right Logger namespaces or some 
	 * stuff. You specify the min logging level allowed in the realistic biomes config.yml, with the 'minLogLevel' 
	 * key, specifed to a string. Either a Level name like INFO, or an integer
	 * 
	 * This requires that the static Logger variable in RealisticBiomes has already been created (aka onEnable has been called)
	 * @param level
	 * @param message
	 */
	public static void doLog(Level level, String message) {
		
		
		if (RealisticBiomes.LOG != null) {
			
			// here we make sure that we only log messages that are loggable with the given Level
			// so if its set to INFO (800) and we try to log a FINER message (400), then it wont work
			// However if its ALL, then its set to Integer.MIN_VALUE, so everything will get logged. etc etc
			if (level.intValue() >= RealisticBiomes.minLogLevel.intValue() ) {
				RealisticBiomes.LOG.info("[" + level.toString() + "] " + message);
				
			}	
		}
	}

	private void loadPersistConfig(ConfigurationSection config) {
		persistConfig = new PersistConfig();
		
		persistConfig.databaseName = config.getString("database_name");
		persistConfig.host = config.getString("database_host");
		persistConfig.port = config.getString("database_port");
		persistConfig.user = config.getString("database_user");
		persistConfig.password = config.getString("database_password");
		persistConfig.prefix = config.getString("database_prefix");
		
		persistConfig.enabled = config.getBoolean("persistence_enabled");
		persistConfig.unloadBatchPeriod = config.getInt("unload_batch_period");
		persistConfig.unloadBatchMaxTime = config.getInt("unload_batch_max_time");
		persistConfig.growEventLoadChance = config.getDouble("grow_event_load_chance");
		persistConfig.logDB = config.getBoolean("log_db", false);
		
		persistConfig.productionLogDb = config.getBoolean("log_db_production", false);
		persistConfig.productionLogLoadMintime = config.getLong("log_db_production_chunk_load_mintime", 5);
		persistConfig.productionLogUnloadMintime = config.getLong("log_db_production_chunk_unload_mintime", 5);
		
		LOG.info("Persistence enabled: " + persistConfig.enabled);
		LOG.info("Database: " + persistConfig.databaseName);
		if (persistConfig.productionLogDb || persistConfig.logDB) {
			LOG.info("Logging enabled");
			if (persistConfig.productionLogDb) {
				LOG.info("\tLoad event minimum time to log: " + persistConfig.productionLogLoadMintime + "ms");
				LOG.info("\tUnload event minimum time to log: " + persistConfig.productionLogUnloadMintime + "ms");
			}
		}
	}
	
	private void loadGrowthConfigs(ConfigurationSection config) {
		
		// load names that map to lists of biomes to be used as shorthand for those biomes
		ConfigurationSection biomeAliasSection = config.getConfigurationSection("biome_aliases");
		HashMap<String, List<Biome>> biomeAliases = new HashMap<String, List<Biome>>();
		for (String alias : biomeAliasSection.getKeys(false)) {
			// convert list of strings into list of biomes
			List<String> biomeStrs = biomeAliasSection.getStringList(alias);
			List<Biome> biomes = new ArrayList<Biome>();
			for (String biomeStr : biomeStrs) {
				try {
					Biome biome = Biome.valueOf(biomeStr);
					biomes.add(biome);
				}
				catch(IllegalArgumentException e) {
					LOG.warning("loading configs: in biome_aliases: \"" + biomeStr +"\" is not a valid biome name.");
				}
			}
			
			// map those biomes to an alias
			biomeAliases.put(alias, biomes);
		}
		
		GrowthConfig defaultConfig = new GrowthConfig();
		
		materialGrowth = new HashMap<Object, GrowthConfig>();
		HashMap<String, GrowthConfig> growthConfigNodes = new HashMap<String, GrowthConfig>();
		
		ConfigurationSection growthConfigSection = config.getConfigurationSection("growth");
		for (String materialName : growthConfigSection.getKeys(false)) {
			ConfigurationSection configSection = growthConfigSection.getConfigurationSection(materialName);
			
			GrowthConfig inheritConfig = defaultConfig;
			
			if (configSection.isSet("inherit")) {
				String inheritStr = configSection.getString("inherit");
				
				if (growthConfigNodes.containsKey(inheritStr)) {
					inheritConfig = growthConfigNodes.get(inheritStr);
				}
				else {
					Object inheritKey = getMaterialKey(inheritStr);
					if (materialGrowth.containsKey(inheritKey)) {
						inheritConfig = materialGrowth.get(inheritKey);
					}
				}
			}
			
			GrowthConfig newGrowthConfig = new GrowthConfig(inheritConfig, configSection, biomeAliases);
			
			Object key = getMaterialKey(materialName);	
			if (key == null) {
				// if the name is partially capitalized, then warning the player that
				// the name might be a misspelling
				if (materialName.length() > 0 && materialName.matches(".*[A-Z].*"))
					LOG.warning("config material name: is \""+materialName+"\" misspelled?");
				growthConfigNodes.put(materialName, newGrowthConfig);
			}
			else {
				materialGrowth.put(key, newGrowthConfig);
			}
		}
	}
	
	private void loadPlantingConfigs(ConfigurationSection config) {
		plantingConfigs = new HashMap<Material, PlantingConfig>();
		HashMap<String, PlantingConfig> prlantingConfigNodes = new HashMap<String, PlantingConfig>();
		
		ConfigurationSection growthConfigSection = config.getConfigurationSection("growth");
		for (String materialName : growthConfigSection.getKeys(false)) {
			ConfigurationSection configSection = growthConfigSection.getConfigurationSection(materialName);
			
			PlantingConfig newConfig = new PlantingConfig(Material.valueOf(materialName), configSection);
			
			plantingConfigs.put(newConfig.getSeedType(), newConfig);
		}
	}
	
	private Object getMaterialKey(String materialName) {
		boolean isMat = false, isTree = false, isEntity = false;
		// test to see if the material has a "type" specifier, record the specifier and remover it
		if (materialName.startsWith("mat_")) {
			materialName = materialName.replaceFirst("mat\\_", "");
			isMat = true;
		}
		else if (materialName.startsWith("tree_")) {
			materialName = materialName.replaceFirst("tree\\_", "");
			isTree = true;
		}
		else if (materialName.startsWith("entity_")) {
			materialName = materialName.replaceFirst("entity\\_", "");
			isEntity = true;
		}
		
		// match name to bukkit objects
		TreeType treeType;
		try { treeType = TreeType.valueOf(materialName); }
		catch (IllegalArgumentException e) { treeType = null; }
		
		Material material = Material.getMaterial(materialName);
		EntityType entityType;

		try { entityType = EntityType.valueOf(materialName); }
		catch (IllegalArgumentException e) { entityType = null; }
		
		// if the type was specifically specified, thenregister only for that type of object
		// warn if that object doesn't actully match anything
		if (isMat) {
			if (material != null)
				return material;
			LOG.warning("config: \""+materialName+"\" specified material but does not match one.");
		}
		if (isTree) {
			if (treeType != null)
				return treeType;
			LOG.warning("config: \""+materialName+"\" specified tree type name but does not match one.");
		}
		if (isEntity) {
			if (entityType != null)
				return entityType;
			LOG.warning("config: \""+materialName+"\" specified entity type name but does not match one.");
		}
		
		// wanr user if they are unsing an ambiguous name
		if (material != null  && entityType != null && treeType != null)
			LOG.warning("config name: \""+materialName+"\" ambiguous, could be material, tree type, or entity type.");
		if (treeType != null  && material != null)
			LOG.warning("config name: \""+materialName+"\" ambiguous, could be material or tree type.");
		if (material != null  && entityType != null)
			LOG.warning("config name: \""+materialName+"\" ambiguous, could be material or entity type.");
		if (treeType != null  && entityType != null)
			LOG.warning("config name: \""+materialName+"\" ambiguous, could be tree type or entity type.");
		
		// finally just match any type
		if (material != null)
			return material;
		if (treeType != null)
			return treeType;
		if (entityType != null)
			return entityType;

		return null;
	}
	
	public void onDisable() {
		if (persistConfig.enabled) {
			LOG.info("saving plant growth data.");
			plantManager.saveAllAndStop();
			plantManager = null;
		}
		LOG.info("is now disabled.");
	}

	private void registerEvents() {
		try {
            PluginManager pm = getServer().getPluginManager();
            pm.registerEvents(new GrowListener(this, materialGrowth), this);
            pm.registerEvents(new PlantListener(this, plantingConfigs), this);
            pm.registerEvents(new SpawnListener(materialGrowth), this);
            pm.registerEvents(new PlayerListener(this, materialGrowth), this);
        }
        catch(Exception e)
        {
        	LOG.severe("caught an exception while attempting to register events with the PluginManager: " + e);
        }
	}

	public HashMap<Object, GrowthConfig> getGrowthConfigs() {
		return materialGrowth;
	}
	
	public BlockGrower getBlockGrower() {
		return blockGrower;
	}
	
	// -----------------------------------
	
	// grow the specified block, return the new growth magnitude
	// gets called when the user hits a block manually!!
	public double growAndPersistBlock(Block block, GrowthConfig growthConfig, boolean naturalGrowEvent) {
		
		RealisticBiomes.doLog(Level.FINER, "RealisticBiomes:growAndPersistBlock() called for block: " + block + " and is naturalGrowEvent? " + naturalGrowEvent);
		if (!persistConfig.enabled)
			return 0.0;
		
		int w = WorldID.getPID(block.getWorld().getUID());
		Coords coords = new Coords(w, block.getX(), block.getY(), block.getZ());
		boolean loadChunk = naturalGrowEvent ? Math.random() < persistConfig.growEventLoadChance : true;
		if (!loadChunk && !plantManager.chunkLoaded(coords)) {
			RealisticBiomes.doLog(Level.FINER, "Realisticbiomes.growAndPersistBlock(): returning 0.0 because loadChunk = false or plantManager.chunkLoaded(" + coords + " is false");
			return 0.0; // don't load the chunk or do anything
			
		}
			
		Plant plant = plantManager.get(coords);
		
		RealisticBiomes.doLog(Level.FINER, "Realisticbiomes.growAndPersistBlock(): plantManager.get() returned: " + plant + " for coords: " + coords);
		
		if (plant == null) {
			RealisticBiomes.doLog(Level.FINER, "Realisticbiomes.growAndPersistBlock(): creating new plant and adding it");
			
			// divide by 1000 to get unix/epoch time, we don't need millisecond precision
			// also fixes bug where the timestamp would be too big for the mysql rb_plant date column
			plant = new Plant(System.currentTimeMillis()  / 1000L);
			plant.addGrowth((float)BlockGrower.getGrowthFraction(block));
			plantManager.add(coords, plant);
			
		}
		else {
			double growthAmount = growthConfig.getRate(block) * plant.setUpdateTime(System.currentTimeMillis() / 1000L);
			RealisticBiomes.doLog(Level.FINER, "Realisticbiomes.growAndPersistBlock(): plant existed, growthAmount was: " + plant.getGrowth());
			plant.addGrowth((float) growthAmount);
			RealisticBiomes.doLog(Level.FINER, "Realisticbiomes.growAndPersistBlock(): plant existed, adding growth: " + growthAmount + " to now be " + plant.getGrowth());

		}
		
		// actually 'grows' the block (in minecraft terms, between the different stages of growth that you can see in game)
		// depending on its growth value
		blockGrower.growBlock(block,coords,plant.getGrowth());
		if (plant.getGrowth() >= 1.0)
			plantManager.remove(coords);
		
		return plant.getGrowth();
	}
	
	public PlantManager getPlantManager() {
		return plantManager;
	}
}
