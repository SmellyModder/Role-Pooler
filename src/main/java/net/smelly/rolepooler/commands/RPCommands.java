package net.smelly.rolepooler.commands;

import net.dv8tion.jda.api.Permission;
import net.smelly.disparser.annotations.Permissions;

/**
 * @author Luke Tonon
 */
public final class RPCommands {
	@Permissions(Permission.ADMINISTRATOR)
	private static final PoolRoleCommand POOL_ROLE_COMMAND = new PoolRoleCommand();
	@Permissions(Permission.MANAGE_ROLES)
	private static final ListPoolsCommand LIST_POOLS_COMMAND = new ListPoolsCommand();
	@Permissions(Permission.ADMINISTRATOR)
	private static final ResyncUserRolesCommand RESYNC_COMMAND = new ResyncUserRolesCommand();
}
