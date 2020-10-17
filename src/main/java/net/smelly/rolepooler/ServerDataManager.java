package net.smelly.rolepooler;

import com.google.gson.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Luke Tonon
 */
public final class ServerDataManager {
	private static final Gson GSON = new GsonBuilder().registerTypeAdapter(PoolRoleMap.class, new PoolRoleMap.Codec()).registerTypeAdapter(UserPoolMap.class, new UserPoolMap.Codec()).setPrettyPrinting().create();
	private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
	private final PoolRoleMap pooledRoleMap;
	private final UserPoolMap userPoolMap;
	private final Path rolePoolPath, userPoolPath;

	/**
	 * Initializes and loads the manager.
	 *
	 * @param dataFileLocation The path of the folder to store the data files in.
	 * @throws IOException If an exception occurs reading the data files.
	 */
	public ServerDataManager(String dataFileLocation) throws IOException {
		this.rolePoolPath = Paths.get(dataFileLocation + "/pooled_roles.json");
		this.userPoolPath = Paths.get(dataFileLocation + "/user_pools.json");
		this.pooledRoleMap = GSON.fromJson(new String(Files.readAllBytes(this.rolePoolPath)), PoolRoleMap.class);
		this.userPoolMap = GSON.fromJson(new String(Files.readAllBytes(this.userPoolPath)), UserPoolMap.class);
		System.out.println("Server Data Manager Loaded!");
	}

	public void writePooledRoles() {
		EXECUTOR_SERVICE.execute(() -> {
			try {
				synchronized (this.rolePoolPath) {
					Writer writer = Files.newBufferedWriter(this.rolePoolPath);
					GSON.toJson(this.pooledRoleMap, writer);
					writer.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	public void writeUserPools() {
		EXECUTOR_SERVICE.execute(() -> {
			try {
				synchronized (this.userPoolMap) {
					Writer writer = Files.newBufferedWriter(this.userPoolPath);
					GSON.toJson(this.userPoolMap, writer);
					writer.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	public boolean putRole(Pool pool, Role role) {
		return this.pooledRoleMap.putRole(pool, role);
	}

	public boolean removeRole(Pool pool, Role role) {
		return this.pooledRoleMap.removeRole(pool, role);
	}

	@Nullable
	public Pool getPoolForRole(Role role) {
		return this.pooledRoleMap.roleToPool.get(role);
	}

	public EnumMap<Pool, Set<Role>> getRoles() {
		EnumMap<Pool, Set<Role>> map = new EnumMap<>(Pool.class);
		SelfUser bot = RolePooler.BOT.getSelfUser();
		this.pooledRoleMap.forEach((key, guildRoleMap) -> {
			Set<Role> roles = new HashSet<>();
			guildRoleMap.forEach((guild, role) -> {
				if (guild.isMember(bot)) {
					roles.add(role);
				}
			});
			if (!roles.isEmpty()) {
				map.put(key, roles);
			}
		});
		return map;
	}

	public void checkAndUpdateRolesForUser(Guild guild, User user) {
		Set<Pool> pools = this.userPoolMap.get(user);
		if (pools != null) {
			for (Pool pool : pools) {
				Map<Guild, Role> guildRoleMap = this.pooledRoleMap.get(pool);
				if (guildRoleMap != null) {
					Role pooledRole = guildRoleMap.get(guild);
					if (pooledRole != null) {
						guild.addRoleToMember(user.getIdLong(), pooledRole).queue();
					}
				}
			}
		}
	}

	/**
	 * Gets all the {@link Role}s pooled to a {@link Pool} and adds them to a {@link User}.
	 */
	public void addPooledRolesToUser(Pool pool, User user) {
		AtomicBoolean added = new AtomicBoolean(false);
		this.pooledRoleMap.get(pool).forEach((guild, role) -> {
			if (guild.isMember(RolePooler.BOT.getSelfUser()) && guild.isMember(user)) {
				if (guild.getRoles().contains(role)) {
					guild.addRoleToMember(user.getIdLong(), role).queue();
					added.set(true);
				}
			}
		});
		if (added.get() && this.userPoolMap.addPoolToUser(user, pool)) {
			this.writeUserPools();
		}
	}

	/**
	 * Gets all the {@link Role}s pooled to a {@link Pool} and removes them from a {@link User}.
	 */
	public void removedPooledRolesFromUser(Pool pool, User user) {
		AtomicBoolean removed = new AtomicBoolean(false);
		this.pooledRoleMap.get(pool).forEach((guild, role) -> {
			if (guild.isMember(RolePooler.BOT.getSelfUser()) && guild.isMember(user)) {
				if (guild.getRoles().contains(role)) {
					guild.removeRoleFromMember(user.getIdLong(), role).queue();
					removed.set(true);
				}
			}
		});
		if (removed.get() && this.userPoolMap.removePoolFromUser(user, pool)) {
			this.writeUserPools();
		}
	}

	private static class PoolRoleMap extends ConcurrentHashMap<Pool, Map<Guild, Role>> {
		private final ConcurrentHashMap<Role, Pool> roleToPool = new ConcurrentHashMap<>();

		private boolean putRole(Pool pool, Role role) {
			if (this.computeIfAbsent(pool, key -> new HashMap<>()).putIfAbsent(role.getGuild(), role) != role) {
				this.roleToPool.put(role, pool);
				return true;
			}
			return false;
		}

		private boolean removeRole(Pool pool, Role role) {
			if (this.computeIfAbsent(pool, key -> new HashMap<>()).remove(role.getGuild(), role)) {
				this.roleToPool.remove(role, pool);
				return true;
			}
			return false;
		}

		static class Codec implements JsonSerializer<PoolRoleMap>, JsonDeserializer<PoolRoleMap> {

			@Override
			public JsonElement serialize(PoolRoleMap pooledRoleMap, Type typeOfSrc, JsonSerializationContext context) {
				JsonObject mapJson = new JsonObject();
				for (Pool pool : Pool.values()) {
					JsonArray entriesJson = new JsonArray();
					pooledRoleMap.getOrDefault(pool, new HashMap<>()).forEach((guild, role) -> {
						JsonObject entryJson = new JsonObject();
						entryJson.addProperty("guildId", guild.getIdLong());
						entryJson.addProperty("roleId", role.getIdLong());
						entriesJson.add(entryJson);
					});
					mapJson.add(pool.name(), entriesJson);
				}
				return mapJson;
			}

			@Override
			public PoolRoleMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				PoolRoleMap pooledRoleMap = new PoolRoleMap();
				JsonObject jsonObject = json.getAsJsonObject();
				for (Pool pool : Pool.values()) {
					JsonArray entriesJSON = jsonObject.getAsJsonArray(pool.name());
					entriesJSON.forEach(jsonElement -> {
						JsonObject entry = jsonElement.getAsJsonObject();
						Guild guild = RolePooler.BOT.getGuildById(entry.get("guildId").getAsLong());
						if (guild != null) {
							Role role = guild.getRoleById(entry.get("roleId").getAsLong());
							if (role != null) {
								pooledRoleMap.putRole(pool, role);
							}
						}
					});
				}
				return pooledRoleMap;
			}

		}
	}

	static class UserPoolMap extends ConcurrentHashMap<User, Set<Pool>> {
		private static final Map<String, Pool> NAME_TO_POOL_MAP = new HashMap<>();

		static {
			for (Pool pool : Pool.values()) {
				NAME_TO_POOL_MAP.put(pool.name(), pool);
			}
		}

		private boolean addPoolToUser(User user, Pool pool) {
			return this.computeIfAbsent(user, userKey -> Collections.synchronizedSet(EnumSet.noneOf(Pool.class))).add(pool);
		}

		private boolean removePoolFromUser(User user, Pool pool) {
			return this.computeIfAbsent(user, userKey -> Collections.synchronizedSet(EnumSet.noneOf(Pool.class))).remove(pool);
		}

		static class Codec implements JsonSerializer<UserPoolMap>, JsonDeserializer<UserPoolMap> {

			@Override
			public JsonElement serialize(UserPoolMap map, Type typeOfSrc, JsonSerializationContext context) {
				JsonObject jsonObject = new JsonObject();
				JsonArray userEntries = new JsonArray();
				map.forEach((user, pools) -> {
					if (!pools.isEmpty()) {
						JsonObject entryJson = new JsonObject();
						entryJson.addProperty("userId", user.getIdLong());
						JsonArray poolsJson = new JsonArray();
						pools.forEach(pool -> poolsJson.add(pool.name()));
						entryJson.add("pools", poolsJson);
						userEntries.add(entryJson);
					}
				});
				jsonObject.add("entries", userEntries);
				return jsonObject;
			}

			@Override
			public UserPoolMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				UserPoolMap userPoolMap = new UserPoolMap();
				JsonArray entriesJson = json.getAsJsonObject().getAsJsonArray("entries");
				entriesJson.forEach(jsonElement -> {
					JsonObject entryJson = jsonElement.getAsJsonObject();
					User user = RolePooler.BOT.getUserById(entryJson.get("userId").getAsLong());
					if (user != null) {
						JsonArray poolsJson = entryJson.getAsJsonArray("pools");
						poolsJson.forEach(poolElement -> userPoolMap.addPoolToUser(user, NAME_TO_POOL_MAP.get(poolElement.getAsString())));
					}
				});
				return userPoolMap;
			}

		}

	}
}
