package com.untamedears.realisticbiomes.persist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

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
	
	private static PreparedStatement deleteOldDataStmt = null;
	private static PreparedStatement loadPlantsStmt = null;
	private static PreparedStatement deleteChunkStmt = null;
	private static PreparedStatement addChunkStmt = null;
	private static PreparedStatement savePlantsStmt = null;
	private static PreparedStatement getLastChunkIdStmt = null;
	
	public PlantChunk(RealisticBiomes plugin, Connection readConn, Connection writeConn, int index) {
		this.plugin = plugin;
		plants = null;
		this.index = index;
		
		this.loaded = false;
		this.inDatabase = false;

		if (deleteOldDataStmt == null) {
			try {
			deleteOldDataStmt = writeConn.prepareStatement("DELETE FROM plant_data WHERE chunkid = ?1");
			
			loadPlantsStmt = readConn.prepareStatement("SELECT data FROM plant_data WHERE chunkid = ?1");
			
			addChunkStmt = writeConn.prepareStatement("INSERT INTO chunk (w, x, z) VALUES (?1, ?2, ?3)");
			getLastChunkIdStmt = writeConn.prepareStatement("SELECT last_insert_rowid()");	
			
			savePlantsStmt = writeConn.prepareStatement("INSERT INTO plant_data (chunkid, data) VALUES (?1, ?2)");
			
			deleteChunkStmt = writeConn.prepareStatement("DELETE FROM chunk WHERE id = ?1");
			} catch (SQLException e) {
				e.printStackTrace();
			}
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
	
	public byte[] chunkPlantData() {
		ByteArrayOutputStream builder = new ByteArrayOutputStream();
		try {
			ObjectOutputStream writer = new ObjectOutputStream(builder);
			writer.writeUTF("PD");
			writer.writeShort(1);
			
			for (Map.Entry<Coords, Plant> pair : plants.entrySet()) {
				Coords coords = pair.getKey();
				Plant plant = pair.getValue();
				writer.writeInt(coords.w);
				writer.writeInt(coords.x);
				writer.writeInt(coords.y);
				writer.writeInt(coords.z);
				writer.writeLong(plant.getUpdateTime());
				writer.writeFloat(plant.getGrowth());
			}
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] data = builder.toByteArray(); 
		return data;
	}
	
	public void loadPlantData(byte[] data) {
		ByteArrayInputStream dataReader = new ByteArrayInputStream(data);
		try {
			ObjectInputStream reader = new ObjectInputStream(dataReader);
			
			String magic = reader.readUTF();
			if (!magic.equals("PD")) {
				// Missing plant data marker
				System.out.println(String.format("Error loading chunk %d - plant data missing marker", index));
				return;
			}
			short version = reader.readShort();
			if (version > 1 || version < 1) {
				// Plant data version wrong
				System.out.println(String.format("Error loading chunk %d - plant data version mismatch (%d > 1)", index, version));
				return;
			}
			
			
			while (reader.available() > 0) {
				int w = reader.readInt();
				int x = reader.readInt();
				int y = reader.readInt();
				int z = reader.readInt();
				long updateTime = reader.readLong();
				float growth = reader.readFloat();

				Coords coords = new Coords(w, x, y, z);
				Plant plant = new Plant(updateTime, growth);
				
				World world = plugin.getServer().getWorld(WorldID.getMCID(coords.w));

				// if the plant does not correspond to an actual crop, don't load it
				if (!plugin.getGrowthConfigs().containsKey(world.getBlockAt(x, y, z).getType())) {
					continue;
				}
				
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
				byte[] plantData = rs.getBytes(1);
				loadPlantData(plantData);
			} 			
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		
		loaded = true;
		return true;
	}
	
	public void unload(Coords chunkCoords) {
		if (!loaded)
			return;
		
		try {
			// if this chunk was not in the database, then add it to the database
			if (!inDatabase) {
				addChunkStmt.setInt(1, chunkCoords.w);
				addChunkStmt.setInt(2, chunkCoords.x);
				addChunkStmt.setInt(3, chunkCoords.z);
				addChunkStmt.execute();
				getLastChunkIdStmt.execute();
				ResultSet rs = getLastChunkIdStmt.getResultSet();
				index = rs.getInt(1);
				
				inDatabase = true;
			}
			
			// first, delete the old data
			deleteOldDataStmt.setInt(1, index);
			deleteOldDataStmt.execute();
			
			// then replace it with all the recorded plants in this chunk
			if (!plants.isEmpty()) {
				byte[] plantData = chunkPlantData();
				savePlantsStmt.setInt(1, index);
				savePlantsStmt.setBytes(2, plantData);
				savePlantsStmt.execute();
			}
			else {
				// otherwise just delete the chunk entirely
				deleteChunkStmt.setInt(1, index);
				deleteChunkStmt.execute();
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		
		plants = null;
		loaded = false;
	}
}
