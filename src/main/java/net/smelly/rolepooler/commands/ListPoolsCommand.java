package net.smelly.rolepooler.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.smelly.disparser.Command;
import net.smelly.disparser.CommandContext;
import net.smelly.disparser.arguments.EitherArgument;
import net.smelly.disparser.arguments.java.EnumArgument;
import net.smelly.disparser.arguments.jda.RoleArgument;
import net.smelly.disparser.feedback.FeedbackHandler;
import net.smelly.disparser.feedback.exceptions.CommandSyntaxException;
import net.smelly.disparser.feedback.exceptions.DynamicCommandExceptionCreator;
import net.smelly.rolepooler.Pool;
import net.smelly.rolepooler.RolePooler;

import java.util.EnumMap;
import java.util.Set;

/**
 * @author Luke Tonon
 */
public final class ListPoolsCommand extends Command {
	private static final String EMBED_TITLE = "Pooled Roles:";
	private static final int BLURPLE = 7506394;
	private static final DynamicCommandExceptionCreator<Role> ROLE_NOT_IN_POOL_EXCEPTION = DynamicCommandExceptionCreator.createInstance(role -> {
		return String.format("The role %s is not in a pool!", role.getAsMention());
	});

	public ListPoolsCommand() {
		super("pooled_roles", EitherArgument.of(EnumArgument.get(Pool.class), RoleArgument.get()).asOptional());
	}

	@Override
	public void processCommand(CommandContext context) throws Exception {
		EnumMap<Pool, Set<Role>> map = RolePooler.DATA_MANAGER.getRoles();
		FeedbackHandler feedbackHandler = context.getFeedbackHandler();
		if (map.isEmpty()) {
			feedbackHandler.sendFeedback(this.createEmbedBuilder().appendDescription("There are no pooled roles.").build());
		} else {
			EitherArgument.Either<Pool, Role> either = context.getParsedResult(0);
			EmbedBuilder embedBuilder = this.createEmbedBuilder();
			if (either == null) {
				map.forEach((pool, roles) -> embedBuilder.addField(pool.name(), this.createFormattedRoleList(roles), true));
			} else {
				Pool rolePool = this.getPoolForEither(either);
				Set<Role> roles = map.get(rolePool);
				if (roles == null || roles.isEmpty()) {
					embedBuilder.appendDescription(String.format("There are no pooled roles for the `%s` pool", rolePool.name()));
				} else {
					embedBuilder.addField(rolePool.name(), this.createFormattedRoleList(roles), true);
				}
			}
			feedbackHandler.sendFeedback(embedBuilder.build());
		}
	}

	private EmbedBuilder createEmbedBuilder() {
		return new EmbedBuilder().setTitle(EMBED_TITLE).setColor(BLURPLE);
	}

	private String createFormattedRoleList(Set<Role> roles) {
		StringBuilder builder = new StringBuilder();
		for (Role role : roles) {
			builder.append(String.format("`%1$s(%2$s)`\n", role.getName(), role.getGuild().getName()));
		}
		return builder.toString();
	}

	private Pool getPoolForEither(EitherArgument.Either<Pool, Role> poolRoleEither) throws CommandSyntaxException {
		if (poolRoleEither.first != null) {
			return poolRoleEither.first;
		} else {
			Pool pool = RolePooler.DATA_MANAGER.getPoolForRole(poolRoleEither.second);
			if (pool == null) {
				throw ROLE_NOT_IN_POOL_EXCEPTION.create(poolRoleEither.second);
			} else {
				return pool;
			}
		}
	}
}
