package net.smelly.rolepooler.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.smelly.disparser.Command;
import net.smelly.disparser.CommandContext;
import net.smelly.rolepooler.RolePooler;
import net.smelly.rolepooler.ServerDataManager;

/**
 * Updates the user pool map for all users in a server. This command may take a decent time to fully process.
 * <p>An example of usage for this command is fixing issues after pooling roles after they've already been on users.</p>
 *
 * @author Luke Tonon
 */
public final class ResyncUserRolesCommand extends Command {
	private static final MessageEmbed MESSAGE = new EmbedBuilder().setColor(7506394).setTitle("Resyncing roles for users in this server").appendDescription("This may take some time to complete...").setFooter("It's recommended you don't run this command frequently.").build();

	public ResyncUserRolesCommand() {
		super("resync_roles");
	}

	@Override
	public void processCommand(CommandContext context) throws Exception {
		ServerDataManager manager = RolePooler.DATA_MANAGER;
		manager.updateRolesInPools(context.getEvent().getGuild());
		manager.writeUserPools();
		manager.writePooledRoles();
		context.getFeedbackHandler().sendFeedback(MESSAGE);
	}
}
