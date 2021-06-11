package net.smelly.rolepooler;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.smelly.rolepooler.commands.RPCommands;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.smelly.disparser.CommandHandler;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.IOException;

/**
 * @author Luke Tonon
 */
public final class RolePooler {
	public static JDA BOT;
	public static ServerDataManager DATA_MANAGER;

	public static void main(String[] args) throws LoginException, IOException, InterruptedException {
		JDABuilder builder = JDABuilder.create(args[0], GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS & ~GatewayIntent.getRaw(GatewayIntent.DIRECT_MESSAGE_TYPING)));
		builder.setStatus(OnlineStatus.ONLINE);
		builder.setActivity(Activity.of(Activity.ActivityType.DEFAULT, "Pooling Roles!"));
		builder.addEventListeners(
				new CommandHandler.CommandHandlerBuilder().setPrefix("rp!").registerCommands(RPCommands.class).build(),
				new RoleListener()
		);
		BOT = builder.build();
		//Sleep current thread for 10 seconds so Guild Caches can fully load to have Server Data load properly.
		Thread.sleep(10000L);
		DATA_MANAGER = new ServerDataManager(args[1]);
	}

	static class RoleListener extends ListenerAdapter {

		@Override
		public void onGuildMemberRoleAdd(@Nonnull GuildMemberRoleAddEvent event) {
			User user = event.getUser();
			for (Role role : event.getRoles()) {
				Pool pool = DATA_MANAGER.getPoolForRole(role);
				if (pool != null) {
					DATA_MANAGER.addPooledRolesToUser(pool, user, true);
				}
			}
		}

		@Override
		public void onGuildMemberRoleRemove(@Nonnull GuildMemberRoleRemoveEvent event) {
			User user = event.getUser();
			for (Role role : event.getRoles()) {
				Pool pool = DATA_MANAGER.getPoolForRole(role);
				if (pool != null) {
					DATA_MANAGER.removedPooledRolesFromUser(pool, user, true);
				}
			}
		}

		@Override
		public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
			DATA_MANAGER.checkAndUpdateRolesForUser(event.getGuild(), event.getUser());
		}

		@Override
		public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
			Member member = event.getMember();
			User user = event.getUser();
			if (member != null && DATA_MANAGER.isUserInPool(user, Pool.BOOSTER)) {
				Role boostRole = event.getGuild().getBoostRole();
				if (boostRole != null && DATA_MANAGER.getPoolForRole(boostRole) == Pool.BOOSTER) {
					DATA_MANAGER.removedPooledRolesFromUser(Pool.BOOSTER, user, true);
				}
			}
		}

	}
}
