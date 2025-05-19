package xyz.marroq.flagwarconnecteds;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.command.CommandSender;

@CommandAlias("flagwarconnecteds|fwc")
public class FlagwarConnectedsCommand extends BaseCommand {

    @Default
    @Description("Lists the version of the plugin")
    public static void onFlagwarConnecteds(CommandSender sender) {
        sender.sendMessage(FlagwarConnecteds.instance.toString());
    }

    @Subcommand("reload")
    @CommandPermission("flagwarconnecteds.reload")
    @Description("reloads the plugin config")
    public static void onReload(CommandSender sender) {
        FlagwarConnecteds.instance.reloadConfig();
        sender.sendMessage("Config reloaded!");
    }

}
