package net.smelly.rolepooler.commands;

import net.dv8tion.jda.api.entities.Role;
import net.smelly.disparser.Command;
import net.smelly.disparser.CommandContext;
import net.smelly.disparser.arguments.java.EnumArgument;
import net.smelly.disparser.arguments.jda.RoleArgument;
import net.smelly.disparser.feedback.FeedbackHandler;
import net.smelly.disparser.feedback.exceptions.BiDynamicCommandExceptionCreator;
import net.smelly.rolepooler.Pool;
import net.smelly.rolepooler.RolePooler;

/**
 * @author Luke Tonon
 */
public final class PoolRoleCommand extends Command {
	private static final BiDynamicCommandExceptionCreator<Role, Pool> ALREADY_IN_POOL_EXCEPTION = BiDynamicCommandExceptionCreator.createInstance(((role, pool) -> {
		return String.format("%1$s role is already in the `%2$s` pool!", role.getAsMention(), pool.name());
	}));
	private static final BiDynamicCommandExceptionCreator<Role, Pool> ALREADY_IN_OTHER_POOL_EXCEPTION = BiDynamicCommandExceptionCreator.createInstance(((role, pool) -> {
		return String.format("%1$s role is already in another pool (`%2$s`)!", role.getAsMention(), pool.name());
	}));
	private static final BiDynamicCommandExceptionCreator<Role, Pool> NOT_IN_POOL_EXCEPTION = BiDynamicCommandExceptionCreator.createInstance(((role, pool) -> {
		return String.format("%1$s role is not in the `%2$s` pool!", role.getAsMention(), pool.name());
	}));

	public PoolRoleCommand() {
		super("pool", EnumArgument.get(Action.class), EnumArgument.get(Pool.class), RoleArgument.get());
	}

	@Override
	public void processCommand(CommandContext context) throws Exception {
		Action action = context.getParsedResult(0);
		Pool pool = context.getParsedResult(1);
		Role role = context.getParsedResult(2);
		FeedbackHandler handler = context.getFeedbackHandler();
		if (action == Action.ADD) {
			Pool rolePool = RolePooler.DATA_MANAGER.getPoolForRole(role);
			if (rolePool != null && rolePool != pool) {
				throw ALREADY_IN_OTHER_POOL_EXCEPTION.create(role, rolePool);
			} else if (RolePooler.DATA_MANAGER.putRole(pool, role)) {
				RolePooler.DATA_MANAGER.writePooledRoles();
				handler.sendSuccess(String.format("Successfully added %1$s role to the `%2$s` pool!", role.getAsMention(), pool.name()));
			} else {
				throw ALREADY_IN_POOL_EXCEPTION.create(role, pool);
			}
		} else {
			if (RolePooler.DATA_MANAGER.removeRole(pool, role)) {
				RolePooler.DATA_MANAGER.writePooledRoles();
				handler.sendSuccess(String.format("Successfully removed %1$s role from `%2$s` pool!", role.getAsMention(), pool.name()));
			} else {
				throw NOT_IN_POOL_EXCEPTION.create(role, pool);
			}
		}
	}

	enum Action {
		ADD,
		REMOVE
	}
}
