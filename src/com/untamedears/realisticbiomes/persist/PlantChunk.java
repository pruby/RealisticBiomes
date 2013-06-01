package com.untamedears.realisticbiomes.persist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.untamedears.realisticbiomes.GrowthConfig;
import com.untamedears.realisticbiomes.RealisticBiomes;

public class PlantChunk {
	RealisticBiomes plugin;
	
	HashMap<Coords,Plant> plants;
	
	// index of this chunk in the database
	int index;
	
	boolean loaded;
	boolean inDatabase;
	
	private static PreparedStatement loadPlantsStmt = null;
	
	public PlantChunk(RealisticBiomes plugin, Connection readConn, int index) {
		this.plugin = plugin;
		plants = null;
		this.index = index;
		
		this.loaded = false;
		this.inDatabase = false;
		try {
			
			loadPlantsStmt = readConn.prepareStatement("SELECT w, x, y, z, date, growth FROM plant WHERE chunkid = ?1");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	///-------------------------
	
	public boolean isLoaded() {
		return loaded;
	}
	
	public int getPlantCount() {
		return plants.keySet().size();
	}
	
	///-------------------------
	
	public void remove(Coords coords) {
		if (!loaded)
			return;
		
		plants.remove(coords);
	}
	
	public void add(Coords coords, Plant plant) {
		if (!loaded) {
			plants = new HashMap<Coords, Plant>();
			loaded = true;
		}
		
		plants.put(coords, plant);
	}
	
	public Plant get(Coords coords) {
		if (!loaded)
			return null;
		
		return plants.get(coords);
	}

	public boolean load(Coords coords) {
		// if the data is being loaded, it is known that this chunk is in the database
		inDatabase = true;
		
		if (loaded)
			return true;
		
		World world = plugin.getServer().getWorld(WorldID.getMCID(coords.w));
		
		plants = new HashMap<Coords, Plant>();
		
		try {
			loadPlantsStmt.setInt(1, index);
			loadPlantsStmt.execute();
			ResultSet rs = loadPlantsStmt.getResultSet();
			while (rs.next()) {
				int w = rs.getInt(1);
				int x = rs.getInt(2);
				int y = rs.getInt(3);
				int z = rs.getInt(4);
				long date = rs.getLong(5);
				float growth = rs.getFloat(6);
				
				// if the plant does not correspond to an actual crop, don't load it
				if (!plugin.getGrowthConfigs().containsKey(world.getBlockAt(x, y, z).getType())) {
					continue;
				}
				
				Plant plant = new Plant(date,growth);
					
				// grow the block
				Block block = world.getBlockAt(x, y, z);
				GrowthConfig growthConfig = plugin.getGrowthConfigs().get(block.getType());
				double growthAmount = growthConfig.getRate(block) * plant.setUpdateTime(System.currentTimeMillis());
				plant.addGrowth((float)growthAmount);
				
				// and update the plant growth
				plugin.getBlockGrower().growBlock(block,coords,plant.getGrowth());
				
				// if the plant isn't finished growing, add it to the 
				// plants
				if (!(plant.getGrowth() >= 1.0)) {
					plants.put(new Coords(w,x,y,z), plant);
				}
			} 			
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		
		loaded = true;
		return true;
	}
	
	public void unload(Coords chunkCoords, ChunkWriter writer) {
		if (!loaded)
			return;
		
		try {
			// if this chunk was not in the database, then add it to the database
			if (!inDatabase) {
				writer.addChunkStmt.setInt(1, chunkCoords.w);
				writer.addChunkStmt.setInt(2, chunkCoords.x);
				writer.addChunkStmt.setInt(3, chunkCoords.z);
				writer.addChunkStmt.execute();
				writer.getLastChunkIdStmt.execute();
				ResultSet rs = writer.getLastChunkIdStmt.getResultSet();
				index = rs.getInt(1);
				
				inDatabase = true;
			}
			
			// first, delete the old data
			writer.deleteOldDataStmt.setInt(1, index);
			writer.deleteOldDataStmt.execute();
			
			// then replace it with all the recorded plants in this chunk
			if (!plants.isEmpty()) {
				for (Coords coords: plants.keySet()) {
					Plant plant = plants.get(coords);
					
					writer.savePlantsStmt.setInt(1, index);
					writer.savePlantsStmt.setInt(2, coords.w);
					writer.savePlantsStmt.setInt(3, coords.x);
					writer.savePlantsStmt.setInt(4, coords.y);
					writer.savePlantsStmt.setInt(5, coords.z);
					writer.savePlantsStmt.setLong(6, plant.getUpdateTime());
					writer.savePlantsStmt.setFloat(7, plant.getGrowth());
					
					writer.savePlantsStmt.execute();
				}
			}
			else {
				// otherwise just delete the chunk entirely
				writer.deleteChunkStmt.setInt(1, index);
				writer.deleteChunkStmt.execute();
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		
		plants = null;
		loaded = false;
	}
}
